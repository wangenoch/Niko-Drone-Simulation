package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.DroneState

/**
 * [v1.4.2] 視覺預設優化引擎
 * 根據站位高度與視角模式，自動計算最適合的 FOV、倍率與抬頭角度。
 */
object ViewportOptimizer {

    data class ViewportParams(
        val fov: Float,
        val zoom: Float,
        val tilt: Float
    )

    /**
     * 獲取基於當前狀態的最佳視覺參數
     * 採用線性插值邏輯，確保高度變化時視覺平滑。
     */
    fun getOptimalParams(height: Float, cameraMode: String): ViewportParams {
        val isTrack = cameraMode.contains("追蹤")
        
        return when {
            // 情況 A: 1.6m 站位 (模擬真人站立高度)
            height <= 1.6f -> {
                if (isTrack) ViewportParams(fov = 45f, zoom = 1.5f, tilt = 4f)
                else ViewportParams(fov = 45f, zoom = 1.5f, tilt = -5f)
            }
            // 情況 B: 8.0m 以上高空站位 (考考監控位)
            height >= 8.0f -> {
                if (isTrack) ViewportParams(fov = 45f, zoom = 1.5f, tilt = 4f)
                else ViewportParams(fov = 70f, zoom = 1.0f, tilt = 9f)
            }
            // 中間過渡地帶 (1.6m ~ 8.0m)
            else -> {
                val t = (height - 1.6f) / (8.0f - 1.6f)
                if (isTrack) {
                    // 追蹤模式下，大部份參數維持恆定
                    ViewportParams(fov = 45f, zoom = 1.5f, tilt = 4f)
                } else {
                    // 固定模式下，從 (45, 1.5, -5) 過渡到 (70, 1.0, 9)
                    ViewportParams(
                        fov = 45f + (70f - 45f) * t,
                        zoom = 1.5f + (1.0f - 1.5f) * t,
                        tilt = -5f + (9f - (-5f)) * t
                    )
                }
            }
        }
    }

    /**
     * 執行自動校準應用 (用於重置或即時同步)
     * @param smooth 是否使用平滑過渡 (如果是智慧觀察員每幀呼叫，建議設為 true)
     */
    fun applyOptimization(state: DroneState, smooth: Boolean = false) {
        val target = getOptimalParams(state.observerHeight, state.cameraMode)
        
        if (smooth) {
            val lerpFactor = 0.05f // 平滑係數
            state.mainFOV += (target.fov - state.mainFOV) * lerpFactor
            state.zoomFactor += (target.zoom - state.zoomFactor) * lerpFactor
            state.observerTilt += (target.tilt - state.observerTilt) * lerpFactor
        } else {
            state.mainFOV = target.fov
            state.zoomFactor = target.zoom
            state.observerTilt = target.tilt
        }
    }
}
