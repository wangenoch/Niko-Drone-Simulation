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

        // 1. 決定基礎風向角 (風的來源方位: 0=北, 90=東, 180=南, 270=西)
        val baseFromAngle = if (direction == "隨機") {
            (state.env.randomWindAngle + (flightTime * 2f)) % 360f 
        } else {
            when(direction) {
                "北風" -> 0f; "東北風" -> 45f; "東風" -> 90f; "東南風" -> 135f
                "南風" -> 180f; "西南風" -> 225f; "西風" -> 270f; "西北風" -> 315f
                else -> 0f
            }
        }

        // 2. 計算流向角 (Flow Angle = From Angle + 180°) 並加入亂流
        val isExtreme = level >= 4 && dirVariation >= 4
        val turbulence = if (isExtreme) sin(flightTime * 25f) * 12f else 0f
        val flowAngle = (baseFromAngle + 180f + jitterAngle + turbulence) % 360f

        // 同步回寫實時物理流向角，供 HUD 與 渲染器統一調用
        state.env.currentWindAngle = flowAngle

        // 3. 轉換為 3D 物理向量 (X=右, Z=深處)
        val rad = Math.toRadians(flowAngle.toDouble()).toFloat()
        return floatArrayOf(sin(rad), cos(rad))
    }

    /** [v1.6.1] 雲層位移：直接同步物理流向 */
    fun updateCloudDrift(state: DroneState, dt: Float) {
        val rad = Math.toRadians(state.env.currentWindAngle.toDouble()).toFloat()
        state.env.cloudU += sin(rad) * 0.003f * (dt / 0.016f)
        state.env.cloudV += cos(rad) * 0.003f * (dt / 0.016f)
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
