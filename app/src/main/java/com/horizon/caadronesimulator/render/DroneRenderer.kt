package com.horizon.caadronesimulator.render

import android.opengl.Matrix
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.render.util.RenderUtils

object DroneRenderer {
    fun drawDroneShadow(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, droneType: String, curX: Float, curY: Float, curZ: Float, timeOfDay: String, showShadow: Boolean, shadowIntensity: Float) {
        if (!showShadow) return
        val spec = DroneRegistry.getSpec(droneType)
        val relativeAlt = (curY - spec.groundOffset).coerceAtLeast(0f)
        val shadowX = curX + (if (timeOfDay == "早晨") relativeAlt * 0.8f else if (timeOfDay == "下午") -relativeAlt * 0.8f else 0f)
        
        // 1. 總透明度：隨相對高度衰減，並套用使用者設定的強度
        val totalAlpha = (shadowIntensity / (1f + relativeAlt * 0.5f)).coerceIn(0f, shadowIntensity)
        if (totalAlpha < 0.001f) return

        // 2. 模糊係數：隨相對高度增加
        val blurFactor = (relativeAlt * 0.15f).coerceIn(0f, 1.2f)
        
        // 3. 多層疊加模擬漸層 (5 層)
        // 使用從內向外疊加的方式，透過多層半透明圓形模擬柔邊 (Penumbra)
        for (i in 5 downTo 1) {
            val ratio = i / 5f // 1.0 (內層) -> 0.2 (外層)
            
            // 半徑：越外層擴散越大
            val layerRadius = spec.shadowSizeBase * (1f + blurFactor * (2.0f - ratio))
            
            // 透明度：使用平方分配，讓中心較深，邊緣極淡
            // 提高基準分配比例 (從 /3f 改為 /1.8f) 以強化最大深度的視覺效果
            val layerAlpha = (totalAlpha / 1.8f) * (ratio * ratio)
            
            if (layerAlpha > 0.005f) {
                RenderUtils.drawFilledCircle(
                    posH, colorH, mvpH, mvpMatrix, 
                    shadowX, 
                    0.04f + i * 0.001f, // 微調 Y 軸避免 Z-fighting
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
        
        // 1. 機身 (Fuselages)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.35f*s, 0.4f*s, 0.25f*s, 0.45f*s, 0.6f*s, floatArrayOf(0.1f, 0.1f, 0.1f, 1f)) // 前艙
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.55f*s, 0.6f*s, 0.2f*s, 0.2f*s, 0.3f*s, floatArrayOf(0f, 0.4f, 0.8f, 0.8f))   // 擋風玻璃
        
        // 2. 尾樑 (Tail Boom)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.4f*s, -0.6f*s, 0.12f*s, 0.12f*s, 1.2f*s, floatArrayOf(0.15f, 0.15f, 0.15f, 1f))
        
        // 3. 主旋翼 (Main Rotor)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.65f*s, 0.2f*s, 0.04f*s, 0.25f*s, 0.04f*s, floatArrayOf(0.2f, 0.2f, 0.2f, 1f)) // 主軸
        val mainPropSpd = if(!isMotorLocked) time*2200f else 0f
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.78f*s, 0.2f*s, 2.5f*s, 0.015f*s, 0.08f*s, floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), mainPropSpd)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.78f*s, 0.2f*s, 0.08f*s, 0.015f*s, 2.5f*s, floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), mainPropSpd)

        // 4. 尾旋翼 (Tail Rotor)
        val tailPropSpd = if(!isMotorLocked) time*4000f else 0f
        Matrix.rotateM(baseM, 0, 90f, 0f, 0f, 1f) // 旋轉坐標系以繪製垂直旋翼
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0.4f*s, -0.1f*s, -1.1f*s, 0.6f*s, 0.01f*s, 0.04f*s, floatArrayOf(0.9f, 0.9f, 0.9f, 0.6f), tailPropSpd)
        Matrix.rotateM(baseM, 0, -90f, 0f, 0f, 1f) // 還原

        // 5. 起落架 (Skids)
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
        
        // 1. 底盤尾部中心燈 (綠色)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.1f, -0.3f*s, 0.05f*s, 0.05f*s, 0.05f*s, floatArrayOf(0f, 1f, 0f, 1f))
        
        // 2. 雙向天線 (位於第二層黑色模組後端)
        arrayOf(-0.12f*s, 0.12f*s).forEach { ax ->
            val antM = baseM.copyOf(); Matrix.translateM(antM, 0, ax, 0.2f, -0.18f*s); Matrix.rotateM(antM, 0, -15f, 1f, 0f, 0f) // 向後傾斜
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, antM, 0f, 0.15f*s, 0f, 0.012f*s, 0.3f*s, 0.012f*s, floatArrayOf(0.15f, 0.15f, 0.15f, 1f))
        }

        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.25f, 0f, 0.25f*s, 0.12f*s, 0.25f*s, floatArrayOf(0f, 0.3f, 0.8f, 1f))
        val armLen = 0.8f * s
        arrayOf(floatArrayOf(1f,1f), floatArrayOf(-1f,1f), floatArrayOf(1f,-1f), floatArrayOf(-1f,-1f)).forEach { p ->
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]*armLen/2*0.707f, 0.15f, p[1]*armLen/2*0.707f, armLen, 0.04f*s, 0.04f*s, floatArrayOf(0.05f, 0.05f, 0.05f, 1f), -45f*(p[0]*p[1]))
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]*armLen*0.707f, 0.15f, p[1]*armLen*0.707f, 0.12f*s, 0.08f*s, 0.12f*s, floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
            
            // 3. 馬達座底部高辨識度燈號 (前方綠色, 後方紅色)
            val ledColor = if(p[1] > 0) floatArrayOf(0f, 1f, 0f, 1f) else floatArrayOf(1f, 0f, 0f, 1f)
            // 將燈號從馬達頂部移至底座下方 (y: 0.11f)，並顯著放大尺寸 (0.14f) 以利地面觀察
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]*armLen*0.707f, 0.11f, p[1]*armLen*0.707f, 0.14f*s, 0.03f*s, 0.14f*s, ledColor)

            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0]*armLen*0.707f, 0.2f, p[1]*armLen*0.707f, 0.7f*s, 0.01f, 0.04f*s, floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), if(!isMotorLocked) time*2500f*(p[0]*p[1]) else 0f)
        }
        val legX = 0.25f*s; val legZ = 0.2f*s
        arrayOf(floatArrayOf(legX, legZ), floatArrayOf(-legX, legZ), floatArrayOf(legX, -legZ), floatArrayOf(-legX, -legZ)).forEach { l -> RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, l[0], -0.2f*s, l[1], 0.03f, 0.5f*s, 0.03f, floatArrayOf(0.1f, 0.1f, 0.1f, 1f)) }
        arrayOf(legX, -legX).forEach { lx ->
            val skidColor = if(lx > 0) floatArrayOf(1f, 0f, 0f, 1f) else floatArrayOf(0f, 1f, 0f, 1f)
            // [v1.2.86] 取消水平橫桿消光黑，改為與端點一致的導航識別色
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, lx, -0.45f*s, 0f, 0.04f*s, 0.04f*s, 0.9f*s, skidColor)
            // 端點裝飾件維持同色，但因尺寸略大 (0.05s) 仍保有結構層次感
            arrayOf(0.5f*s, -0.5f*s).forEach { tz -> RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, lx, -0.45f*s, tz, 0.05f*s, 0.05f*s, 0.15f*s, skidColor) }
        }
    }

    private fun drawDrone(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float, time: Float, isMotorLocked: Boolean) {
        val s = DroneRegistry.getSpec("QUAD_STANDARD").scale; val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
        Matrix.translateM(baseM, 0, x, y, z); Matrix.rotateM(baseM, 0, yaw, 0f, 1f, 0f); Matrix.rotateM(baseM, 0, pitch, 1f, 0f, 0f); Matrix.rotateM(baseM, 0, -roll, 0f, 0f, 1f)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0f, 0f, 0.4f*s, 0.15f*s, 0.8f*s, floatArrayOf(0.12f, 0.12f, 0.12f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 0.05f*s, 0.4f*s, 0.25f*s, 0.06f*s, 0.12f*s, floatArrayOf(1f, 0.4f, 0f, 1f))
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
