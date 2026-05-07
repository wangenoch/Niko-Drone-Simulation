package com.horizon.caadronesimulator.logic

import kotlin.math.*

/**
 * [v1.3.9] 獨立風力物理管理組件
 * 負責計算風力向量、隨機偏擺與各項大氣模型運算。
 */
object WindManager {

    /**
     * 計算當前風力受力向量
     * 支援 v1.3.7 氣象學定義修正：
     * 北風(0°): 往南(0, -1), 南風(180°): 往北(0, 1), 東風(-90°): 往西(-1, 0), 西風(90°): 往東(1, 0)
     */
    fun calculateWindVector(
        level: Int,
        direction: String,
        variation: Int,
        dirVariation: Int,
        flightTime: Float,
        randomDirAngle: Float
    ): FloatArray {
        if (level <= 0 || direction == "無") return floatArrayOf(0f, 0f)

        // 全風向隨機偏擺擴充 (±45°)
        val jitterScale = (dirVariation / 5f) * 45f 
        val jitterAngle = if (dirVariation > 0) {
            sin(flightTime * 1.5f) * jitterScale
        } else 0f

        return if (direction == "隨機") {
            floatArrayOf(sin(randomDirAngle), cos(randomDirAngle))
        } else {
            val baseAngle = when(direction) {
                "北風" -> 0f
                "南風" -> 180f
                "東風" -> -90f
                "西風" -> 90f
                else -> 0f
            }
            val finalRad = Math.toRadians((baseAngle + jitterAngle).toDouble()).toFloat()
            floatArrayOf(sin(finalRad), -cos(finalRad))
        }
    }

    /**
     * 計算陣風強度 (Gust)
     */
    fun calculateGust(
        variation: Int,
        randomWindPhase: Float,
        flightTime: Float,
        level: Int,
        useHardcore: Boolean
    ): Float {
        var gust = 1.0f + (variation * 0.35f) * (sin(randomWindPhase) * 0.6f + sin(randomWindPhase * 2.2f) * 0.4f)
        if (useHardcore && level > 0) {
            val turbulence = (sin(flightTime * 15f) * 0.1f).toFloat() // 15Hz 高頻顫震
            gust += turbulence * level
        }
        return gust
    }

    /**
     * 計算高度感應風切變係數
     */
    fun calculateHeightFactor(altitude: Float, groundY: Float): Float {
        val h = max(0.1f, altitude - groundY)
        // 近地 0.05m 風力為 10%，10m 處達到 100%
        return (ln(h / 0.05f) / ln(10f / 0.05f)).coerceIn(0.1f, 1.3f)
    }
}
