package com.horizon.caadronesimulator.logic.drivers

import com.horizon.caadronesimulator.model.ChannelMapping
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [v1.6.3] RadioMaster AX12 (UMBUS-V2) 驅動程式
 * 依據 UMBUS Protocol Specification 2024 校準：
 * 1. 軸向對位：G0:Yaw, G1:Pitch, G2:Throttle, G3:Roll。
 * 2. 支援隱藏通道：Bytes 14-17 (105, 106 軸)。
 * 3. 推薦波特率：921600。
 */
class AX12V2Driver : InternalDeviceDriver {
    override val brandName = "RadioMaster AX12 (UMBUS-V2)"
    override var defaultBaudRate = 921600
    override val minPacketSize = 87
    override val defaultPort = "/dev/ttyS0"
    override val factoryAppPackage = "com.Flyshark.RadioMasterAX"

    // Industrial Logic 2.0: 驅動主導權
    override val isMappingProtected = true
    override val isAutoPromptEnabled = true
    override val recommendedBaudRate = 921600
    override val protectionWarning = "RadioMaster AX12 (UMBUS-V2) 專業協議已鎖定。"

    companion object {
        const val SYNC_BYTE = 0xA6.toByte()
        const val FRAME_TYPE_CHANNEL_DATA = 0x57.toByte()
        const val CHANNEL_DATA_LEN = 87

        // [方案 P] CRC-8/MAXIM 查找表
        private val CRC8_TABLE = intArrayOf(
            0x00, 0x5E, 0xBC, 0xE2, 0x61, 0x3F, 0xDD, 0x83, 0xC2, 0x9C, 0x7E, 0x20, 0xA3, 0xFD, 0x1F, 0x41,
            0x9D, 0xC3, 0x21, 0x7F, 0xFC, 0xA2, 0x40, 0x1E, 0x5F, 0x01, 0xE3, 0xBD, 0x3E, 0x60, 0x82, 0xDC,
            0x23, 0x7D, 0x9F, 0xC1, 0x42, 0x1C, 0xFE, 0xA0, 0xE1, 0xBF, 0x5D, 0x03, 0x80, 0xDE, 0x3C, 0x62,
            0xBE, 0xE0, 0x02, 0x5C, 0xDF, 0x81, 0x63, 0x3D, 0x7C, 0x22, 0xC0, 0x9E, 0x1D, 0x43, 0xA1, 0xFF,
            0x46, 0x18, 0xFA, 0xA4, 0x27, 0x79, 0x9B, 0xC5, 0x84, 0xDA, 0x38, 0x66, 0xE5, 0xBB, 0x59, 0x07,
            0xDB, 0x85, 0x67, 0x39, 0xBA, 0xE4, 0x06, 0x58, 0x19, 0x47, 0xA5, 0xFB, 0x78, 0x26, 0xC4, 0x9A,
            0x65, 0x3B, 0xD9, 0x87, 0x04, 0x5A, 0xB8, 0xE6, 0xA7, 0xF9, 0x1B, 0x45, 0xC6, 0x98, 0x7A, 0x24,
            0xF8, 0xA6, 0x44, 0x1A, 0x99, 0xC7, 0x25, 0x7B, 0x3A, 0x64, 0x86, 0xD8, 0x5B, 0x05, 0xE7, 0xB9,
            0x8C, 0xD2, 0x30, 0x6E, 0xED, 0xB3, 0x51, 0x0F, 0x4E, 0x10, 0xF2, 0xAC, 0x2F, 0x71, 0x93, 0xCD,
            0x11, 0x4F, 0xAD, 0xF3, 0x70, 0x2E, 0xCC, 0x92, 0xD3, 0x8D, 0x6F, 0x31, 0xB2, 0xEC, 0x0E, 0x50,
            0xAF, 0xF1, 0x13, 0x4D, 0xCE, 0x90, 0x72, 0x2C, 0x6D, 0x33, 0xD1, 0x8F, 0x0C, 0x52, 0xB0, 0xEE,
            0x32, 0x6C, 0x8E, 0xD0, 0x53, 0x0D, 0xEF, 0xB1, 0xF0, 0xAE, 0x4C, 0x12, 0x91, 0xCF, 0x2D, 0x73,
            0xCA, 0x94, 0x76, 0x28, 0xAB, 0xF5, 0x17, 0x49, 0x08, 0x56, 0xB4, 0xEA, 0x69, 0x37, 0xD5, 0x8B,
            0x57, 0x09, 0xEB, 0xB5, 0x36, 0x68, 0x8A, 0xD4, 0x95, 0xCB, 0x29, 0x77, 0xF4, 0xAA, 0x48, 0x16,
            0xE9, 0xB7, 0x55, 0x0B, 0x88, 0xD6, 0x34, 0x6A, 0x2B, 0x75, 0x97, 0xC9, 0x4A, 0x14, 0xF6, 0xA8,
            0x74, 0x2A, 0xC8, 0x96, 0x15, 0x4B, 0xA9, 0xF7, 0xB6, 0xE8, 0x0A, 0x54, 0xD7, 0x89, 0x6B, 0x35
        )

        fun verifyChecksum(data: ByteArray): Boolean {
            if (data.size < CHANNEL_DATA_LEN) return false
            val expected = data.last().toInt() and 0xFF
            var crc = 0x00
            for (i in 1 until data.size - 1) {
                crc = CRC8_TABLE[(data[i].toInt() and 0xFF) xor crc]
            }
            return crc == expected
        }
        
        /**
         * AX-Enhanced 軟啟動序列
         */
        fun getInitSequence(): ByteArray = byteArrayOf(0x55.toByte(), 0xAA.toByte())
    }

