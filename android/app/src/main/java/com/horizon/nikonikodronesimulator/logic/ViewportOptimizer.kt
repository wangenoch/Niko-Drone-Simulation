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

        val v = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults
        val baseFov = when(cameraMode) {
            com.horizon.nikonikodronesimulator.model.AppConfig.CAM_MODE_STATION_FIXED -> v.DEFAULT_FIXED_FOV
            com.horizon.nikonikodronesimulator.model.AppConfig.CAM_MODE_STATION_TRACK -> v.DEFAULT_TRACKING_FOV
            com.horizon.nikonikodronesimulator.model.AppConfig.CAM_MODE_STATION_SMART -> v.DEFAULT_SMART_FOV
            else -> v.MAIN_FOV
        }

        // [v1.7.15] 憲法級動態視覺優化：基於模式專屬 FOV 進行階梯式縮放
        return when {
            // 1. 起槳與近場保護 (H < 0.2m 或 D < 2.0m)
            h < 0.2f || d < 2.0f -> ViewportParams(
                fov = baseFov,
                zoom = v.ZOOM_FACTOR_TRACKING,
                tilt = v.OBSERVER_TILT_TRACKING
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
