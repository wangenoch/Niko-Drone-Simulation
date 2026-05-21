package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import com.horizon.caadronesimulator.render.util.RenderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * [v1.4.0] 擴增實境投影組件
 * 負責繪製垂直投影線與地面定位環。
 */
object ArAnchorRenderer {
    private var vertexBuffer: FloatBuffer? = null
    private var circleBuffer: FloatBuffer? = null

    fun drawAnchor(
        mvpMatrix: FloatArray,
        posX: Float, posY: Float, posZ: Float,
        groundY: Float,
        posH: Int,
        colorH: Int,
        mvpH: Int,
        showAnchor: Boolean,
        useTexH: Int = -1 // [v1.7.6] 傳入紋理開關控制
    ) {
        if (!showAnchor || posY - groundY < 0.2f) return

        if (useTexH != -1) GLES20.glUniform1i(useTexH, 0)

        // 1. 繪製垂直投影虛線
        val lineCoords = floatArrayOf(
            posX, posY, posZ,
            posX, groundY, posZ
        )
        val bb = ByteBuffer.allocateDirect(lineCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        val lb = bb.asFloatBuffer()
        lb.put(lineCoords)
        lb.position(0)

        // 關鍵修正：繪製線段前必須更新 MVP 矩陣
        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorH, 1f, 1f, 1f, 0.4f)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, lb)
        GLES20.glEnableVertexAttribArray(posH) // 記得開啟
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)

        // 2. 繪製地面定位環 (使用現成的 RenderUtils)
        // [v1.5.2 修正] 提升高度至 0.02f，解決被 H 坪標線 (0.015f) 遮擋之 Z-Fighting 問題
        val anchorColor = floatArrayOf(0f, 1f, 1f, 0.6f) // 青色
        RenderUtils.drawCircleOutline(posH, colorH, mvpH, mvpMatrix, posX, groundY + 0.02f, posZ, 0.6f, anchorColor)
    }
}