    override fun parseRaw(buffer: ByteBuffer): List<Float> {
        // [v1.6.3] 依據 UMBUS SPEC 進行物理軸向映射 (Standard Normalized Range)
        val rawYaw      = buffer.getShort(6).toFloat()  // G0
        val rawPitch    = buffer.getShort(8).toFloat()  // G1
        val rawThrottle = buffer.getShort(10).toFloat() // G2
        val rawRoll     = buffer.getShort(12).toFloat() // G3

        val pool = mutableListOf<Float>()
        
        // 101:Throttle, 102:Yaw, 103:Pitch, 104:Roll
        pool.add((rawThrottle / 500.0f).coerceIn(-1f, 1f)) // 101
        pool.add((rawYaw / 500.0f).coerceIn(-1f, 1f))      // 102
        pool.add((rawPitch / 500.0f).coerceIn(-1f, 1f))    // 103
        pool.add((rawRoll / 500.0f).coerceIn(-1f, 1f))     // 104

        // 105, 106: 隱藏通道 (Bytes 14-17)
        val hidden1 = (buffer.getShort(14).toFloat() / 500.0f).coerceIn(-1f, 1f)
        val hidden2 = (buffer.getShort(16).toFloat() / 500.0f).coerceIn(-1f, 1f)
        pool.add(hidden1) // 105
        pool.add(hidden2) // 106

        // 107 ~ 126: 輔助開關區 (Offset 18+)
        for (i in 0 until 20) {
            val offset = 18 + (i * 2)
            if (offset + 2 <= buffer.capacity()) {
                // SPEC 註明此區為 u16le (0-65535)，中心點為 32768
                val uVal = buffer.getShort(offset).toInt() and 0xFFFF
                val normalized = (uVal - 32768) / 32768.0f
                pool.add(normalized.coerceIn(-1f, 1f))
            } else pool.add(0f)
        }
        return pool
    }

    override fun getDefaultMapping(mode: Int, key: String): ChannelMapping {
        // [v1.6.3] 標準化映射：101:Throttle, 102:Yaw, 103:Pitch, 104:Roll
        return when(key) {
            "ly" -> ChannelMapping(if(mode==2 || mode==3) 101 else 103, true, if(mode==2 || mode==3) "油門" else "俯仰")
            "lx" -> ChannelMapping(if(mode==3 || mode==4) 104 else 102, false, if(mode==3 || mode==4) "橫滾" else "航向")
            "ry" -> ChannelMapping(if(mode==1 || mode==4) 101 else 103, false, if(mode==1 || mode==4) "油門" else "俯仰")
            "rx" -> ChannelMapping(if(mode==1 || mode==2) 104 else 102, false, if(mode==1 || mode==2) "橫滾" else "航向")
            else -> ChannelMapping(-1)
        }
    }
}
