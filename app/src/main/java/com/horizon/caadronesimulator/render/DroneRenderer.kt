package com.horizon.caadronesimulator.render

import android.opengl.Matrix
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.render.util.RenderUtils
import kotlin.math.*

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
        
        // 計算陰影偏移 (X 軸)
        val shadowX = if (isSunSimEnabled) {
            // 太陽模擬模式：0.0(東/左) -> 1.0(西/右)
            // 太陽在東方時，陰影向西(右)投射
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

    fun drawActiveDrone(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, droneType: String, curX: Float, curY: Float, curZ: Float, curYaw: Float, visPitch: Float, visRoll: Float, flightTime: Float, isMotorLocked: Boolean) {
        val spec = DroneRegistry.getSpec(droneType)
        val drawY = curY + spec.visualOffset 
        when (droneType) {
            "HEAVY_LIFT" -> drawHeavyLiftDrone(posH, colorH, mvpH, mvpMatrix, curX, drawY, curZ, curYaw, visPitch, visRoll, flightTime, isMotorLocked)
            "HELI_900" -> drawHelicopter(posH, colorH, mvpH, mvpMatrix, curX, drawY, curZ, curYaw, visPitch, visRoll, flightTime, isMotorLocked)
            else -> drawDrone(posH, colorH, mvpH, mvpMatrix, curX, drawY, curZ, curYaw, visPitch, visRoll, flightTime, isMotorLocked)
        }
    }

    private fun drawHelicopter(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float, time: Float, isMotorLocked: Boolean) {
        val s = DroneRegistry.getSpec("HELI_900").scale; val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
        Matrix.translateM(baseM, 0, x, y, z); Matrix.rotateM(baseM, 0, yaw, 0f, 1f, 0f); Matrix.rotateM(baseM, 0, pitch, 1f, 0f, 0f); Matrix.rotateM(baseM, 0, -roll, 0f, 0f, 1f)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.35f*s, 0.4f*s, 0.25f*s, 0.45f*s, 0.6f*s, floatArrayOf(0.1f, 0.1f, 0.1f, 1f)) 
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.55f*s, 0.6f*s, 0.2f*s, 0.2f*s, 0.3f*s, floatArrayOf(0f, 0.4f, 0.8f, 0.8f))   
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.4f*s, -0.6f*s, 0.12f*s, 0.12f*s, 1.2f*s, floatArrayOf(0.15f, 0.15f, 0.15f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.65f*s, 0.2f*s, 0.04f*s, 0.25f*s, 0.04f*s, floatArrayOf(0.2f, 0.2f, 0.2f, 1f)) 
        val mainPropSpd = if(!isMotorLocked) time*2200f else 0f
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.78f*s, 0.2f*s, 2.5f*s, 0.015f*s, 0.08f*s, floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), mainPropSpd)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.78f*s, 0.2f*s, 0.08f*s, 0.015f*s, 2.5f*s, floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), mainPropSpd)
        val tailPropSpd = if(!isMotorLocked) time*4000f else 0f
        Matrix.rotateM(baseM, 0, 90f, 0f, 0f, 1f) 
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0.4f*s, -0.1f*s, -1.1f*s, 0.6f*s, 0.01f*s, 0.04f*s, floatArrayOf(0.9f, 0.9f, 0.9f, 0.6f), tailPropSpd)
        Matrix.rotateM(baseM, 0, -90f, 0f, 0f, 1f) 
        arrayOf(-0.3f*s, 0.3f*s).forEach { sx ->
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, sx, 0.15f*s, 0.2f*s, 0.03f*s, 0.35f*s, 0.03f*s, floatArrayOf(0.2f, 0.2f, 0.2f, 1f))
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, sx, 0.0f, 0.2f*s, 0.04f*s, 0.04f*s, 1.2f*s, floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
        }
    }

    private fun drawHeavyLiftDrone(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float, time: Float, isMotorLocked: Boolean) {
        val s = DroneRegistry.getSpec("HEAVY_LIFT").scale; val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
        Matrix.translateM(baseM, 0, x, y, z); Matrix.rotateM(baseM, 0, yaw, 0f, 1f, 0f); Matrix.rotateM(baseM, 0, pitch, 1f, 0f, 0f); Matrix.rotateM(baseM, 0, -roll, 0f, 0f, 1f)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.1f, 0f, 0.6f*s, 0.05f*s, 0.6f*s, floatArrayOf(0f, 0.3f, 0.8f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.15f, 0f, 0.4f*s, 0.12f*s, 0.4f*s, floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.1f, -0.3f*s, 0.05f*s, 0.05f*s, 0.05f*s, floatArrayOf(0f, 1f, 0f, 1f))
        arrayOf(-0.12f*s, 0.12f*s).forEach { ax ->
            val antM = baseM.copyOf(); Matrix.translateM(antM, 0, ax, 0.2f, -0.18f*s); Matrix.rotateM(antM, 0, -15f, 1f, 0f, 0f) 
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, antM, 0f, 0.15f*s, 0f, 0.012f*s, 0.3f*s, 0.012f*s, floatArrayOf(0.15f, 0.15f, 0.15f, 1f))
        }
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.25f, 0f, 0.25f*s, 0.12f*s, 0.25f*s, floatArrayOf(0f, 0.3f, 0.8f, 1f))
        val armLen = 0.8f * s
        arrayOf(floatArrayOf(1f,1f), floatArrayOf(-1f,1f), floatArrayOf(1f,-1f), floatArrayOf(-1f,-1f)).forEach { p ->
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]*armLen/2*0.707f, 0.15f, p[1]*armLen/2*0.707f, armLen, 0.04f*s, 0.04f*s, floatArrayOf(0.05f, 0.05f, 0.05f, 1f), -45f*(p[0]*p[1]))
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]*armLen*0.707f, 0.15f, p[1]*armLen*0.707f, 0.12f*s, 0.08f*s, 0.12f*s, floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
            val ledColor = if(p[1] > 0) floatArrayOf(0f, 1f, 0f, 1f) else floatArrayOf(1f, 0f, 0f, 1f)
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]*armLen*0.707f, 0.11f, p[1]*armLen*0.707f, 0.14f*s, 0.03f*s, 0.14f*s, ledColor)
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]*armLen*0.707f, 0.2f, p[1]*armLen*0.707f, 0.7f*s, 0.01f, 0.04f*s, floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), if(!isMotorLocked) time*2500f*(p[0]*p[1]) else 0f)
        }
        val legX = 0.25f*s; val legZ = 0.2f*s
        arrayOf(floatArrayOf(legX, legZ), floatArrayOf(-legX, legZ), floatArrayOf(legX, -legZ), floatArrayOf(-legX, -legZ)).forEach { l -> RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, l[0], -0.2f*s, l[1], 0.03f, 0.5f*s, 0.03f, floatArrayOf(0.1f, 0.1f, 0.1f, 1f)) }
        arrayOf(legX, -legX).forEach { lx ->
            val skidColor = if(lx > 0) floatArrayOf(1f, 0f, 0f, 1f) else floatArrayOf(0f, 1f, 0f, 1f)
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, lx, -0.45f*s, 0f, 0.04f*s, 0.04f*s, 0.9f*s, skidColor)
            arrayOf(0.5f*s, -0.5f*s).forEach { tz -> RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, lx, -0.45f*s, tz, 0.05f*s, 0.05f*s, 0.15f*s, skidColor) }
        }
    }

    private fun drawDrone(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float, time: Float, isMotorLocked: Boolean) {
        val s = DroneRegistry.getSpec("QUAD_STANDARD").scale; val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
        Matrix.translateM(baseM, 0, x, y, z); Matrix.rotateM(baseM, 0, yaw, 0f, 1f, 0f); Matrix.rotateM(baseM, 0, pitch, 1f, 0f, 0f); Matrix.rotateM(baseM, 0, -roll, 0f, 0f, 1f)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0f, 0f, 0.4f*s, 0.15f*s, 0.8f*s, floatArrayOf(0.12f, 0.12f, 0.12f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.05f*s, 0.4f*s, 0.25f*s, 0.06f*s, 0.12f*s, floatArrayOf(1f, 0.4f, 0.0f, 1f))
        val pSpd = if(!isMotorLocked) time*2000f else 0f
        arrayOf(floatArrayOf(-0.45f*s, 0.45f*s, 0f,1f,0f), floatArrayOf(0.45f*s, 0.45f*s, 1f,0f,0f), floatArrayOf(-0.45f*s, -0.45f*s, 0f,1f,0f), floatArrayOf(0.45f*s, -0.45f*s, 1f,0f,0f)).forEach { p ->
            val side = if(p[0]*p[1]>0) 1f else -1f
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]/2, 0f, p[1]/2, 0.08f*s, 0.04f*s, 0.7f*s, floatArrayOf(0.18f, 0.18f, 0.18f, 1f), 45f*side)
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0], 0.05f*s, p[1], 0.16f*s, 0.16f*s, 0.16f*s, floatArrayOf(p[2], p[3], p[4], 1f))
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0], 0.12f*s, p[1], 0.65f*s, 0.01f*s, 0.05f*s, floatArrayOf(0.95f, 0.95f, 0.95f, 0.6f), pSpd*side)
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0], 0.12f*s, p[1], 0.05f*s, 0.01f*s, 0.65f*s, floatArrayOf(0.95f, 0.95f, 0.95f, 0.6f), pSpd*side)
        }
    }
}
