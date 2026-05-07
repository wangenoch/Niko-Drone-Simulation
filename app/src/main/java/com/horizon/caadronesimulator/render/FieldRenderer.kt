package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.Matrix
import com.horizon.caadronesimulator.model.Constants
import com.horizon.caadronesimulator.render.util.RenderUtils
import kotlin.math.*

object FieldRenderer {
    fun drawField(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, windLevel: Int, windDirection: String, randomDirAngle: Float, flightTime: Float, showObstacles: Boolean, isSunSimEnabled: Boolean = false, sunPosition: Float = 0.5f, useSimplified: Boolean = false,
                  titleTextureId: Int = -1, texH: Int = -1, texCoordH: Int = -1, useTexH: Int = -1) {
        // 恢復廣闊的地面背景
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, -0.1f, 0f, 800f, 800f, floatArrayOf(0.15f, 0.35f, 0.15f, 1f))
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, -0.05f, 0f, 120f, 100f, floatArrayOf(0.35f, 0.35f, 0.35f, 1f))
        drawWindFlag(posH, colorH, mvpH, mvpMatrix, 0f, windLevel, windDirection, randomDirAngle, flightTime)
        
        // 繪製多樣化障礙物
        if (showObstacles) {
            val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
            // 分佈在兩側: [x, z, height, type(0:大建築, 1:中建築, 2:立方樹A, 3:立方樹B)]
            val obstacles = arrayOf(
                floatArrayOf(-32f, 10f, 13f, 0f),  // 左側大建築
                floatArrayOf(-28f, 25f, 8f, 2f),   // 左側立方樹A
                floatArrayOf(32f, 5f, 10f, 1f),    // 右側中建築
                floatArrayOf(28f, -5f, 7f, 3f),    // 右側立方樹B
                floatArrayOf(35f, 20f, 11f, 2f)    // 右遠端立方樹A
            )

            obstacles.forEach { p ->
                val x = p[0]; val z = p[1]; val h = p[2]
                
                // [v1.3.7] 繪製障礙物動態陰影
                val shadowXOff = if (isSunSimEnabled) {
                    val angle = Math.toRadians((sunPosition * 180f).toDouble()).toFloat()
                    -cos(angle) * (h * 0.6f) // 隨建築高度拉長陰影
                } else {
                    if (windDirection.contains("北")) 0f // 舊模式維持簡約
                    else 0f // 這裡可以根據 timeOfDay 做簡單偏移
                }
                
                // 繪製建築物陰影面片
                RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, x + shadowXOff, 0.01f, z, 3f + (h * 0.1f), floatArrayOf(0f, 0f, 0f, 0.3f))

                when(p[3].toInt()) {
                    0 -> { // 大建築
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h/2, z, 5f, h, 5f, floatArrayOf(0.3f, 0.32f, 0.35f, 1f))
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h, z, 5.2f, 0.5f, 5.2f, floatArrayOf(0.15f, 0.15f, 0.15f, 1f))
                    }
                    1 -> { // 中建築
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h/2, z, 4f, h, 4f, floatArrayOf(0.35f, 0.38f, 0.42f, 1f))
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h, z, 4.2f, 0.3f, 4.2f, floatArrayOf(0.2f, 0.2f, 0.2f, 1f))
                    }
                    2 -> { // 立方樹 A (解決破圖)
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.25f, z, 0.8f, h*0.5f, 0.8f, floatArrayOf(0.35f, 0.2f, 0.1f, 1f))
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.6f, z, 3.5f, h*0.4f, 3.5f, floatArrayOf(0.1f, 0.4f, 0.15f, 1f))
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.85f, z, 2.5f, h*0.3f, 2.5f, floatArrayOf(0.15f, 0.5f, 0.2f, 1f))
                    }
                    3 -> { // 立方樹 B
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.3f, z, 0.6f, h*0.6f, 0.6f, floatArrayOf(0.3f, 0.15f, 0.05f, 1f))
                        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, h*0.75f, z, 4f, 3.5f, 4f, floatArrayOf(0.05f, 0.45f, 0.1f, 1f))
                    }
                }
            }
            
            // [v1.3.9] 繪製操作區後方帳篷 (緊貼站位線 Z=-15)
            val tentColor = floatArrayOf(0.2f, 0.4f, 0.8f, 1f) 
            // 四根支柱
            arrayOf(floatArrayOf(-2f, -15.6f), floatArrayOf(2f, -15.6f), floatArrayOf(-2f, -19.4f), floatArrayOf(2f, -19.4f)).forEach { p ->
                RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, p[0], 1.25f, p[1], 0.1f, 2.5f, 0.1f, floatArrayOf(0.7f, 0.7f, 0.7f, 1f))
            }
            // 帳篷頂部
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 2.6f, -17.5f, 4.2f, 0.2f, 4f, tentColor)
            RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, 0f, 2.8f, -17.5f, 3f, 0.3f, 3f, tentColor)
        }
        // 恢復高亮度色彩 (Alpha 0.9)，並優化寬度與高度層次
        val colorWhite = floatArrayOf(1f, 1f, 1f, 0.9f)
        val colorYellow = floatArrayOf(1f, 0.9f, 0f, 0.9f)
        val colorRed = floatArrayOf(1f, 0.1f, 0.1f, 0.7f)
        
        // 使用極低的 Y 軸偏移 (0.005f)，其餘交給 glPolygonOffset 處理
        val lineY = 0.005f
        if (!useSimplified) {
            RenderUtils.drawLine(posH, colorH, mvpH, mvpMatrix, 0f, lineY, -11.5f, 0f, lineY, 11.5f, colorWhite)
            RenderUtils.drawLine(posH, colorH, mvpH, mvpMatrix, -15f, lineY, 0f, 15f, lineY, 0f, colorWhite)
        }
        
        val targetRectColor = if (useSimplified) colorWhite else colorYellow
        val targetCircleColor = if (useSimplified) colorWhite else colorRed

        drawRectOutline(posH, colorH, mvpH, mvpMatrix, lineY + 0.002f, 16f, 16f, targetRectColor)
        drawRectOutline(posH, colorH, mvpH, mvpMatrix, lineY + 0.002f, 8f, 8f, targetRectColor)
        
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, 6f, lineY + 0.004f, 0f, 8f, targetCircleColor)
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, 6f, lineY + 0.004f, 0f, 4f, targetCircleColor)
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, -6f, lineY + 0.004f, 0f, 8f, targetCircleColor)
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, -6f, lineY + 0.004f, 0f, 4f, targetCircleColor)
        
        RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.006f, -6.0f, 1.2f, floatArrayOf(1f, 1f, 1f, 1f))
        RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.008f, -6.0f, 1.0f, floatArrayOf(0f, 0.35f, 0.7f, 1f))
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, -0.2f, lineY + 0.01f, -6.0f, 0.06f, 0.4f, colorWhite)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0.2f, lineY + 0.01f, -6.0f, 0.06f, 0.4f, colorWhite)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.01f, -6.0f, 0.35f, 0.06f, colorWhite)

        // 新增左右兩個紅圈 (直徑 2m, 半徑 1m)
        val solidRed = floatArrayOf(1f, 0f, 0f, 1f)
        RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, -13.5f, lineY + 0.005f, 10.0f, 1.0f, solidRed)
        RenderUtils.drawFilledCircle(posH, colorH, mvpH, mvpMatrix, 13.5f, lineY + 0.005f, 10.0f, 1.0f, solidRed)

        // [v1.3.9] CAA 考場操作員站位標線 (倒 T 型)
        val markerColor = if (useSimplified) colorWhite else colorWhite 
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.002f, -15f, 1.8f, 0.1f, markerColor) 
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, lineY + 0.002f, -15.25f, 0.1f, 0.5f, markerColor)

        // [v1.4.5] 修正標題 Z 軸至 -9.0
        // X=0, Y=0.015(地板), Z=-9.0, W=10.0, D=1.5
        if (titleTextureId != -1) {
            GLES20.glUniform1i(useTexH, 1)
            RenderUtils.drawTexturedRect(posH, texH, texCoordH, mvpH, mvpMatrix, 0f, lineY + 0.01f, -9.0f, 10.0f, 1.5f, titleTextureId)
            GLES20.glUniform1i(useTexH, 0)
        }

        Constants.CONE_POSITIONS.forEach { drawCone(posH, colorH, mvpH, mvpMatrix, it[0], it[1]) }
    }

    private fun drawRectOutline(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, y: Float, w: Float, h: Float, color: FloatArray) {
        val t = 0.12f
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, y, h/2f, w+t, t, color)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, 0f, y, -h/2f, w+t, t, color)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, -w/2f, y, 0f, t, h-t, color)
        RenderUtils.drawRect(posH, colorH, mvpH, mvpMatrix, w/2f, y, 0f, t, h-t, color)
    }

    private fun drawWindFlag(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, windLevel: Int, windDirection: String, windAngleDeg: Float, flightTime: Float) {
        val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
        val z = 13f
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 1.5f, z, 0.1f, 3f, 0.1f, floatArrayOf(0.4f, 0.4f, 0.4f, 1f))
        if (windLevel > 0) {
            // 直接使用傳入的角度，確保與物理受力 100% 同步
            val flagM = baseM.copyOf(); Matrix.translateM(flagM, 0, x, 2.8f, z); Matrix.rotateM(flagM, 0, windAngleDeg, 0f, 1f, 0f)
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
        // 修正圓錐高度與結構，總高度達 0.8m 符合物理判定
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 0.025f, z, 0.4f, 0.05f, 0.4f, floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 0.2f,   z, 0.3f, 0.3f, 0.3f, floatArrayOf(1f, 0.4f, 0f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 0.45f,  z, 0.2f, 0.2f, 0.2f, floatArrayOf(1f, 1f, 1f, 1f))
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 0.7f,   z, 0.1f, 0.2f, 0.1f, floatArrayOf(1f, 0.4f, 0f, 1f))
    }
}
