package com.horizon.caadronesimulator.logic.drivers

import com.horizon.caadronesimulator.model.ChannelMapping
import java.nio.ByteBuffer

/**
 * [v1.2.81] RadioMaster AX12 專屬驅動
 * 嚴格執行 UMBUS 物理偏移規範。
 */
class AX12Driver : InternalDeviceDriver {
    override val brandName = "RadioMaster AX12"
    override val defaultBaudRate = 921600
    override val minPacketSize = 87
    override val defaultPort = "/dev/ttyS0"
    override val factoryAppPackage = "com.Flyshark.RadioMasterAX"

    override fun parseRaw(buffer: ByteBuffer): List<Float> {
        // [v1.2.81 驅動整合版]
        // 實測真相：Offset 6=左水平, 8=右垂直, 10=右水平, 12=左垂直
        
        // [v1.2.82] 終極物理定鎖：Offset 6, 8, 10, 12 嚴格對應硬體 S1, S2, S3, S4
        val s1 = (buffer.getShort(6).toFloat() / 500.0f).coerceIn(-1f, 1f)
        val s2 = (buffer.getShort(8).toFloat() / 500.0f).coerceIn(-1f, 1f)
        val s3 = (buffer.getShort(10).toFloat() / 500.0f).coerceIn(-1f, 1f)
        val s4 = (buffer.getShort(12).toFloat() / 500.0f).coerceIn(-1f, 1f)

        val pool = mutableListOf<Float>()
        // 101:S1, 102:S2, 103:S3, 104:S4
        pool.add(s1); pool.add(s2); pool.add(s4); pool.add(s3)

        // 2. 輔助開關區 (CH5 ~ CH24) - 套用純淨化濾鏡
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

    /**
     * [v1.2.81 補丁] 輔助通道純淨化濾鏡 (Hysteresis Filter)
     * 消除電訊抖動，將連續信號轉為明確的 -1, 0, 1 段位。
     */
    private fun quantizeAux(value: Float): Float {
        return when {
            value <= -0.5f -> -1.0f
            value >= 0.5f -> 1.0f
            else -> 0.0f
        }
    }


    override fun getDefaultMapping(mode: Int, key: String): ChannelMapping {
        // [v1.2.82 物理真相校準] 101:S1(Roll), 102:S2(Yaw), 103:S3(Pitch), 104:S4(Throttle)
        return when(key) {
            "ly" -> ChannelMapping(104, true, if(mode==2 || mode==3) "油門 Throttle" else "俯仰 Pitch")
            "lx" -> ChannelMapping(103, false, if(mode==3 || mode==4) "橫滾 Roll" else "航向 Yaw")
            "ry" -> ChannelMapping(102, false, if(mode==1 || mode==4) "油門 Throttle" else "俯仰 Pitch")
            "rx" -> ChannelMapping(101, false, if(mode==1 || mode==2) "橫滾 Roll" else "航向 Yaw")
            else -> ChannelMapping(-1)
        }
    }
}
