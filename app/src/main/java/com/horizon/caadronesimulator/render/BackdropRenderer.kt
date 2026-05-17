package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * [v1.7.0] 靜態貼圖遠景渲染器 - 終極接縫終結版
 * 修正：實施「UV 縮進採樣 (Texture Gutter)」與「幾何硬閉合」，徹底消滅垂直光柱。
 */
class BackdropRenderer {
    private var program = 0
    private var posH = -1
    private var texCoordH = -1
    private var mvpH = -1
    private var texH = -1
    private var vOffsetH = -1 

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texBuffer: FloatBuffer

    private val radius = 3800f
    private val height = 1100f
    private val segments = 64

    fun init() {
        val vShader = """
            attribute vec4 vPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val fShader = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform float uVOffset;
            varying vec2 vTexCoord;
            void main() {
                // 1. [關鍵修正] U 軸縮進採樣：避開貼圖左右邊緣不穩定的抗鋸齒像素
                // 採樣區間從原本的 0.0~1.0 縮進至 4/2056 ~ 2052/2056
                float u = 0.0019 + vTexCoord.x * 0.9961;
                
                // 2. V 軸區間採樣
                float v = clamp(vTexCoord.y * 0.3333 + uVOffset, uVOffset + 0.005, uVOffset + 0.328);
                
                vec4 texColor = texture2D(uTexture, vec2(u, v));
                
                // 3. 頂部淡入
                float fade = smoothstep(0.0, 0.3, vTexCoord.y);
                gl_FragColor = vec4(texColor.rgb, texColor.a * fade);
            }
        """.trimIndent()

        val vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also { GLES20.glShaderSource(it, vShader); GLES20.glCompileShader(it) }
        val fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also { GLES20.glShaderSource(it, fShader); GLES20.glCompileShader(it) }
        program = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, vs); GLES20.glAttachShader(it, fs); GLES20.glLinkProgram(it) }

        posH = GLES20.glGetAttribLocation(program, "vPosition")
        texCoordH = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpH = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        texH = GLES20.glGetUniformLocation(program, "uTexture")
        vOffsetH = GLES20.glGetUniformLocation(program, "uVOffset")

        val vData = FloatArray(segments * 18); val tData = FloatArray(segments * 12)
        var vIdx = 0; var tIdx = 0
        
        // 預先計算起始點
        val x0 = radius; val z0 = 0f

        for (i in 0 until segments) {
            val ang1 = (i.toFloat() / segments) * 2f * Math.PI.toFloat()
            val ang2 = ((i + 1).toFloat() / segments) * 2f * Math.PI.toFloat()
            
            val x1 = Math.cos(ang1.toDouble()).toFloat() * radius
            val z1 = Math.sin(ang1.toDouble()).toFloat() * radius
            
            // [關鍵修正] 幾何硬閉合：最後一個頂點強制等於起始點 (x0, z0)
            val x2 = if (i == segments - 1) x0 else Math.cos(ang2.toDouble()).toFloat() * radius
            val z2 = if (i == segments - 1) z0 else Math.sin(ang2.toDouble()).toFloat() * radius

            val u1 = i.toFloat() / segments
            val u2 = (i + 1).toFloat() / segments

            vData[vIdx++] = x1; vData[vIdx++] = -300f; vData[vIdx++] = z1; tData[tIdx++] = u1; tData[tIdx++] = 1f
            vData[vIdx++] = x2; vData[vIdx++] = -300f; vData[vIdx++] = z2; tData[tIdx++] = u2; tData[tIdx++] = 1f
            vData[vIdx++] = x1; vData[vIdx++] = height; vData[vIdx++] = z1; tData[tIdx++] = u1; tData[tIdx++] = 0f
            vData[vIdx++] = x1; vData[vIdx++] = height; vData[vIdx++] = z1; tData[tIdx++] = u1; tData[tIdx++] = 0f
            vData[vIdx++] = x2; vData[vIdx++] = -300f; vData[vIdx++] = z2; tData[tIdx++] = u2; tData[tIdx++] = 1f
            vData[vIdx++] = x2; vData[vIdx++] = height; vData[vIdx++] = z2; tData[tIdx++] = u2; tData[tIdx++] = 0f
        }
        vertexBuffer = ByteBuffer.allocateDirect(vData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(vData).also { it.position(0) }
        texBuffer = ByteBuffer.allocateDirect(tData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(tData).also { it.position(0) }
    }

    fun draw(pMatrix: FloatArray, vMatrix: FloatArray, textureId: Int, timeOfDay: String) {
        if (textureId == -1) return
        GLES20.glUseProgram(program)
        val staticV = vMatrix.copyOf(); staticV[12] = 0f; staticV[13] = 0f; staticV[14] = 0f
        val mvp = FloatArray(16); Matrix.multiplyMM(mvp, 0, pMatrix, 0, staticV, 0)
        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvp, 0)
        val vOffset = when(timeOfDay) { "早晨" -> 0.0f; "下午" -> 0.6666f; else -> 0.3333f }
        GLES20.glUniform1f(vOffsetH, vOffset)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId); GLES20.glUniform1i(texH, 0)
        GLES20.glEnableVertexAttribArray(posH); GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordH); GLES20.glVertexAttribPointer(texCoordH, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, segments * 6)
        GLES20.glDisableVertexAttribArray(posH); GLES20.glDisableVertexAttribArray(texCoordH)
    }
}
