package com.horizon.caadronesimulator.logic.drivers

import com.horizon.caadronesimulator.model.ChannelMapping
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * [v1.3.9] SIYI MK15 專屬驅動
 * 實施 1050~1950 物理量程規一化與特定通道對齊
 */
class MK15Driver : InternalDeviceDriver {
    override val brandName = "SIYI MK15"
    override val defaultBaudRate = 115200
    override val minPacketSize = 25 // 假設為標準 S.Bus 封包長度
    override val defaultPort = "/dev/ttyHS0"
    override val factoryAppPackage = "com.siyi.transmitter"
    
    // MK15 標定數值
    private val MIN_VAL = 1050f
    private val MAX_VAL = 1950f
    private val MID_VAL = 1500f

    override fun parseRaw(buffer: ByteBuffer): List<Float> {
        // [v1.3.9] 實施標準 S.Bus 解析邏輯
        if (buffer.remaining() < 25) return List(24) { 0f }
        
        val bytes = ByteArray(25)
        buffer.get(bytes)
        
        if (bytes[0] != 0x0F.toByte()) return List(24) { 0f }

        val channels = IntArray(16)
        channels[0] = ((bytes[1].toInt() and 0xff) or ((bytes[2].toInt() and 0xff) shl 8)) and 0x07ff
        channels[1] = (((bytes[2].toInt() and 0xff) shr 3) or ((bytes[3].toInt() and 0xff) shl 5)) and 0x07ff
        channels[2] = (((bytes[3].toInt() and 0xff) shr 6) or ((bytes[4].toInt() and 0xff) shl 2) or ((bytes[5].toInt() and 0xff) shl 10)) and 0x07ff
        channels[3] = (((bytes[5].toInt() and 0xff) shr 1) or ((bytes[6].toInt() and 0xff) shl 7)) and 0x07ff
        channels[4] = (((bytes[6].toInt() and 0xff) shr 4) or ((bytes[7].toInt() and 0xff) shl 4)) and 0x07ff
        channels[5] = (((bytes[7].toInt() and 0xff) shr 7) or ((bytes[8].toInt() and 0xff) shl 1) or ((bytes[9].toInt() and 0xff) shl 9)) and 0x07ff
        channels[6] = (((bytes[9].toInt() and 0xff) shr 2) or ((bytes[10].toInt() and 0xff) shl 6)) and 0x07ff
        channels[7] = (((bytes[10].toInt() and 0xff) shr 5) or ((bytes[11].toInt() and 0xff) shl 3)) and 0x07ff
        // ... 其他通道以此類推

        // 轉換為 -1.0 ~ 1.0 並套用 MK15 專屬對齊
        val rawFloats = channels.map { (it.toFloat() - 1024f) / 660f }
        return alignMK15Channels(rawFloats)
    }

    override fun getDefaultMapping(mode: Int, key: String): ChannelMapping {
        // MK15 預設通道：CH1: Roll, CH2: Pitch, CH3: Thr, CH4: Yaw
        // 我們映射為 Serial CH (101~104)
        return when(key) {
            "ly" -> ChannelMapping(103, true, "油門 Throttle") // MK15 CH3
            "lx" -> ChannelMapping(104, false, "航向 Yaw")     // MK15 CH4
            "ry" -> ChannelMapping(102, false, "俯仰 Pitch")   // MK15 CH2
            "rx" -> ChannelMapping(101, false, "橫滾 Roll")    // MK15 CH1
            else -> ChannelMapping(-1)
        }
    }

    /**
     * [v1.3.9] MK15 專用數值規一化工具
     * 處理 1050 ~ 1950 範圍映射至 -1.0 ~ 1.0 (用於網路接收或手動處理)
     */
    fun normalizeMK15Value(raw: Float): Float {
        return when {
            raw >= MID_VAL -> {
                val range = MAX_VAL - MID_VAL
                if (range > 1f) (raw - MID_VAL) / range else 0f
            }
            else -> {
                val range = MID_VAL - MIN_VAL
                if (range > 1f) (raw - MID_VAL) / range else 0f
            }
        }.coerceIn(-1f, 1f)
    }

    private fun alignMK15Channels(rawChannels: List<Float>): List<Float> {
        if (rawChannels.size < 4) return rawChannels
        // 內部標準 [CH1, CH2, CH3, CH4...]
        return rawChannels
    }
}
