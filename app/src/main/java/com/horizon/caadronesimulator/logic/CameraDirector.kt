package com.horizon.caadronesimulator.logic

import android.opengl.Matrix
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.model.DroneState
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

        val isObserverMode = (cameraMode == "觀察員視角 (實驗性)")

        if (isObserverMode && !isOverrideActive) {
            finalTargetHeight = when {
                relH < 0.2f -> 1.6f
                relH > 15.0f || d > 20.0f -> 1.6f
                else -> 8.0f
            }
            finalTargetTilt = strategy.tilt
            finalTargetZoom = strategy.zoom
            finalTargetFov = strategy.fov
        } else if (cameraMode == "站位視角 (智慧)") {
            // [v1.6.2] 智慧縮放模式：根據距離動態計算推近鏡頭
            finalTargetHeight = targetHeight
            finalTargetTilt = targetTilt
            // 基礎 1.2x，每飛遠 12 米增加 1.0x 放大
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
        
        // Zoom Pip 距離計算 (Datum: H-Pad 為原點，相機在 -9)
        val distToCam = sqrt(droneX.pow(2) + (smoothedHeight - droneY).pow(2) + (droneZ + 9f).pow(2))
        val targetZoomPipFov = (120f / distToCam).coerceIn(3f, 45f)
        smoothedZoomPipFov += (targetZoomPipFov - smoothedZoomPipFov) * lerp
    }

    fun computeMainViewMatrix(
        vMatrix: FloatArray, mode: String, 
        curX: Float, curY: Float, curZ: Float, curYaw: Float,
        predictX: Float, predictZ: Float, cameraTilt: Float, droneType: String
    ) {
        val dx = curX - 0f
        val dz = curZ - (-9f) // 基準深度位移至 -9
        val distH = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)

        when {
            mode == "站位視角 (追蹤)" -> {
                val isOverhead = distH < 2.0f
                val upY = if (isOverhead) 0f else 1f
                val upZ = if (isOverhead) 1f else 0f
                val rad = Math.toRadians(smoothedTilt.toDouble()).toFloat()
                val verticalShift = if (isOverhead) 0f else tan(rad) * distH
                Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, curX, curY + verticalShift, curZ, 0f, upY, upZ)
            }
            mode == "站位視角 (智慧)" -> {
                // [v1.6.2] 智慧縮放模式：共用追蹤矩陣邏輯，其縮放效果由 smoothedZoom 實現
                val dx = curX - 0f
                val dz = curZ - (-9f)
                val distH = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
                val isOverhead = distH < 2.0f
                val upY = if (isOverhead) 0f else 1f
                val upZ = if (isOverhead) 1f else 0f
                val rad = Math.toRadians(smoothedTilt.toDouble()).toFloat()
                val verticalShift = if (isOverhead) 0f else tan(rad) * distH
                Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, curX, curY + verticalShift, curZ, 0f, upY, upZ)
            }
            mode == "站位視角 (固定)" -> {
                // [v1.6.1] 站位視角 (固定)：視線基準為中心點 (0,0,0)，仰角拉桿控制抬頭
                val rad = Math.toRadians(smoothedTilt.toDouble()).toFloat()
                // 固定視距為 9.0m (從站位 -9 到中心 0)，根據角度計算垂直偏移量
                val verticalTargetShift = tan(rad) * 9.0f
                Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, 0f, verticalTargetShift, 0f, 0f, 1f, 0f)
            }
            mode == "觀察員視角 (實驗性)" -> {
                val isOverhead = distH < 2.0f
                val upY = if (isOverhead) 0f else 1f
                val upZ = if (isOverhead) 1f else 0f
                val rad = Math.toRadians(smoothedTilt.toDouble()).toFloat()
                val verticalShift = if (isOverhead) 0f else tan(rad) * distH
                Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, predictX, curY + verticalShift, predictZ, 0f, upY, upZ)
            }
            mode == "跟隨視角" -> {
                val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                val camX = curX - sin(rad) * 5f; val camZ = curZ - cos(rad) * 5f
                Matrix.setLookAtM(vMatrix, 0, camX, curY + 2.5f, camZ, curX, curY, curZ, 0f, 1f, 0f)
            }
            mode == "FPV 視角" -> {
                val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                val tRad = Math.toRadians(cameraTilt.toDouble()).toFloat()
                val spec = DroneRegistry.getSpec(droneType)
                val s = spec.scale
                val fOff = spec.cameraVisualOffset * s
                val eY = curY + 0.1f * s
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
        val dx = curX - 0f
        val dz = curZ - (-9f)
        val distH = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        val isOverhead = distH < 1.5f
        val upY = if (isOverhead) 0f else 1f
        val upZ = if (isOverhead) 1f else 0f
        Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -9f, curX, curY, curZ, 0f, upY, upZ)
    }
}
