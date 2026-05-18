package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * [v1.5.9] 太陽與耀光渲染器 (Performance Optimized)
 * 修正：加入智慧遮擋判定，當太陽不在視野內或被遮擋時停止耀光矩陣計算。
 */
class SunRenderer {
    private var program = 0
    private var posH = 0
    private var colorH = 0
    private var mvpH = 0
    private val vertexBuffer: FloatBuffer

    private val currentSunWorldPos = FloatArray(4)
    private var isSunVisibleInFrame = false // [優化] 視錐體可見性標記

    init {
        val vertices = floatArrayOf(-1f, 1f, 0f, -1f, -1f, 0f, 1f, 1f, 0f, 1f, -1f, 0f)
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertices).also { it.position(0) }
    }

    fun init() {
        val vs = "attribute vec4 vPosition; uniform mat4 uMVPMatrix; varying vec2 vCoord; void main() { gl_Position = uMVPMatrix * vPosition; vCoord = vPosition.xy; }".trimIndent()
        val fs = "precision mediump float; uniform vec4 vColor; varying vec2 vCoord; void main() { float dist = length(vCoord); float alpha = 1.0 - smoothstep(0.5, 1.0, dist); if (dist > 1.0) discard; gl_FragColor = vec4(vColor.rgb, vColor.a * alpha); }".trimIndent()
        val vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also { GLES20.glShaderSource(it, vs); GLES20.glCompileShader(it) }
        val fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also { GLES20.glShaderSource(it, fs); GLES20.glCompileShader(it) }
        program = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, vShader); GLES20.glAttachShader(it, fShader); GLES20.glLinkProgram(it) }
        posH = GLES20.glGetAttribLocation(program, "vPosition"); colorH = GLES20.glGetUniformLocation(program, "vColor"); mvpH = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    fun drawSun(pMatrix: FloatArray, vMatrix: FloatArray, sunPos: Float) {
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(posH); GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val staticV = vMatrix.copyOf(); staticV[12] = 0f; staticV[13] = 0f; staticV[14] = 0f
        val angle = Math.toRadians((sunPos * 180f).toDouble()).toFloat()
        val mMatrix = FloatArray(16); Matrix.setIdentityM(mMatrix, 0)
        
        Matrix.rotateM(mMatrix, 0, -(sunPos * 180f - 90f), 0f, 1f, 0f) 
        Matrix.translateM(mMatrix, 0, 0f, 3000f * sin(angle), 4800f) 

        val origin = floatArrayOf(0f, 0f, 0f, 1f)
        Matrix.multiplyMV(currentSunWorldPos, 0, mMatrix, 0, origin, 0)

        val mvMatrix = FloatArray(16); Matrix.multiplyMM(mvMatrix, 0, staticV, 0, mMatrix, 0)
        mvMatrix[0] = 1f; mvMatrix[1] = 0f; mvMatrix[2] = 0f
        mvMatrix[4] = 0f; mvMatrix[5] = 1f; mvMatrix[6] = 0f
        mvMatrix[8] = 0f; mvMatrix[9] = 0f; mvMatrix[10] = 1f
        
        val sunScale = 300f + (1f - sin(angle)) * 50f
        Matrix.scaleM(mvMatrix, 0, sunScale, sunScale, 1f)

        val mvp = FloatArray(16); Matrix.multiplyMM(mvp, 0, pMatrix, 0, mvMatrix, 0)
        
        // [優化] 視錐體快速裁剪判定
        val sunNDC = FloatArray(4)
        Matrix.multiplyMV(sunNDC, 0, mvp, 0, origin, 0)
        isSunVisibleInFrame = sunNDC[3] > 0 && abs(sunNDC[0]/sunNDC[3]) < 1.2f && abs(sunNDC[1]/sunNDC[3]) < 1.2f

        if (!isSunVisibleInFrame) {
            GLES20.glDisableVertexAttribArray(posH)
            return
        }

        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvp, 0)
        val g = (0.8f + sin(angle) * 0.2f).coerceIn(0.5f, 1.0f); val b = (0.5f + sin(angle) * 0.5f).coerceIn(0.3f, 1.0f)
        GLES20.glUniform4f(colorH, 1.0f, g, b, 1.0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posH)
    }

    fun drawLensFlare(pMatrix: FloatArray, vMatrix: FloatArray, sunPos: Float) {
        // [優化] 如果太陽不在螢幕內，直接跳過昂貴的耀光鏈計算
        if (!isSunVisibleInFrame || sunPos < 0.05f || sunPos > 0.95f) return

        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(posH); GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        
        val staticV = vMatrix.copyOf(); staticV[12]=0f; staticV[13]=0f; staticV[14]=0f
        val mvp = FloatArray(16); Matrix.multiplyMM(mvp, 0, pMatrix, 0, staticV, 0)
        
        val sunScreenPos = FloatArray(4)
        Matrix.multiplyMV(sunScreenPos, 0, mvp, 0, currentSunWorldPos, 0)
        
        if (sunScreenPos[3] > 0) {
            val ndcX = sunScreenPos[0] / sunScreenPos[3]; val ndcY = sunScreenPos[1] / sunScreenPos[3]
            drawFlareElement(ndcX, ndcY, 0.5f, floatArrayOf(1f, 1f, 0.9f, 0.12f))
            listOf(0.5f to 0.12f, 0.2f to 0.08f, -0.4f to 0.1f, -0.7f to 0.15f).forEach { (dist, scale) -> 
                drawFlareElement(ndcX * dist, ndcY * dist, scale, floatArrayOf(0.7f, 0.8f, 1f, 0.06f))
            }
        }
        GLES20.glDisableVertexAttribArray(posH); GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawFlareElement(ndcX: Float, ndcY: Float, scale: Float, color: FloatArray) {
        val m = FloatArray(16); Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, ndcX, ndcY, 0f); Matrix.scaleM(m, 0, scale, scale * 1.5f, 1f)
        GLES20.glUniformMatrix4fv(mvpH, 1, false, m, 0); GLES20.glUniform4f(colorH, color[0], color[1], color[2], color[3]); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
}
