package com.horizon.caadronesimulator.render.util

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object RenderUtils {
    fun drawBox(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, parentM: FloatArray, tx: Float, ty: Float, tz: Float, w: Float, h: Float, d: Float, color: FloatArray, ry: Float = 0f) {
        val m = parentM.copyOf(); Matrix.translateM(m, 0, tx, ty, tz); if (ry != 0f) Matrix.rotateM(m, 0, ry, 0f, 1f, 0f)
        val resM = FloatArray(16); Matrix.multiplyMM(resM, 0, mvpMatrix, 0, m, 0); GLES20.glUniformMatrix4fv(mvpH, 1, false, resM, 0)
        fun drawF(verts: FloatArray, c: FloatArray) {
            val b = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(verts).also { it.position(0) }
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, b); GLES20.glEnableVertexAttribArray(posH)
            GLES20.glUniform4fv(colorH, 1, c, 0); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        val tC = floatArrayOf(min(1f, color[0]*1.2f), min(1f, color[1]*1.2f), min(1f, color[2]*1.2f), color[3])
        val sC = floatArrayOf(color[0]*0.7f, color[1]*0.7f, color[2]*0.7f, color[3])
        drawF(floatArrayOf(-w/2,h/2,d/2, w/2,h/2,d/2, -w/2,h/2,-d/2, w/2,h/2,-d/2), tC)
        drawF(floatArrayOf(-w/2,-h/2,d/2, w/2,-h/2,d/2, -w/2,h/2,d/2, w/2,h/2,d/2), color)
        drawF(floatArrayOf(w/2,-h/2,d/2, w/2,-h/2,-d/2, w/2,h/2,d/2, w/2,h/2,-d/2), sC)
        drawF(floatArrayOf(-w/2,-h/2,-d/2, -w/2,-h/2,d/2, -w/2,h/2,-d/2, -w/2,h/2,d/2), sC)
        drawF(floatArrayOf(w/2,-h/2,-d/2, -w/2,-h/2,-d/2, w/2,h/2,-d/2, -w/2,h/2,-d/2), color)
        drawF(floatArrayOf(-w/2,-h/2,-d/2, w/2,-h/2,-d/2, -w/2,-h/2,d/2, w/2,-h/2,d/2), sC)
    }

    fun drawRect(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, w: Float, h: Float, color: FloatArray) {
        val v = floatArrayOf(x-w/2, y, z+h/2, x+w/2, y, z+h/2, x-w/2, y, z-h/2, x+w/2, y, z-h/2)
        val b = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(v).also { it.position(0) }
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, b); GLES20.glEnableVertexAttribArray(posH); GLES20.glUniform4fv(colorH, 1, color, 0); GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun drawLine(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, color: FloatArray) {
        // 使用平衡的線寬 (0.18m)，既能保證遠處可見度，又能減少閃爍
        val t = 0.18f; val dx = x2-x1; val dz = z2-z1; val len = sqrt(dx*dx+dz*dz)
        if (len == 0f) return
        val ux = -dz/len*(t/2f); val uz = dx/len*(t/2f)
        val v = floatArrayOf(x1+ux, y1, z1+uz, x1-ux, y1, z1-uz, x2+ux, y2, z2+uz, x2-ux, y2, z2-uz)
        val b = ByteBuffer.allocateDirect(v.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(v).also { it.position(0) }
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, b); GLES20.glEnableVertexAttribArray(posH); GLES20.glUniform4fv(colorH, 1, color, 0); GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun drawFilledCircle(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, r: Float, color: FloatArray) {
        val pts = 64; val v = FloatArray((pts+2)*3); v[0]=x; v[1]=y; v[2]=z
        for (i in 0..pts) { val a = (i.toFloat()/pts.toFloat())*2f*PI.toFloat(); v[(i+1)*3]=x+cos(a)*r; v[(i+1)*3+1]=y; v[(i+1)*3+2]=z+sin(a)*r }
        val b = ByteBuffer.allocateDirect(v.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(v).also { it.position(0) }
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, b); GLES20.glEnableVertexAttribArray(posH); GLES20.glUniform4fv(colorH, 1, color, 0); GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, pts+2)
    }

    fun drawCircleOutline(posH: Int, colorH: Int, mvpH: Int, mvpMatrix: FloatArray, x: Float, y: Float, z: Float, r: Float, color: FloatArray) {
        val pts = 120; val otherX = if (x < 0) 6f else -6f
        for (i in 0 until pts) {
            val a1 = (i.toFloat() / pts) * 2f * PI.toFloat(); val a2 = ((i + 1).toFloat() / pts) * 2f * PI.toFloat()
            val x1 = x + cos(a1) * r; val z1 = z + sin(a1) * r; val x2 = x + cos(a2) * r; val z2 = z + sin(a2) * r
            val midX = (x1 + x2) / 2f; val midZ = (z1 + z2) / 2f; val distToOtherSq = (midX - otherX).pow(2) + midZ.pow(2)
            if (distToOtherSq < r * r) continue
            drawLine(posH, colorH, mvpH, mvpMatrix, x1, y, z1, x2, y, z2, color)
        }
    }
}
