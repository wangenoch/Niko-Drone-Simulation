package com.horizon.nikonikodronesimulator.logic

import android.opengl.Matrix
import com.horizon.nikonikodronesimulator.model.AppConfig
import com.horizon.nikonikodronesimulator.model.DroneRegistry
import com.horizon.nikonikodronesimulator.model.DroneState
import kotlin.math.*

/**
 * [v1.5.3] 專業相機導演系統 (Normalized Datum: H-Pad)
 * 基準：H-Pad 為 (0,0,0)，觀察員初始位於 (0, 6, -9)。
 */
object CameraDirector {

    var smoothedFov = 45f; private set
    var smoothedZoom = 1.5f; private set
    var smoothedTilt = 0f; private set
    var smoothedHeight = 6.0f; private set
    var smoothedZoomPipFov = 45f; private set

    /** [v1.7.12] 強制歸位：立即同步所有平滑緩衝區，徹底消除重置時的「慢慢飄移」感 */
    fun snapToDefaults(height: Float, tilt: Float, zoom: Float, fov: Float) {
        smoothedHeight = height
        smoothedTilt = tilt
        smoothedZoom = zoom
        smoothedFov = fov
    }

    fun update(
        droneX: Float, droneY: Float, droneZ: Float,
        targetHeight: Float, targetTilt: Float, targetZoom: Float, targetFov: Float,
        cameraMode: String, lastManualTouchTime: Long,
        droneType: String, dt: Float, state: DroneState
    ) {
        val relH = droneY.coerceAtLeast(0f)
        val d = sqrt(droneX * droneX + droneZ * droneZ) // 直接計算離 H 點距離
        
        val strategy = ViewportOptimizer.getOptimalParams(targetHeight, relH, droneX, droneZ, cameraMode)
        val isOverrideActive = (System.currentTimeMillis() - lastManualTouchTime < 3000)

        val finalTargetHeight: Float
        val finalTargetTilt: Float
        val finalTargetZoom: Float
        val finalTargetFov: Float

        val isObserverMode = (cameraMode == AppConfig.CAM_MODE_OBS)

        if (isObserverMode && !isOverrideActive) {
            // [v1.7.7] 觀察員視角：智慧高度適配 (近距離升起，遠距離降下)
            finalTargetHeight = if (d < 8.0f) {
                val factor = (d / 8.0f).coerceIn(0f, 1f)
                5.0f - (factor * 3.4f) // 近處 5.0m -> 遠處 1.6m
            } else {
                1.6f
            }
            finalTargetTilt = strategy.tilt
            finalTargetZoom = strategy.zoom
            finalTargetFov = strategy.fov
        } else if (cameraMode == AppConfig.CAM_MODE_STATION_SMART) {
            finalTargetHeight = targetHeight
            finalTargetTilt = targetTilt
            finalTargetZoom = (1.2f + (d / 12.0f)).coerceIn(1.0f, 4.0f)
            finalTargetFov = targetFov
        } else {
            finalTargetHeight = targetHeight
            finalTargetTilt = targetTilt
            finalTargetZoom = targetZoom
            finalTargetFov = targetFov
        }

        val lerp = 0.015f
        smoothedFov += (finalTargetFov - smoothedFov) * lerp
        smoothedZoom += (finalTargetZoom - smoothedZoom) * lerp
        smoothedTilt += (finalTargetTilt - smoothedTilt) * lerp
        smoothedHeight += (finalTargetHeight - smoothedHeight) * lerp

        if (isObserverMode && !isOverrideActive) {
            state.observerHeight = smoothedHeight
            state.observerTilt = smoothedTilt
            state.zoomFactor = smoothedZoom
            state.mainFOV = smoothedFov
        }
        
        // Zoom Pip 距離計算
        val distToCam = sqrt(droneX.pow(2) + (smoothedHeight - droneY).pow(2) + (droneZ + 9f).pow(2))
        val targetZoomPipFov = (120f / distToCam).coerceIn(3f, 45f)
        smoothedZoomPipFov += (targetZoomPipFov - smoothedZoomPipFov) * lerp
    }

