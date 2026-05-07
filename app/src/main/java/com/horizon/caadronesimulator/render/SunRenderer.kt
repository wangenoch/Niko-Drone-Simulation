package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * [v1.3.7] 太陽與耀光渲染器 - 安全防護版
 */
class SunRenderer {
    private var program = 0
    private var posH = 0
    private var colorH = 0
    private var mvpH = 0

    private val vertexBuffer: FloatBuffer

    init {
        val vertices = floatArrayOf(
            -1f,  1f, 0f,
            -1f, -1f, 0f,
             1f,  1f, 0f,
             1f, -1f, 0f
        )
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(vertices)
            position(0)
        }
    }

    fun init() {
        val vs = """
            attribute vec4 vPosition;
            uniform mat4 uMVPMatrix;
            varying vec2 vCoord;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                vCoord = vPosition.xy;
            }
        """.trimIndent()

        val fs = """
            precision mediump float;
            uniform vec4 vColor;
            varying vec2 vCoord;
            void main() {
                float dist = length(vCoord);
                float alpha = 1.0 - smoothstep(0.5, 1.0, dist);
                if (dist > 1.0) discard;
                gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
            }
        """.trimIndent()
        
        val vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also { GLES20.glShaderSource(it, vs); GLES20.glCompileShader(it) }
        val fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also { GLES20.glShaderSource(it, fs); GLES20.glCompileShader(it) }
        program = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, vShader); GLES20.glAttachShader(it, fShader); GLES20.glLinkProgram(it) }
        
        posH = GLES20.glGetAttribLocation(program, "vPosition")
        colorH = GLES20.glGetUniformLocation(program, "vColor")
        mvpH = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    fun drawSun(pMatrix: FloatArray, vMatrix: FloatArray, sunPos: Float) {
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        val mvp = FloatArray(16)
        val mMatrix = FloatArray(16)
        Matrix.setIdentityM(mMatrix, 0)

        val angle = Math.toRadians((sunPos * 180f).toDouble()).toFloat()
        val sX = cos(angle) * 150f
        val sY = sin(angle) * 100f
        val sZ = 200f 

        Matrix.translateM(mMatrix, 0, sX, sY, sZ)
        
        val mvMatrix = FloatArray(16)
        Matrix.multiplyMM(mvMatrix, 0, vMatrix, 0, mMatrix, 0)
        
        // 看板效果：重置旋轉
        mvMatrix[0] = 1f; mvMatrix[1] = 0f; mvMatrix[2] = 0f
        mvMatrix[4] = 0f; mvMatrix[5] = 1f; mvMatrix[6] = 0f
        mvMatrix[8] = 0f; mvMatrix[9] = 0f; mvMatrix[10] = 1f
        
        val size = 15f + (1f - sin(angle)) * 8f
        Matrix.scaleM(mvMatrix, 0, size, size, 1f)

        Matrix.multiplyMM(mvp, 0, pMatrix, 0, mvMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvp, 0)

        val g = (0.7f + sin(angle) * 0.3f).coerceIn(0.4f, 1.0f)
        val b = (0.4f + sin(angle) * 0.6f).coerceIn(0.2f, 1.0f)
        GLES20.glUniform4f(colorH, 1.0f, g, b, 1.0f)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        // [關鍵修復] 結束後立即關閉屬性陣列，防止污染場景
        GLES20.glDisableVertexAttribArray(posH)
    }

    fun drawLensFlare(pMatrix: FloatArray, vMatrix: FloatArray, sunPos: Float) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
        
        val angle = Math.toRadians((sunPos * 180f).toDouble()).toFloat()
        val sunWorldPos = floatArrayOf(cos(angle) * 150f, sin(angle) * 100f, 200f, 1.0f)
        
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, pMatrix, 0, vMatrix, 0)
        val sunScreenPos = FloatArray(4)
        Matrix.multiplyMV(sunScreenPos, 0, mvp, 0, sunWorldPos, 0)
        
        if (sunScreenPos[3] > 0) {
            val ndcX = sunScreenPos[0] / sunScreenPos[3]
            val ndcY = sunScreenPos[1] / sunScreenPos[3]
            
            if (abs(ndcX) < 1.3f && abs(ndcY) < 1.3f) {
                drawFlareElement(ndcX, ndcY, 0.4f, floatArrayOf(1f, 1f, 0.9f, 0.2f))
                val elements = listOf(0.5f to 0.1f, 0.2f to 0.06f, -0.3f to 0.08f, -0.6f to 0.15f)
                elements.forEach { (dist, scale) ->
                    drawFlareElement(ndcX * dist, ndcY * dist, scale, floatArrayOf(0.7f, 0.8f, 1f, 0.1f))
                }
            }
        }
        
        GLES20.glDisableVertexAttribArray(posH)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawFlareElement(ndcX: Float, ndcY: Float, scale: Float, color: FloatArray) {
        val m = FloatArray(16); Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, ndcX, ndcY, 0f)
        Matrix.scaleM(m, 0, scale, scale * 1.5f, 1f)
        GLES20.glUniformMatrix4fv(mvpH, 1, false, m, 0)
        GLES20.glUniform4f(colorH, color[0], color[1], color[2], color[3])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
}
