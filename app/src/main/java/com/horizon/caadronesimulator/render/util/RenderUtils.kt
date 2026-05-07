package com.horizon.caadronesimulator.render.util

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * 渲染工具類 (已優化內存分配)
 */
object RenderUtils {
    // 預分配緩衝區以避免 GC 壓力
    private val boxBuffer = createBuffer(3 * 36)
    private val rectBuffer = createBuffer(3 * 4)
    private val texBuffer = createBuffer(2 * 4) // 材質座標
    private val lineBuffer = createBuffer(3 * 4)
    private val circleBuffer = createBuffer((120 + 2) * 3)
    private val batchLineBuffer = createBuffer(120 * 6 * 3) // 120段 * 2個三角形 * 3個頂點 * 3個坐標

    private fun createBuffer(capacity: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    fun drawBox(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, parentM: FloatArray, tx: Float, ty: Float, tz: Float, w: Float, h: Float, d: Float, color: FloatArray, ry: Float = 0f) {
        val m = parentM.copyOf(); Matrix.translateM(m, 0, tx, ty, tz); if (ry != 0f) Matrix.rotateM(m, 0, ry, 0f, 1f, 0f)
        val resM = FloatArray(16); Matrix.multiplyMM(resM, 0, mvpMatrix, 0, m, 0); GLES20.glUniformMatrix4fv(mvpH, 1, false, resM, 0)
        
        val tC = floatArrayOf(min(1f, color[0]*1.2f), min(1f, color[1]*1.2f), min(1f, color[2]*1.2f), color[3])
        val sC = floatArrayOf(color[0]*0.7f, color[1]*0.7f, color[2]*0.7f, color[3])

        // 1. 繪製頂面 (Top)
        boxBuffer.clear()
        boxBuffer.put(floatArrayOf(-w/2,h/2,d/2, w/2,h/2,d/2, -w/2,h/2,-d/2, w/2,h/2,-d/2))
        drawBatch(posH, colorH, tC, 4)

        // 2. 批次繪製前後面 (Front & Back) - 4+4=8個頂點
        boxBuffer.clear()
        // Front
        boxBuffer.put(floatArrayOf(-w/2,-h/2,d/2, w/2,-h/2,d/2, -w/2,h/2,d/2, w/2,h/2,d/2))
        // Back (為了使用同一個 DrawArrays，我們需要點分開或用 TRIANGLES)
        // 這裡改用 GL_TRIANGLES 繪製以支援不連續的面
        boxBuffer.clear()
        // Top (2 triangles)
        putRectTriangles(boxBuffer, -w/2,h/2,d/2, w/2,h/2,d/2, -w/2,h/2,-d/2, w/2,h/2,-d/2)
        drawBatch(posH, colorH, tC, 6)

        // 前後 (4 triangles)
        boxBuffer.clear()
        putRectTriangles(boxBuffer, -w/2,-h/2,d/2, w/2,-h/2,d/2, -w/2,h/2,d/2, w/2,h/2,d/2) // Front
        putRectTriangles(boxBuffer, w/2,-h/2,-d/2, -w/2,-h/2,-d/2, w/2,h/2,-d/2, -w/2,h/2,-d/2) // Back
        drawBatch(posH, colorH, color, 12)

        // 左右底 (6 triangles)
        boxBuffer.clear()
        putRectTriangles(boxBuffer, w/2,-h/2,d/2, w/2,-h/2,-d/2, w/2,h/2,d/2, w/2,h/2,-d/2) // Right
        putRectTriangles(boxBuffer, -w/2,-h/2,-d/2, -w/2,-h/2,d/2, -w/2,h/2,-d/2, -w/2,h/2,d/2) // Left
        putRectTriangles(boxBuffer, -w/2,-h/2,-d/2, w/2,-h/2,-d/2, -w/2,-h/2,d/2, w/2,-h/2,d/2) // Bottom
        drawBatch(posH, colorH, sC, 18)
    }

    private fun putRectTriangles(buf: FloatBuffer, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float) {
        // Triangle 1: 1-2-3
        buf.put(x1).put(y1).put(z1).put(x2).put(y2).put(z2).put(x3).put(y3).put(z3)
        // Triangle 2: 3-2-4
        buf.put(x3).put(y3).put(z3).put(x2).put(y2).put(z2).put(x4).put(y4).put(z4)
    }

    private fun drawBatch(posH: Int, colorH: Int, color: FloatArray, count: Int) {
        boxBuffer.position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, boxBuffer)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glUniform4fv(colorH, 1, color, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
    }

    private fun drawDirect(posH: Int, colorH: Int, verts: FloatArray, color: FloatArray) {
        boxBuffer.clear(); boxBuffer.put(verts).position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, boxBuffer)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glUniform4fv(colorH, 1, color, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun drawRect(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, w: Float, h: Float, color: FloatArray) {
        val v = floatArrayOf(x-w/2, y, z+h/2, x+w/2, y, z+h/2, x-w/2, y, z-h/2, x+w/2, y, z-h/2)
        rectBuffer.clear(); rectBuffer.put(v).position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, rectBuffer)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glUniform4fv(colorH, 1, color, 0)
        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /**
     * [v1.4.2] 繪製地板貼圖 (用於單位標題)
     */
    fun drawTexturedRect(posH: Int, texH: Int, texCoordH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, w: Float, d: Float, textureId: Int) {
        val v = floatArrayOf(x-w/2, y, z+d/2, x+w/2, y, z+d/2, x-w/2, y, z-d/2, x+w/2, y, z-d/2)
        rectBuffer.clear(); rectBuffer.put(v).position(0)

        // 還原為之前的座標狀態，停止擅自修正
        val uv = floatArrayOf(1f, 0f, 0f, 0f, 1f, 1f, 0f, 1f)
        texBuffer.clear(); texBuffer.put(uv).position(0)

        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, rectBuffer)
        GLES20.glEnableVertexAttribArray(posH)

        GLES20.glVertexAttribPointer(texCoordH, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glEnableVertexAttribArray(texCoordH)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(texH, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        GLES20.glDisableVertexAttribArray(texCoordH)
    }

    fun drawLine(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, color: FloatArray) {
        val t = 0.18f; val dx = x2-x1; val dz = z2-z1; val len = sqrt(dx*dx+dz*dz)
        if (len == 0f) return
        val ux = -dz/len*(t/2f); val uz = dx/len*(t/2f)
        val v = floatArrayOf(x1+ux, y1, z1+uz, x1-ux, y1, z1-uz, x2+ux, y2, z2+uz, x2-ux, y2, z2-uz)
        lineBuffer.clear(); lineBuffer.put(v).position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, lineBuffer)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glUniform4fv(colorH, 1, color, 0)
        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun drawFilledCircle(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, r: Float, color: FloatArray) {
        val pts = 64; val v = FloatArray((pts+2)*3); v[0]=x; v[1]=y; v[2]=z
        for (i in 0..pts) { 
            val a = (i.toFloat()/pts.toFloat())*2f*PI.toFloat()
            v[(i+1)*3]=x+cos(a)*r; v[(i+1)*3+1]=y; v[(i+1)*3+2]=z+sin(a)*r
        }
        circleBuffer.clear(); circleBuffer.put(v).position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, circleBuffer)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glUniform4fv(colorH, 1, color, 0)
        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, pts+2)
    }

    fun drawCircleOutline(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, r: Float, color: FloatArray) {
        val pts = 120; val otherX = if (x < 0) 6f else -6f
        val thickness = 0.18f
        batchLineBuffer.clear()
        var vertexCount = 0
        
        for (i in 0 until pts) {
            val a1 = (i.toFloat() / pts) * 2f * PI.toFloat(); val a2 = ((i + 1).toFloat() / pts) * 2f * PI.toFloat()
            val x1 = x + cos(a1) * r; val z1 = z + sin(a1) * r; val x2 = x + cos(a2) * r; val z2 = z + sin(a2) * r
            val midX = (x1 + x2) / 2f; val midZ = (z1 + z2) / 2f; val distToOtherSq = (midX - otherX).pow(2) + midZ.pow(2)
            
            if (distToOtherSq < r * r) continue
            
            // 計算線段方向與法向量以生成寬度
            val dx = x2 - x1; val dz = z2 - z1
            val len = sqrt(dx * dx + dz * dz)
            if (len == 0f) continue
            val ux = -dz / len * (thickness / 2f)
            val uz = dx / len * (thickness / 2f)
            
            // 三角形 1: (p1+u), (p1-u), (p2+u)
            batchLineBuffer.put(x1 + ux).put(y).put(z1 + uz)
            batchLineBuffer.put(x1 - ux).put(y).put(z1 - uz)
            batchLineBuffer.put(x2 + ux).put(y).put(z2 + uz)
            
            // 三角形 2: (p2+u), (p1-u), (p2-u)
            batchLineBuffer.put(x2 + ux).put(y).put(z2 + uz)
            batchLineBuffer.put(x1 - ux).put(y).put(z1 - uz)
            batchLineBuffer.put(x2 - ux).put(y).put(z2 - uz)
            
            vertexCount += 6
        }
        
        if (vertexCount > 0) {
            batchLineBuffer.position(0)
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, batchLineBuffer)
            GLES20.glEnableVertexAttribArray(posH)
            GLES20.glUniform4fv(colorH, 1, color, 0)
            GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }
    }
}
