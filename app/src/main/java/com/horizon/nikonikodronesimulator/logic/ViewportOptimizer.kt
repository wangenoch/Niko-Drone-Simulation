package com.horizon.nikonikodronesimulator.logic

import com.horizon.nikonikodronesimulator.model.DroneState

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

    fun getOptimalParams(observerHeight: Float, droneAltitude: Float, dronePosX: Float, dronePosZ: Float, cameraMode: String): ViewportParams {
        val d = kotlin.math.sqrt(dronePosX * dronePosX + dronePosZ * dronePosZ)
        val h = droneAltitude

        // [v1.7.12] 嚴格執行憲法模式：優先尊重 AppConfig 預設值，移除近距離強制 4 度偏移
        // 這解決了小型裝置在重置時會跳轉到非 0 度視角的問題
        return when {
            // 1. 起槳保護 (H < 0.2m)
            h < 0.2f -> ViewportParams(
                fov = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.MAIN_FOV,
                zoom = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING,
                tilt = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
            )

            // 2. 垂直越頂鎖定 (D < 2.0m) - 回歸 AppConfig 基準
            d < 2.0f -> ViewportParams(
                fov = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.MAIN_FOV,
                zoom = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING,
                tilt = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
            )

            // 3. 遠航/高空模式 (D > 20m 或 H > 15m)
            h > 15f || d > 20f -> ViewportParams(fov = 85f, zoom = 1.0f, tilt = 10f)

            // 4. 場內模式 (H < 15m)
            else -> ViewportParams(fov = 55f, zoom = 1.3f, tilt = -5f)
        }
    }

    /**
     * 執行自動校準應用 (全參數套用 0.02f 穩定插值)
     */
    fun applyOptimization(state: DroneState, smooth: Boolean = false) {
        val target = getOptimalParams(state.observerHeight, state.altitude, state.posX, state.posZ, state.cameraMode)
        
        if (smooth) {
            val lerpFactor = 0.02f
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
