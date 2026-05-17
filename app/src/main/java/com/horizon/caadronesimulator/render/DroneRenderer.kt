package com.horizon.caadronesimulator.render

import android.opengl.Matrix
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.render.util.RenderUtils
import kotlin.math.*

/**
 * [v1.5.3] 無人機模型渲染器 (Data-Driven Geometry)
 * 職責：讀取機型專屬幾何描述，動態繪製機身與動態零件。
 */
object DroneRenderer {

    fun drawDroneShadow(
        posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, 
        droneType: String, curX: Float, curY: Float, curZ: Float, 
        timeOfDay: String, showShadow: Boolean, shadowIntensity: Float,
        isSunSimEnabled: Boolean = false, sunPosition: Float = 0.5f
    ) {
        if (!showShadow) return
        val spec = DroneRegistry.getSpec(droneType)
        val relativeAlt = (curY - spec.groundOffset).coerceAtLeast(0f)
        
        val shadowX = if (isSunSimEnabled) {
            val angle = Math.toRadians((sunPosition * 180f).toDouble()).toFloat()
            val offsetFactor = -cos(angle) * 1.2f 
            curX + relativeAlt * offsetFactor
        } else {
            curX + (if (timeOfDay == "早晨") relativeAlt * 0.8f else if (timeOfDay == "下午") -relativeAlt * 0.8f else 0f)
        }
        
        val totalAlpha = (shadowIntensity / (1f + relativeAlt * 0.5f)).coerceIn(0f, shadowIntensity)
        if (totalAlpha < 0.001f) return

        val blurFactor = (relativeAlt * 0.15f).coerceIn(0f, 1.2f)
        
        for (i in 5 downTo 1) {
            val ratio = i / 5f 
            val layerRadius = spec.shadowSizeBase * (1f + blurFactor * (2.0f - ratio))
            val layerAlpha = (totalAlpha / 1.8f) * (ratio * ratio)
            
            if (layerAlpha > 0.005f) {
                RenderUtils.drawFilledCircle(
                    posH, colorH, mvpH, mvpMatrix, 
                    shadowX, 
                    0.04f + i * 0.001f, 
                    curZ, 
                    layerRadius, 
                    floatArrayOf(0f, 0f, 0f, layerAlpha)
                )
            }
        }
    }

    /**
     * [v1.5.3] 數據驅動繪製核心
     */
    fun drawActiveDrone(
        posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, 
        droneId: String, curX: Float, curY: Float, curZ: Float, 
        curYaw: Float, visPitch: Float, visRoll: Float, 
        flightTime: Float, isMotorLocked: Boolean, motorRpmFactor: Float = 1.0f
    ) {
        val spec = DroneRegistry.getSpec(droneId)
        val module = DroneRegistry.getModule(droneId)
        val s = spec.scale
        val drawY = curY + spec.visualOffset 

        val baseM = FloatArray(16)
        Matrix.setIdentityM(baseM, 0)
        Matrix.translateM(baseM, 0, curX, drawY, curZ)
        
        // 1. 基礎姿態旋轉 (Yaw -> Pitch -> Roll)
        Matrix.rotateM(baseM, 0, curYaw, 0f, 1f, 0f)
        Matrix.rotateM(baseM, 0, -visPitch, 1f, 0f, 0f)
        Matrix.rotateM(baseM, 0, -visRoll, 0f, 0f, 1f) // [v1.5.3] 修正 Roll 極性

        // 2. 遍歷零件清單繪製
        module.geometry.forEach { part ->
            val finalRy = if (part.isPropeller) {
                val pSpd = if(!isMotorLocked) flightTime * part.baseRpm * motorRpmFactor else 0f
                part.ry + pSpd * part.rotationDirection
            } else if (part.isTailPropeller) {
                // 尾槳旋轉邏輯
                0f // 尾槳旋轉由下面的 rz 疊加處理，此處 ry 固定
            } else {
                part.ry
            }

            val finalRz = if (part.isTailPropeller) {
                val pSpd = if(!isMotorLocked) flightTime * part.baseRpm * motorRpmFactor else 0f
                part.rz + pSpd * part.rotationDirection
            } else {
                part.rz
            }

            RenderUtils.drawBox(
                posH, colorH, mvpH, mvpMatrix, baseM,
                part.tx * s, part.ty * s, part.tz * s, // 應用全局比例
                part.w * s, part.h * s, part.d * s,
                part.color,
                ry = finalRy,
                rx = part.rx,
                rz = finalRz
            )
        }
    }
}
