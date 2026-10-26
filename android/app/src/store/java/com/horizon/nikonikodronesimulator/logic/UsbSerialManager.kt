package com.horizon.nikonikodronesimulator.logic

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.ConnectionStatus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [v1.7.14] 通用 USB 通訊管理器 (Store 版 - 隱私淨化版)
 * 職責：僅處理合規的 USB-Serial OTG 數據。
 * 變更：已移除所有網路 (UDP) 相關邏輯與權限需求，確保上架版本具備最高隱私安全性。
 */
class UsbSerialManager(
    private val context: Context,
    private val droneState: DroneState,
    private val onRawChannelsReceived: (List<Float>) -> Unit,
    private val onConnectionStatusUpdate: (ConnectionStatus) -> Unit
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val isRunning = AtomicBoolean(false)
    
    private val ACTION_USB_PERMISSION = "com.horizon.nikonikodronesimulator.USB_PERMISSION"
    private var pendingDevice: UsbDevice? = null
    
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { startUsbReading(it) }
                    }
                    pendingDevice = null
                }
            }
        }
    }

    private val assemblyBuffer = ByteArray(4096)
    private var assemblyPos = 0

    fun toggleConnection() {
        if (isRunning.get()) stopAll() else startReadingByPath("USB")
    }

    private fun resetBuffers() {
        synchronized(assemblyBuffer) {
            assemblyPos = 0
            assemblyBuffer.fill(0)
        }
    }

    fun startReadingByPath(path: String) {
        if (isRunning.get()) stopAll() 
        resetBuffers()
        
        if (path == "USB") {
            if (droneState.isHidPriorityEnabled) {
                onConnectionStatusUpdate(ConnectionStatus.IDLE)
                return
            }

            val deviceList = usbManager.deviceList
            if (deviceList.isEmpty()) return
            
            for (device in deviceList.values) {
                if (usbManager.hasPermission(device)) {
                    val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                    val driver = drivers.find { it.device.deviceId == device.deviceId }
                    if (driver != null) {
                        startUsbReading(device)
                        break 
                    }
                } else {
                    requestUsbPermission(device)
                    break 
                }
            }
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        pendingDevice = device
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else 0
        
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(usbPermissionReceiver, filter)
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun startUsbReading(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(device)
            return
        }

        val connection = usbManager.openDevice(device) ?: return
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = drivers.find { it.device.deviceId == device.deviceId } ?: return
        
        serialPort = driver.ports[0]
        try {
            serialPort?.open(connection)
            serialPort?.setParameters(droneState.baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            isRunning.set(true)
            
            ioManager = SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) { handleRawIncoming(data) }
                override fun onRunError(e: Exception) { stopAll() }
            })
            ioManager?.start()
            onConnectionStatusUpdate(ConnectionStatus.LINKED)
        } catch (e: Exception) { stopAll() }
    }

    private fun handleRawIncoming(data: ByteArray) {
        synchronized(assemblyBuffer) {
            if (data.size > assemblyBuffer.size) return
            if (assemblyPos + data.size > assemblyBuffer.size) assemblyPos = 0
            System.arraycopy(data, 0, assemblyBuffer, assemblyPos, data.size)
            assemblyPos += data.size
            
            var i = 0
            while (i < assemblyPos) {
                val u = assemblyBuffer[i].toInt() and 0xFF
                var consumed = 0
                if (u == 0xA6) { consumed = 87 } 
                else if (u == 0xC8 || u == 0x81) {
                    if (i + 1 < assemblyPos) {
                        val pLen = (assemblyBuffer[i+1].toInt() and 0xFF) + 2
                        if (pLen > 128) { i++; continue }
                        if (i + pLen <= assemblyPos) { consumed = pLen }
                        else { break }
                    } else { break }
                }
                
                if (consumed > 0) { i += consumed } else { i++ }
            }
            if (i > 0) {
                val rem = assemblyPos - i
                if (rem > 0) System.arraycopy(assemblyBuffer, i, assemblyBuffer, 0, rem)
                assemblyPos = rem
            }
        }
    }

    fun stopAll() {
        isRunning.set(false)
        try { context.unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
        ioManager?.stop(); ioManager = null
        serialPort?.close(); serialPort = null
        onConnectionStatusUpdate(ConnectionStatus.IDLE)
    }

    fun listAvailableSerialPorts(): List<String> = listOf("USB")
    fun setLockedPath(path: String) {}
    fun setLockedProtocol(protocol: String) {}
}
