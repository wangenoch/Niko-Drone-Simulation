package com.horizon.caadronesimulator.logic

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
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.ConnectionStatus
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [v1.7.6] 通用 USB 通訊管理器 (Store 版)
 * 職責：僅處理合規的 USB-Serial OTG 與網路 UDP 數據，不含專業驅動。
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
    private var udpSocket: DatagramSocket? = null
    private var networkThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    
    // [v1.7.8] 權限請求機制
    private val ACTION_USB_PERMISSION = "com.horizon.caadronesimulator.USB_PERMISSION"
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

    /** [v1.7.8] 加固：原子化狀態清理，防止殘留 Buffer 導致新連線解析崩潰 */
    private fun resetBuffers() {
        synchronized(assemblyBuffer) {
            assemblyPos = 0
            assemblyBuffer.fill(0)
        }
    }

    fun startReadingByPath(path: String) {
        if (isRunning.get()) stopAll() 
        resetBuffers()
        
        when(path) {
            "USB" -> {
                // [v1.7.8] 加固：只有在設定中明確關閉「HID 優先」時才執行 Serial 掃描與權限請求
                if (droneState.isHidPriorityEnabled) {
                    onConnectionStatusUpdate(ConnectionStatus.IDLE)
                    return
                }

                // [v1.7.8] 專業級主動捕獲模式：不再預設「認識才求權限」
                // 遍歷所有實體連結，只要發現任何裝置但無權限，立即彈出系統對話框
                val deviceList = usbManager.deviceList
                if (deviceList.isEmpty()) return
                
                for (device in deviceList.values) {
                    if (usbManager.hasPermission(device)) {
                        // 已經有權限，嘗試尋找合適驅動並啟動
                        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                        val driver = drivers.find { it.device.deviceId == device.deviceId }
                        if (driver != null) {
                            startUsbReading(device)
                            break // 成功開啟一個後退出，防止多裝置衝突
                        }
                    } else {
                        // [關鍵修正] 只要看見硬體，不論識別碼，優先求權限（達成截圖中的效果）
                        requestUsbPermission(device)
                        break // 彈窗是異步的，請求後退出等待廣播
                    }
                }
            }
            "NETWORK" -> startUdpReading(droneState.networkHost, droneState.networkPort)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        pendingDevice = device
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else 0
        
        // [v1.7.8] 加固：明確指定包名以破解 Android 9+ 的隱式廣播攔截，確保彈出權限對話框
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(usbPermissionReceiver, filter)
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun startUsbReading(device: UsbDevice) {
        // [v1.7.8] 安全哨兵：在執行 openDevice 前強制核對權限，徹底杜絕 SecurityException 閃退
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
            
            // [v1.7.8] 加固：建立 I/O 管理器前確保標記已重置
            isRunning.set(true)
            
            ioManager = SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) { handleRawIncoming(data) }
                override fun onRunError(e: Exception) { 
                    // [v1.7.8] 靜默處理插拔錯誤
                    stopAll() 
                }
            })
            ioManager?.start()
            onConnectionStatusUpdate(ConnectionStatus.LINKED)
        } catch (e: Exception) { stopAll() }
    }

    private fun startUdpReading(host: String, port: Int) {
        isRunning.set(true)
        networkThread = Thread {
            try {
                val address = InetAddress.getByName(host)
                udpSocket = DatagramSocket(port)
                udpSocket?.soTimeout = 1000
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                while (isRunning.get()) {
                    try {
                        udpSocket?.receive(packet)
                        if (packet.length > 0) handleRawIncoming(packet.data.copyOfRange(0, packet.length))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }.apply { name = "StoreNetworkEngine"; start() }
    }

    private fun handleRawIncoming(data: ByteArray) {
        synchronized(assemblyBuffer) {
            // [v1.7.8] 加固：防範惡意大數據包導致的 Buffer 溢出
            if (data.size > assemblyBuffer.size) return
            
            if (assemblyPos + data.size > assemblyBuffer.size) assemblyPos = 0
            System.arraycopy(data, 0, assemblyBuffer, assemblyPos, data.size)
            assemblyPos += data.size
            
            var i = 0
            while (i < assemblyPos) {
                val u = assemblyBuffer[i].toInt() and 0xFF
                var consumed = 0
                if (u == 0xA6) { 
                    // AX12/UMBUS 標頭
                    consumed = 87 
                } else if (u == 0xC8 || u == 0x81) {
                    // CRSF 標頭
                    if (i + 1 < assemblyPos) {
                        val pLen = (assemblyBuffer[i+1].toInt() and 0xFF) + 2
                        // [v1.7.8] 加固：驗證協議長度合理性 (CRSF 最大約 64 bytes)
                        if (pLen > 128) {
                            i++ // 長度異常，視為無效標頭，跳過 1 byte 繼續尋找
                            continue
                        }
                        if (i + pLen <= assemblyPos) { consumed = pLen }
                        else { break } // 數據包未收全，保留至下一幀
                    } else { break }
                }
                
                if (consumed > 0) {
                    // [v1.7.8] 解析邏輯分發 (僅在完整包時觸發回調)
                    if (u == 0xA6 || u == 0xC8 || u == 0x81) {
                        // 此處未來可擴充對 Store 版數據的回調觸發
                    }
                    i += consumed 
                } else { 
                    i++ 
                }
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
        udpSocket?.close(); udpSocket = null
        onConnectionStatusUpdate(ConnectionStatus.IDLE)
    }

    fun listAvailableSerialPorts(): List<String> = listOf("USB", "NETWORK")
    fun setLockedPath(path: String) {}
    fun setLockedProtocol(protocol: String) {}
}
