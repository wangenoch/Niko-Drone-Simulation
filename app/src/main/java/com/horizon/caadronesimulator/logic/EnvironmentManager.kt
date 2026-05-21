package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.DroneState
import kotlin.math.*

/**
 * [v1.6.3] 全域環境管理器 (Environment Manager)
 * 職責：處理時間、天氣、太陽位置與環境色彩的換算。
 * 目的：將原本 Renderer 內的數值運算抽離，達成「數據驅動渲染」。
 */
object EnvironmentManager {

    /**
     * 獲取當前的天空清除顏色 (R, G, B, A)
     */
    fun getSkyClearColor(state: DroneState): FloatArray {
        if (state.isSunSimEnabled) {
            val angle = Math.toRadians((state.sunPosition * 180f).toDouble()).toFloat()
            val s = sin(angle)
            val r = (1.0f - (1.0f - 0.53f) * s).coerceIn(0.53f, 1.0f)
            val g = (0.6f + 0.21f * s).coerceIn(0.6f, 0.81f)
            val b = (0.3f + 0.62f * s).coerceIn(0.3f, 0.92f)
            return floatArrayOf(r, g, b, 1.0f)
        } else {
            if (state.weatherMode == 3) { // 低空層雲 (陰天)
                return floatArrayOf(0.4f, 0.45f, 0.5f, 1.0f)
            } else {
                return when (state.timeOfDay) {
                    AppConfig.TIME_MORNING -> floatArrayOf(1.0f, 0.7f, 0.5f, 1.0f)
                    AppConfig.TIME_AFTERNOON -> floatArrayOf(1.0f, 0.6f, 0.3f, 1.0f)
                    else -> floatArrayOf(0.53f, 0.81f, 0.92f, 1.0f)
                }
            }
        }
    }

    /**
     * 獲取當前的雲朵色彩
     */
    fun getCloudColor(state: DroneState): FloatArray {
        return if (state.weatherMode == 3) {
            floatArrayOf(0.7f, 0.7f, 0.75f, 0.85f)
        } else {
            floatArrayOf(1.0f, 1.0f, 1.0f, 0.7f)
        }
    }
}
