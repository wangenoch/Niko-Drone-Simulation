package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.DroneState
import kotlin.math.*

/**
 * [v1.7.5] 大氣物理管理組件 (Centralized Atmosphere Engine)
 * 負責計算風力向量、陣風、以及雲層的大氣位移。
 */
object WindManager {

    /**
     * 計算當前風力受力向量
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

        val jitterScale = (dirVariation / 5f) * 45f 
        val jitterAngle = if (dirVariation > 0) sin(flightTime * 1.5f) * jitterScale else 0f

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
     * [歸位] 計算雲層在大氣中的位移 (U/V 滾動)
     * 渲染器應只負責繪製，位移邏輯屬於大氣物理。
     */
    fun updateCloudDrift(state: DroneState, dt: Float) {
        val wVec = calculateWindVector(state.windLevel, state.windDirection, 0, 0, 0f, 0f)
        state.env.cloudU -= wVec[0] * 0.003f * (dt / 0.016f)
        state.env.cloudV -= wVec[1] * 0.003f * (dt / 0.016f)
    }

    fun calculateGust(variation: Int, randomWindPhase: Float, flightTime: Float, level: Int, useHardcore: Boolean): Float {
        var gust = 1.0f + (variation * 0.35f) * (sin(randomWindPhase) * 0.6f + sin(randomWindPhase * 2.2f) * 0.4f)
        if (useHardcore && level > 0) gust += (sin(flightTime * 15f) * 0.1f).toFloat() * level
        return gust
    }

    fun calculateHeightFactor(altitude: Float, groundY: Float): Float {
        val h = (altitude - groundY).coerceAtLeast(0f)
        if (h <= 0.01f) return 0f // 離地 1cm 以下風力係數為 0
        return (ln(max(0.01f, h) / 0.05f) / ln(10f / 0.05f)).coerceIn(0f, 1.3f)
    }
}
