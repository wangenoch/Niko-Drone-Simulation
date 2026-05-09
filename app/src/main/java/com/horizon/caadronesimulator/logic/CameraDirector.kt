package com.horizon.caadronesimulator.logic

import android.opengl.Matrix
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.model.DroneState
import kotlin.math.*

/**
 * [v1.6.0] 專業相機導演系統 (獨立化架構)
 * 職責：統籌主畫面、PiP 子畫面、Zoom PiP 的視覺邏輯，執行全系統 0.015f 超平滑濾波。
 */
object CameraDirector {

    // 平滑插值狀態儲存
    var smoothedFov = 45f; private set
    var smoothedZoom = 1.5f; private set
    var smoothedTilt = 0f; private set
    var smoothedHeight = 6.0f; private set
    
    // 姿態輔助視角專用緩衝
    var smoothedZoomPipFov = 45f; private set

    /**
     * 每幀更新：視覺導演決策 (由 Renderer 調用)
     * @param targetHeight 使用者或系統設定的目標高度
     * @param targetTilt 使用者或系統設定的仰角
     * @param targetZoom 使用者設定的倍率
     * @param targetFov 使用者設定的視野
     * @param state 用於將平滑數據寫回 UI，達成自動還原動畫
     */
    fun update(
        droneX: Float, droneY: Float, droneZ: Float,
        targetHeight: Float, targetTilt: Float, targetZoom: Float, targetFov: Float,
        cameraMode: String, useSmartObserver: Boolean, lastManualTouchTime: Long,
        droneType: String, dt: Float, state: DroneState
    ) {
        val relH = droneY.coerceAtLeast(0f)
        val d = sqrt(droneX * droneX + droneZ * droneZ)
        
        // 1. 取得術科情境策略參數 (從 ViewportOptimizer 獲取理想目標)
        val strategy = ViewportOptimizer.getOptimalParams(targetHeight, relH, droneX, droneZ, cameraMode)
        
        // 2. 判定是否處於手動覆蓋狀態 (3秒內)
        val isOverrideActive = (System.currentTimeMillis() - lastManualTouchTime < 3000)

        val finalTargetHeight: Float
        val finalTargetTilt: Float
        val finalTargetZoom: Float
        val finalTargetFov: Float

        if (useSmartObserver && !isOverrideActive && cameraMode.contains("站位視角")) {
            // [智慧/還原模式] 執行自動導播或還原策略
            finalTargetHeight = when {
                relH < 0.2f -> 1.6f
                relH > 15.0f || d > 20.0f -> 1.6f
                else -> 8.0f
            }
            finalTargetTilt = strategy.tilt
            finalTargetZoom = strategy.zoom
            finalTargetFov = strategy.fov
        } else {
            // [手動模式/覆蓋中] 追隨 UI 拉桿數值，不進行還原
            finalTargetHeight = targetHeight
            finalTargetTilt = targetTilt
            finalTargetZoom = targetZoom
            finalTargetFov = targetFov
        }

        // 3. 全系統 0.015f 超平滑濾波 (達成沉穩導播感)
        val lerp = 0.015f
        smoothedFov += (finalTargetFov - smoothedFov) * lerp
        smoothedZoom += (finalTargetZoom - smoothedZoom) * lerp
        smoothedTilt += (finalTargetTilt - smoothedTilt) * lerp
        smoothedHeight += (finalTargetHeight - smoothedHeight) * lerp

        // 4. [關鍵修復] 將平滑後的數值寫回 DroneState，達成 UI 拉桿自動滑動還原視覺效果
        if (useSmartObserver && !isOverrideActive) {
            state.observerHeight = smoothedHeight
            state.observerTilt = smoothedTilt
            state.zoomFactor = smoothedZoom
            state.mainFOV = smoothedFov
        }
        
        // 5. Zoom Pip 動態 FOV 計算 (精準模式專用)
        val distToCam = sqrt(droneX.pow(2) + (smoothedHeight - droneY).pow(2) + (droneZ + 15f).pow(2))
        val targetZoomPipFov = (120f / distToCam).coerceIn(3f, 45f)
        smoothedZoomPipFov += (targetZoomPipFov - smoothedZoomPipFov) * lerp
    }

    /**
     * 輸出主視圖矩陣 (構圖模式：含仰角位移，確保地景比例)
     */
    fun computeMainViewMatrix(
        vMatrix: FloatArray, mode: String, 
        curX: Float, curY: Float, curZ: Float, curYaw: Float,
        predictX: Float, predictZ: Float, cameraTilt: Float, droneType: String
    ) {
        val dx = curX - 0f
        val dz = curZ - (-15f)
        val distH = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)

        when {
            mode.contains("站位視角 (追蹤)") -> {
                val isOverhead = distH < 2.0f
                val upY = if (isOverhead) 0f else 1f
                val upZ = if (isOverhead) 1f else 0f
                val rad = Math.toRadians(smoothedTilt.toDouble()).toFloat()
                
                // 構圖位移：越頂時關閉位移防止旋轉
                val verticalShift = if (isOverhead) 0f else tan(rad) * distH
                Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -15f, predictX, curY + verticalShift, predictZ, 0f, upY, upZ)
            }
            mode == "跟隨視角" -> {
                val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                val camX = curX - sin(rad) * 5f; val camZ = curZ - cos(rad) * 5f
                Matrix.setLookAtM(vMatrix, 0, camX, curY + 2.5f, camZ, curX, curY, curZ, 0f, 1f, 0f)
            }
            mode == "FPV 視角" -> {
                val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                val tRad = Math.toRadians(cameraTilt.toDouble()).toFloat()
                val s = DroneRegistry.getSpec(droneType).scale
                val fOff = if (droneType == "HEAVY_LIFT") 0.38f * s else 0.4f * s
                val eY = curY + 0.1f * s
                val eX = curX + sin(rad) * fOff
                val eZ = curZ + cos(rad) * fOff
                val lX = eX + sin(rad) * cos(tRad) * 10f
                val lZ = eZ + cos(rad) * cos(tRad) * 10f
                val lY = eY + sin(tRad) * 10f
                Matrix.setLookAtM(vMatrix, 0, eX, eY, eZ, lX, lY, lZ, -sin(rad) * sin(tRad), cos(tRad), -cos(rad) * sin(tRad))
            }
            else -> Matrix.setLookAtM(vMatrix, 0, 0f, 6f, -22f, 0f, 0f, 0f, 0f, 1f, 0f)
        }
    }

    /**
     * 輸出精準視圖矩陣 (精準模式：100% 鎖定飛機中心，無位移)
     * 注入垂直越頂保護，防止仰角過大導致矩陣坍塌
     */
    fun computePrecisionViewMatrix(
        vMatrix: FloatArray,
        curX: Float, curY: Float, curZ: Float
    ) {
        val dx = curX - 0f
        val dz = curZ - (-15f)
        val distH = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        
        // 奇點保護：當飛機在正上方時，翻轉 Up-Vector
        val isOverhead = distH < 1.5f
        val upY = if (isOverhead) 0f else 1f
        val upZ = if (isOverhead) 1f else 0f

        Matrix.setLookAtM(vMatrix, 0, 0f, smoothedHeight, -15f, curX, curY, curZ, 0f, upY, upZ)
    }
}