    fun computeMainViewMatrix(
        vMatrix: FloatArray, mode: String, 
        curX: Float, curY: Float, curZ: Float, curYaw: Float,
        predictX: Float, predictZ: Float, cameraTilt: Float, droneType: String,
        overrideVisualOffset: Float? = null // [v1.7.18] 動態覆寫鏡頭位置
    ) {
        val dx = curX - 0f
        val dz = curZ - (-9f)
        val distH = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)

        when {
            mode == AppConfig.CAM_MODE_STATION_SMART || mode == AppConfig.CAM_MODE_STATION_TRACK -> {
                val isOverhead = distH < 2.0f
                val upY = if (isOverhead) 0f else 1f
                val upZ = if (isOverhead) 1f else 0f
                val rad = Math.toRadians(smoothedTilt.toDouble()).toFloat()
                val verticalShift = if (isOverhead) 0f else tan(rad) * distH
                Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, curX, curY + verticalShift, curZ, 0f, upY, upZ)
            }
            mode == AppConfig.CAM_MODE_STATION_FIXED -> {
                val rad = Math.toRadians(smoothedTilt.toDouble()).toFloat()
                val verticalTargetShift = tan(rad) * 9.0f
                Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, 0f, verticalTargetShift, 0f, 0f, 1f, 0f)
            }
            mode == AppConfig.CAM_MODE_OBS -> {
                val isOverhead = distH < 2.0f
                val upY = if (isOverhead) 0f else 1f
                val upZ = if (isOverhead) 1f else 0f
                val rad = Math.toRadians(smoothedTilt.toDouble()).toFloat()
                val hWeight = ((curY - 3f) / 9f).coerceIn(0f, 1f)
                val currentFov = smoothedFov / smoothedZoom
                val targetBiasDeg = hWeight * (currentFov * 0.25f)
                val landingFactor = ((distH - 2f) / 3f).coerceIn(0f, 1f)
                val distanceScale = (distH / 6f).coerceIn(0.3f, 1.0f) * landingFactor
                val finalBiasRad = Math.toRadians((targetBiasDeg * distanceScale).toDouble()).toFloat()
                val verticalShift = if (!isOverhead) (tan(rad) * distH) - (tan(finalBiasRad) * distH) else 0f
                Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, predictX, curY + verticalShift, predictZ, 0f, upY, upZ)
            }
            mode == AppConfig.CAM_MODE_FOLLOW -> {
                val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                val camX = curX - sin(rad) * 5f; val camZ = curZ - cos(rad) * 5f
                Matrix.setLookAtM(vMatrix, 0, camX, curY + 2.5f, camZ, curX, curY, curZ, 0f, 1f, 0f)
            }
            mode == AppConfig.CAM_MODE_FPV -> {
                val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                val tRad = Math.toRadians(cameraTilt.toDouble()).toFloat()
                val spec = DroneRegistry.getSpec(droneType)
                val s = spec.scale
                val fOff = (overrideVisualOffset ?: spec.cameraVisualOffset) * s
                val eY = curY + spec.cameraHeightOffset * s // [v1.7.19] 使用機種專屬高度基因
                val eX = curX + sin(rad) * fOff
                val eZ = curZ + cos(rad) * fOff
                val lX = eX + sin(rad) * cos(tRad) * 10f
                val lZ = eZ + cos(rad) * cos(tRad) * 10f
                val lY = eY + sin(tRad) * 10f
                Matrix.setLookAtM(vMatrix, 0, eX, eY, eZ, lX, lY, lZ, -sin(rad) * sin(tRad), cos(tRad), -cos(rad) * sin(tRad))
            }
            else -> Matrix.setLookAtM(vMatrix, 0, 0f, 6f, -15f, 0f, 0f, 0f, 0f, 1f, 0f)
        }
    }

    fun computePrecisionViewMatrix(vMatrix: FloatArray, curX: Float, curY: Float, curZ: Float) {
        val dx = curX - 0f; val dz = curZ - (-9f); val distH = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        val isOverhead = distH < 1.5f; val upY = if (isOverhead) 0f else 1f; val upZ = if (isOverhead) 1f else 0f
        Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, curX, curY, curZ, 0f, upY, upZ)
    }
}
