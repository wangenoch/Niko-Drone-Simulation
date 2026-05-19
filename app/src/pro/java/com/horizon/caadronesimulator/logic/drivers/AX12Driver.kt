package com.horizon.caadronesimulator.logic.drivers

import com.horizon.caadronesimulator.model.ChannelMapping
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [v1.5.2] RadioMaster AX12 整合型驅動 (Protocol Driver)
 * 職責：整合原本散落在 Handler 與 Standardizer 中的解碼、校驗與通道對齊邏輯。
 */
class AX12Driver : InternalDeviceDriver {
    override val brandName = "RadioMaster AX12"
    override var defaultBaudRate = 921600 // [v1.5.2] 改為 var 允許動態覆蓋
    override val minPacketSize = 87
    override val defaultPort = "/dev/ttyS0"
    override val factoryAppPackage = "com.Flyshark.RadioMasterAX"

    // [v1.5.2] Industrial Logic 2.0: 驅動主導權
    override val isMappingProtected = true
    override val isAutoPromptEnabled = true
    override val recommendedBaudRate = 921600
    override val protectionWarning = "RadioMaster AX12 專業協議已鎖定，確保控制精確性。"

    companion object {
        const val SYNC_BYTE = 0xA6.toByte()
        const val FRAME_TYPE_CHANNEL_DATA = 0x57.toByte()
        const val FRAME_TYPE_IDLE = 0x77.toByte()
        const val FRAME_TYPE_HEARTBEAT = 0x08.toByte()
        const val FRAME_TYPE_EXTENDED = 0x10.toByte()
        const val FRAME_TYPE_ELRS_TELEM = 0x15.toByte()
        
        const val CHANNEL_DATA_LEN = 87
        const val HEARTBEAT_LEN = 7

        // CRC-8/MAXIM 查找表 (Dallas 1-Wire, Poly: 0x31)
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

        /**
         * 校驗 UMBUS 封包 CRC
         */
        fun verifyChecksum(data: ByteArray): Boolean {
            if (data.size < 3) return false
            if (data[1] == FRAME_TYPE_HEARTBEAT && data.size == HEARTBEAT_LEN) return true
            
            val expected = data.last().toInt() and 0xFF
            val frameType = data[1]
            
            val init = when (frameType) {
                FRAME_TYPE_EXTENDED -> 0x7F 
                FRAME_TYPE_ELRS_TELEM -> 0x32 
                else -> 0x00
            }
            
            var crc = init
            for (i in 1 until data.size - 1) {
                val byte = data[i].toInt() and 0xFF
                crc = CRC8_TABLE[byte xor crc]
            }
            return crc == expected
        }
    }

    override fun parseRaw(buffer: ByteBuffer): List<Float>? {
        // [v1.5.2 深度整合]
        // 物理真相：Offset 6=Pitch, 8=Throttle, 10=Yaw, 12=Roll (對應 UMBUS SPEC)
        val pitch = (buffer.getShort(6).toFloat() / 500.0f).coerceIn(-1f, 1f)
        val throttle = (buffer.getShort(8).toFloat() / 500.0f).coerceIn(-1f, 1f)
        val yaw = (buffer.getShort(10).toFloat() / 500.0f).coerceIn(-1f, 1f)
        val roll = (buffer.getShort(12).toFloat() / 500.0f).coerceIn(-1f, 1f)

        val pool = mutableListOf<Float>()
        
        // [v1.5.2] 物理量程優化：加入 2% 中心零位補償 (Center Deadzone)
        // 解決 UMBUS 硬體電訊 ±5 抖動問題
        fun applyZeroCompensation(v: Float): Float = if (kotlin.math.abs(v) < 0.02f) 0f else v

        // 這裡直接執行 ProtocolStandardizer 原本的任務：輸出為 T-Y-P-R 序列
        // 101:Throttle, 102:Yaw, 103:Pitch, 104:Roll
        pool.add(applyZeroCompensation(throttle)) // 101
        pool.add(applyZeroCompensation(yaw))      // 102
        pool.add(applyZeroCompensation(pitch))    // 103
        pool.add(applyZeroCompensation(roll))     // 104

        // 輔助開關區 (CH5 ~ CH24)
        for (i in 0 until 20) {
            val offset = 18 + (i * 2)
            if (offset + 2 <= buffer.capacity()) {
                val rawVal = buffer.getShort(offset).toFloat()
                val normalized = (rawVal / 500.0f).coerceIn(-1f, 1f)
                pool.add(quantizeAux(normalized))
            } else {
                pool.add(0f)
            }
        }

        return pool
    }

    private fun quantizeAux(value: Float): Float {
        return when {
            value <= -0.5f -> -1.0f
            value >= 0.5f -> 1.0f
            else -> 0.0f
        }
    }

    override fun getDefaultMapping(mode: Int, key: String): ChannelMapping {
        // [v1.5.2] 由於 parseRaw 已完成標準化，此處 Mapping 回歸直覺順序
        return when(key) {
            "ly" -> ChannelMapping(if(mode==2 || mode==3) 101 else 103, true, if(mode==2 || mode==3) "油門 Throttle" else "俯仰 Pitch")
            "lx" -> ChannelMapping(if(mode==3 || mode==4) 104 else 102, false, if(mode==3 || mode==4) "橫滾 Roll" else "航向 Yaw")
            "ry" -> ChannelMapping(if(mode==1 || mode==4) 101 else 103, false, if(mode==1 || mode==4) "油門 Throttle" else "俯仰 Pitch")
            "rx" -> ChannelMapping(if(mode==1 || mode==2) 104 else 102, false, if(mode==1 || mode==2) "橫滾 Roll" else "航向 Yaw")
            else -> ChannelMapping(-1)
        }
    }
}
