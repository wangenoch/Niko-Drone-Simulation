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

    /** [v1.7.8] 加固：原子化狀態清理，防止殘留 Buffer 導致新連線解析崩潰 */
    private fun resetBuffers() {
        synchronized(assemblyBuffer) {
            assemblyPos = 0
            assemblyBuffer.fill(0)
        }
    }

    fun startReadingByPath(path: String) {
        if (isRunning.get()) stopAll() // 強制單例保護
        resetBuffers()
        
        when(path) {
            "USB" -> {
                val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                if (drivers.isNotEmpty()) {
                    // [v1.7.8] 硬體白名單優先級：RadioMaster(0x0483/0x5740), CP210x(0x10c4), FTDI(0x0403)
                    val preferredDriver = drivers.find { d ->
                        val vid = d.device.vendorId
                        vid == 0x0483 || vid == 0x10C4 || vid == 0x0403
                    } ?: drivers[0]
                    
                    if (usbManager.hasPermission(preferredDriver.device)) startUsbReading(preferredDriver.device)
                }
            }
            "NETWORK" -> startUdpReading(droneState.networkHost, droneState.networkPort)
        }
    }

    private fun startUsbReading(device: UsbDevice) {
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
        ioManager?.stop(); ioManager = null
        serialPort?.close(); serialPort = null
        udpSocket?.close(); udpSocket = null
        onConnectionStatusUpdate(ConnectionStatus.IDLE)
    }

    fun listAvailableSerialPorts(): List<String> = listOf("USB", "NETWORK")
    fun setLockedPath(path: String) {}
    fun setLockedProtocol(protocol: String) {}
}
