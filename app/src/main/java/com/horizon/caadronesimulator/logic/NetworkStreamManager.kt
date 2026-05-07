package com.horizon.caadronesimulator.logic

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * [v1.3.9] 網路數據流管理器
 * 支援 UDP 監聽與 TCP 客戶端模式，對接 MAVLink / SITL
 */
class NetworkStreamManager(
    private val onDataReceived: (ByteArray) -> Unit,
    private val onStatusUpdate: (Boolean, String) -> Unit
) {
    private var udpSocket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var networkThread: Thread? = null

    fun startUdpListener(host: String, port: Int) {
        if (isRunning.get()) stopAll()

        isRunning.set(true)
        networkThread = thread(name = "UDPListener") {
            try {
                val address = InetAddress.getByName(host)
                udpSocket = DatagramSocket(port, address)
                udpSocket?.soTimeout = 1000 // 1秒超時以利檢查 isRunning
                
                onStatusUpdate(true, "UDP 監聽中 $host:$port")
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isRunning.get()) {
                    try {
                        udpSocket?.receive(packet)
                        if (packet.length > 0) {
                            val data = packet.data.copyOfRange(0, packet.length)
                            onDataReceived(data)
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // 正常的超時，繼續循環
                    } catch (e: Exception) {
                        if (isRunning.get()) Log.e("NetworkStream", "UDP Receive Error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                onStatusUpdate(false, "UDP 啟動失敗: ${e.message}")
            } finally {
                udpSocket?.close()
                udpSocket = null
            }
        }
    }

    fun stopAll() {
        isRunning.set(false)
        udpSocket?.close()
        networkThread?.interrupt()
        try { networkThread?.join(500) } catch (e: Exception) {}
        networkThread = null
        onStatusUpdate(false, "網路通訊已停止")
    }
}
