package com.horizon.caadronesimulator.logic

import android.content.Context
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

    private val assemblyBuffer = ByteArray(4096)
    private var assemblyPos = 0

    fun toggleConnection() {
        if (isRunning.get()) stopAll() else startReadingByPath("USB")
    }

    fun startReadingByPath(path: String) {
        when(path) {
            "USB" -> {
                val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                if (drivers.isNotEmpty()) {
                    val driver = drivers[0]
                    if (usbManager.hasPermission(driver.device)) startUsbReading(driver.device)
                }
            }
            "NETWORK" -> startUdpReading(droneState.networkHost, droneState.networkPort)
        }
    }

    private fun startUsbReading(device: UsbDevice) {
        val connection = usbManager.openDevice(device) ?: return
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) return
        serialPort = drivers[0].ports[0]
        try {
            serialPort?.open(connection)
            serialPort?.setParameters(droneState.baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            ioManager = SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) { handleRawIncoming(data) }
                override fun onRunError(e: Exception) { stopAll() }
            })
            ioManager?.start()
            isRunning.set(true)
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
            if (assemblyPos + data.size > assemblyBuffer.size) assemblyPos = 0
            System.arraycopy(data, 0, assemblyBuffer, assemblyPos, data.size)
            assemblyPos += data.size
            
            var i = 0
            while (i < assemblyPos) {
                val u = assemblyBuffer[i].toInt() and 0xFF
                var consumed = 0
                if (u == 0xA6) { consumed = 87 } // [v1.7.6] Store 版物理跳過 UMBUS
                else if (u == 0xC8 || u == 0x81) {
                    if (i + 1 < assemblyPos) {
                        val pLen = (assemblyBuffer[i+1].toInt() and 0xFF) + 2
                        if (i + pLen <= assemblyPos) { consumed = pLen }
                    }
                }
                if (consumed > 0) i += consumed else i++
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
        ioManager?.stop(); ioManager = null
        serialPort?.close(); serialPort = null
        udpSocket?.close(); udpSocket = null
        onConnectionStatusUpdate(ConnectionStatus.IDLE)
    }

    fun listAvailableSerialPorts(): List<String> = listOf("USB", "NETWORK")
    fun setLockedPath(path: String) {}
    fun setLockedProtocol(protocol: String) {}
}
