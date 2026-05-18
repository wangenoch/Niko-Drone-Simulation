package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.DroneState
import kotlin.math.*

/**
 * [v1.6.1] 大氣物理管理組件 (Centralized Atmosphere Engine)
 * 負責計算風力向量、陣風、以及雲層的大氣位移。
 * 修正：新增 360 度方位支援，對應 3x3 風向九宮格。
 * 升級：落實「真隨機」邏輯，將方位基準歸位至 DroneState。
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
        state: DroneState
    ): FloatArray {
        if (level <= 0 || direction == "無") return floatArrayOf(0f, 0f)

        val jitterScale = (dirVariation / 5f) * 45f 
        val jitterAngle = if (dirVariation > 0) sin(flightTime * 1.5f) * jitterScale else 0f

        val finalAngle = if (direction == "隨機") {
            // [v1.6.1] 隨機風亂流邏輯：使用全域同步的 randomWindAngle 基準
            val baseWander = (state.env.randomWindAngle + (flightTime * 5f)) % 360f 
            val wander = sin(flightTime * 0.8f) * jitterScale 
            
            // 亂流判定：當風力與激烈度拉滿時加入高頻抖動
            val isExtreme = level >= 4 && dirVariation >= 4
            val turbulence = if (isExtreme) sin(flightTime * 25f) * 15f else 0f
            
            baseWander + wander + turbulence
        } else {
            val baseAngle = when(direction) {
                "北風" -> 0f
                "東北風" -> -45f
                "東風" -> -90f
                "東南風" -> -135f
                "南風" -> 180f
                "西南風" -> 135f
                "西風" -> 90f
                "西北風" -> 45f
                else -> 0f
            }
            baseAngle + jitterAngle
        }

        // 同步回寫實時物理角，供 UI 指標與雲層位移使用
        state.env.currentWindAngle = finalAngle

        val finalRad = Math.toRadians(finalAngle.toDouble()).toFloat()
        return floatArrayOf(sin(finalRad), -cos(finalRad))
    }

    /**
     * [v1.6.1] 修正：雲層位移現在直接引用當前物理風向
     */
    fun updateCloudDrift(state: DroneState, dt: Float) {
        // 取得當前真實受力向量 (已在物理循環中更新過角度)
        val rad = Math.toRadians(state.env.currentWindAngle.toDouble()).toFloat()
        val wX = sin(rad)
        val wV = -cos(rad)
        
        state.env.cloudU -= wX * 0.003f * (dt / 0.016f)
        state.env.cloudV -= wV * 0.003f * (dt / 0.016f)
    }

    fun calculateGust(variation: Int, randomWindPhase: Float, flightTime: Float, level: Int, useHardcore: Boolean): Float {
        var gust = 1.0f + (variation * 0.35f) * (sin(randomWindPhase) * 0.6f + sin(randomWindPhase * 2.2f) * 0.4f)
        if (useHardcore && level > 0) gust += (sin(flightTime * 15f) * 0.1f).toFloat() * level
        return gust
    }

    fun calculateHeightFactor(altitude: Float, groundY: Float): Float {
        val h = (altitude - groundY).coerceAtLeast(0f)
        if (h <= 0.01f) return 0f
        return (ln(max(0.01f, h) / 0.05f) / ln(10f / 0.05f)).coerceIn(0f, 1.3f)
    }
}
