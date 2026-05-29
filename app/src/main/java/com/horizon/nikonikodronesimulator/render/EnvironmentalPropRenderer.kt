package com.horizon.nikonikodronesimulator.render

import android.opengl.Matrix
import com.horizon.nikonikodronesimulator.render.util.RenderUtils
import kotlin.math.sin

/**
 * [v1.7.15] 環境道具渲染器 (Environmental Prop Renderer)
 * 職責：負責場地中受環境（如風力、時間）影嚮的動態實體物件。
 */
object EnvironmentalPropRenderer {

    /**
     * 繪製風向旗：包含桿子與隨風擺動的四段旗面
     */
    fun drawWindFlag(
        posH: Int, 
        colorH: Int, 
        mvpH: Int, 
        mvpMatrix: FloatArray, 
        x: Float, 
        windLevel: Int, 
        flightTime: Float
    ) {
        val baseM = FloatArray(16); Matrix.setIdentityM(baseM, 0)
        val z = 19f
        // 1. 繪製旗桿
        RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, baseM, x, 1.5f, z, 0.1f, 3f, 0.1f, floatArrayOf(0.4f, 0.4f, 0.4f, 1f))
        
        // 2. 繪製動態旗面
        if (windLevel > 0) {
            val flagM = baseM.copyOf(); Matrix.translateM(flagM, 0, x, 2.8f, z)
            // [v1.7.9.11 視覺統一人員]
            // 核心憲法：旗子必須無條件順著物理流向飄動。
            // 使用 270 - flow 進行投影對位 (OpenGL 座標系修正)，確保旗尖指向與 currentFlowAngle 物理一致。
            val currentFlowAngle = com.horizon.nikonikodronesimulator.model.DroneState.getInstance().env.currentWindAngle
            Matrix.rotateM(flagM, 0, 270f - currentFlowAngle, 0f, 1f, 0f)
            
            for (i in 0 until 4) {
                val offX = i * 0.3f; val dip = (i * i * 0.05f) * (1f - windLevel * 0.15f)
                val wobble = sin(flightTime * 5f + i) * 0.02f * windLevel; val size = 0.3f - i * 0.04f
                val color = if (i % 2 == 0) floatArrayOf(1f, 0.3f, 0f, 1f) else floatArrayOf(1f, 1f, 1f, 1f)
                RenderUtils.drawBox(posH, colorH, mvpH, mvpMatrix, flagM, offX + 0.15f, -dip + wobble, 0f, 0.3f, size, size, color)
            }
        }
    }
}
