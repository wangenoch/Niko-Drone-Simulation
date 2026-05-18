package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.Matrix
import com.horizon.caadronesimulator.model.Constants
import com.horizon.caadronesimulator.render.util.RenderUtils
import kotlin.math.*

/**
 * [v1.5.9] 場地渲染器 - 像素級 1:1 還原版
 * 融合原本的高亮度質感與現有的 (0,0,0) 導航原點。
 */
object FieldRenderer {
    fun drawField(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, windLevel: Int, windDirection: String, windAngleDeg: Float, flightTime: Float, showObstacles: Boolean, isSunSimEnabled: Boolean = false, sunPosition: Float = 0.5f, useSimplified: Boolean = false,
                  titleTextureId: Int = -1, texH: Int = -1, texCoordH: Int = -1, useTexH: Int = -1) {
        
        // 1. 廣闊地面背景
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, -0.1f, 0f, 800f, 800f, floatArrayOf(0.15f, 0.35f, 0.15f, 1f))
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, -0.05f, 6f, 120f, 100f, floatArrayOf(0.35f, 0.35f, 0.35f, 1f)) // 中心點位於 Z=6
        
        drawWindFlag(posH, colorH, mvpH, mvpMatrix, 0f, windLevel, windDirection, windAngleDeg, flightTime)
        
        // 2. 多樣化障礙物 (從 Constants 讀取實體數據)
        if (showObstacles) {
            val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
            Constants.OBSTACLES.forEach { p ->
                val x = p[0]; val z = p[1]; val h = p[2]
                val shadowXOff = if (isSunSimEnabled) {
                    val angle = Math.toRadians((sunPosition * 180f).toDouble()).toFloat()
                    -cos(angle) * (h * 0.6f)
                } else 0f
                RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, x + shadowXOff, 0.01f, z, 3f + (h * 0.1f), floatArrayOf(0f, 0f, 0f, 0.3f))
                when(p[3].toInt()) {
                    0 -> { RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h/2, z, 5f, h, 5f, floatArrayOf(0.3f, 0.32f, 0.35f, 1f)); RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h, z, 5.2f, 0.5f, 5.2f, floatArrayOf(0.15f, 0.15f, 0.15f, 1f)) }
                    1 -> { RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h/2, z, 4f, h, 4f, floatArrayOf(0.35f, 0.38f, 0.42f, 1f)); RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h, z, 4.2f, 0.3f, 4.2f, floatArrayOf(0.2f, 0.2f, 0.2f, 1f)) }
                    2 -> { RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.25f, z, 0.8f, h*0.5f, 0.8f, floatArrayOf(0.35f, 0.2f, 0.1f, 1f)); RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.6f, z, 3.5f, h*0.4f, 3.5f, floatArrayOf(0.1f, 0.4f, 0.15f, 1f)); RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.85f, z, 2.5f, h*0.3f, 2.5f, floatArrayOf(0.15f, 0.5f, 0.2f, 1f)) }
                    3 -> { RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.3f, z, 0.6f, h*0.6f, 0.6f, floatArrayOf(0.3f, 0.15f, 0.05f, 1f)); RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.75f, z, 4f, 3.5f, 4f, floatArrayOf(0.05f, 0.45f, 0.1f, 1f)) }
                }
            }
            // 帳篷位置復原 (Z=-15 ➔ Z=-9)
            val tentColor = floatArrayOf(0.2f, 0.4f, 0.8f, 1f)
            arrayOf(floatArrayOf(-2f, -9.6f), floatArrayOf(2f, -9.6f), floatArrayOf(-2f, -13.4f), floatArrayOf(2f, -13.4f)).forEach { p ->
                RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0], 1.25f, p[1], 0.1f, 2.5f, 0.1f, floatArrayOf(0.7f, 0.7f, 0.7f, 1f))
            }
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 2.6f, -11.5f, 4.2f, 0.2f, 4f, tentColor)
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 2.8f, -11.5f, 3f, 0.3f, 3f, tentColor)
        }

        // 3. 標線還原 (高亮度 Alpha 0.9, 層次化 LineY)
        val colorWhite = floatArrayOf(1f, 1f, 1f, 0.9f)
        val colorYellow = floatArrayOf(1f, 0.9f, 0f, 0.9f)
        val colorRed = floatArrayOf(1f, 0.1f, 0.1f, 0.7f)
        val lineY = 0.005f

        if (!useSimplified) {
            RenderUtils.drawLine(posH, colorH, mvpH, mvpMatrix, 0f, lineY, -5.5f, 0f, lineY, 17.5f, colorWhite)
            RenderUtils.drawLine(posH, colorH, mvpH, mvpMatrix, -15f, lineY, 6f, 15f, lineY, 6f, colorWhite)
        }
        
        val targetRectColor = if (useSimplified) colorWhite else colorYellow
        val targetCircleColor = if (useSimplified) colorWhite else colorRed

        // 矩形框 (16m/8m) 歸位至 Z=6 中心
        drawRectOutline(posH, colorH, mvpH, mvpMatrix, lineY + 0.002f, 6f, 16f, 16f, targetRectColor)
        drawRectOutline(posH, colorH, mvpH, mvpMatrix, lineY + 0.002f, 6f, 8f, 8f, targetRectColor)
        
        // 8 字圓圈 歸位至 Z=6 中心
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, 6f, lineY + 0.004f, 6f, 8f, targetCircleColor)
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, 6f, lineY + 0.004f, 6f, 4f, targetCircleColor)
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, -6f, lineY + 0.004f, 6f, 8f, targetCircleColor)
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, -6f, lineY + 0.004f, 6f, 4f, targetCircleColor)
        
        // H 坪還原 (維持 0,0)
        RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.006f, 0f, 1.2f, floatArrayOf(1f, 1f, 1f, 1f))
        RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.008f, 0f, 1.0f, floatArrayOf(0f, 0.35f, 0.7f, 1f))
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, -0.2f, lineY + 0.01f, 0f, 0.06f, 0.4f, colorWhite)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0.2f, lineY + 0.01f, 0f, 0.06f, 0.4f, colorWhite)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.01f, 0f, 0.35f, 0.06f, colorWhite)

        // 紅圈復原 (Z=16)
        val solidRed = floatArrayOf(1f, 0f, 0f, 1f)
        RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, -13.5f, lineY + 0.005f, 16.0f, 1.0f, solidRed)
        RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, 13.5f, lineY + 0.005f, 16.0f, 1.0f, solidRed)

        // 站位標線 (Z=-9)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.002f, -9f, 1.8f, 0.1f, colorWhite)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.002f, -9.25f, 0.1f, 0.5f, colorWhite)

        // 標題文字 (位於 H 坪下方 3.0m 處)
        if (titleTextureId != -1) {
            GLES20.glUniform1i(useTexH, 1)
            RenderUtils.drawTexturedRect(posH, texH, texCoordH, mvpH, mvpMatrix, 0f, lineY + 0.01f, -3.0f, 10.0f, 1.5f, titleTextureId)
            GLES20.glUniform1i(useTexH, 0)
        }

        Constants.CONE_POSITIONS.forEach { drawCone(posH, colorH, mvpH, mvpMatrix, it[0], it[1]) }
    }

    private fun drawRectOutline(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, y: Float, offsetZ: Float, w: Float, h: Float, color: FloatArray) {
        val t = 0.12f
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, y, offsetZ + h/2f, w+t, t, color)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, y, offsetZ - h/2f, w+t, t, color)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, -w/2f, y, offsetZ, t, h-t, color)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, w/2f, y, offsetZ, t, h-t, color)
    }

    private fun drawWindFlag(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, windLevel: Int, windDirection: String, windAngleDeg: Float, flightTime: Float) {
        val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
        val z = 19f
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 1.5f, z, 0.1f, 3f, 0.1f, floatArrayOf(0.4f, 0.4f, 0.4f, 1f))
        if (windLevel > 0) {
            val flagM = baseM.copyOf(); Matrix.translateM(flagM, 0, x, 2.8f, z); Matrix.rotateM(flagM, 0, -windAngleDeg, 0f, 1f, 0f)
            for (i in 0 until 4) {
                val offX = i * 0.3f; val dip = (i * i * 0.05f) * (1f - windLevel * 0.15f)
                val wobble = sin(flightTime * 5f + i) * 0.02f * windLevel; val size = 0.3f - i * 0.04f
                val color = if (i % 2 == 0) floatArrayOf(1f, 0.3f, 0f, 1f) else floatArrayOf(1f, 1f, 1f, 1f)
                RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, flagM, offX + 0.15f, -dip + wobble, 0f, 0.3f, size, size, color)
            }
        }
    }

    private fun drawCone(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, z: Float) {
        val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 0.025f, z, 0.4f, 0.05f, 0.4f, floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 0.2f,   z, 0.3f, 0.3f, 0.3f, floatArrayOf(1f, 0.4f, 0f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 0.45f,  z, 0.2f, 0.2f, 0.2f, floatArrayOf(1f, 1f, 1f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 0.7f,   z, 0.1f, 0.2f, 0.1f, floatArrayOf(1f, 0.4f, 0f, 1f))
    }
}
