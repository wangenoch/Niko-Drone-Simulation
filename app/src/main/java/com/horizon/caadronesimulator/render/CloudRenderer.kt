package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * [v1.7.2] 專業穹頂雲層渲染器 - 視覺修復版
 * 修正：移除錯誤的 UV 邊緣遮罩，改用高度漸變 (vHeight)，徹底解決雲層消失與接縫問題。
 */
class CloudRenderer {
    private var program = 0
    private var posH = -1
    private var texCoordH = -1
    private var mvpH = -1
    private var offsetH = -1
    private var colorH = -1
    private var densityH = -1
    
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer

    private val radius = 4600f // 位於山脈牆外
    private val latitudeBands = 24
    private val longitudeBands = 24

    fun init() {
        val vShader = """
            attribute vec4 vPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 vTexCoord;
            varying float vHeight;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                vTexCoord = aTexCoord;
                vHeight = vPosition.y; // 傳遞高度供地平線淡入使用
            }
        """.trimIndent()

        val fShader = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uOffset;
            uniform vec4 vColor;
            uniform float uDensity;
            varying vec2 vTexCoord;
            varying float vHeight;
            void main() {
                // 1. [關鍵修正] 移除 UV 邊界判定，確保雲朵不失蹤
                vec2 shiftedCoord = vTexCoord + uOffset;
                vec4 texColor = texture2D(uTexture, shiftedCoord);
                
                // 2. 實施高度遮罩：讓雲層在地平線 (h=0) 以下自然變透明，防止穿模
                float hFade = smoothstep(-200.0, 400.0, vHeight);
                
                // 3. 雲層厚度與光影混合
                gl_FragColor = vec4(vColor.rgb, texColor.a * vColor.a * uDensity * hFade);
            }
        """.trimIndent()

        val vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also { GLES20.glShaderSource(it, vShader); GLES20.glCompileShader(it) }
        val fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also { GLES20.glShaderSource(it, fShader); GLES20.glCompileShader(it) }
        program = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, vs); GLES20.glAttachShader(it, fs); GLES20.glLinkProgram(it) }

        posH = GLES20.glGetAttribLocation(program, "vPosition")
        texCoordH = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpH = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        offsetH = GLES20.glGetUniformLocation(program, "uOffset")
        colorH = GLES20.glGetUniformLocation(program, "vColor")
        densityH = GLES20.glGetUniformLocation(program, "uDensity")

        // 構建半球體，並實施無縫 UV 採樣 (重複 8x8)
        val vList = mutableListOf<Float>()
        val tList = mutableListOf<Float>()
        for (lat in 0..latitudeBands) {
            val theta = (lat.toFloat() * Math.PI.toFloat() / (2f * latitudeBands))
            val sinT = Math.sin(theta.toDouble()).toFloat(); val cosT = Math.cos(theta.toDouble()).toFloat()
            for (lon in 0..longitudeBands) {
                val phi = (lon.toFloat() * 2f * Math.PI.toFloat() / longitudeBands)
                val sinP = Math.sin(phi.toDouble()).toFloat(); val cosP = Math.cos(phi.toDouble()).toFloat()
                vList.add(radius * cosP * sinT); vList.add(radius * cosT - 300f); vList.add(radius * sinP * sinT)
                tList.add(lon.toFloat() / longitudeBands * 8f); tList.add(lat.toFloat() / latitudeBands * 4f)
            }
        }

        val iList = mutableListOf<Short>()
        for (lat in 0 until latitudeBands) {
            for (lon in 0 until longitudeBands) {
                val f = (lat * (longitudeBands + 1) + lon).toShort(); val s = (f + longitudeBands + 1).toShort()
                iList.add(f); iList.add(s); iList.add((f + 1).toShort()); iList.add(s); iList.add((s + 1).toShort()); iList.add((f + 1).toShort())
            }
        }

        val fV = FloatArray(iList.size * 3); val fT = FloatArray(iList.size * 2)
        for (idx in iList.indices) {
            val p = iList[idx].toInt()
            fV[idx*3] = vList[p*3]; fV[idx*3+1] = vList[p*3+1]; fV[idx*3+2] = vList[p*3+2]
            fT[idx*2] = tList[p*2]; fT[idx*2+1] = tList[p*2+1]
        }
        vertexBuffer = ByteBuffer.allocateDirect(fV.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(fV).also { it.position(0) }
        texCoordBuffer = ByteBuffer.allocateDirect(fT.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(fT).also { it.position(0) }
        indicesCount = iList.size
    }

    private var indicesCount = 0

    fun draw(pMatrix: FloatArray, vMatrix: FloatArray, textureId: Int, offset: Pair<Float, Float>, skyColor: FloatArray, density: Float) {
        if (textureId == -1 || density <= 0.05f) return
        GLES20.glUseProgram(program)
        val staticV = vMatrix.copyOf(); staticV[12] = 0f; staticV[13] = 0f; staticV[14] = 0f
        val mvp = FloatArray(16); Matrix.multiplyMM(mvp, 0, pMatrix, 0, staticV, 0)
        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvp, 0)
        GLES20.glUniform2f(offsetH, offset.first, offset.second)
        GLES20.glUniform4fv(colorH, 1, skyColor, 0)
        GLES20.glUniform1f(densityH, density)
        GLES20.glEnableVertexAttribArray(posH); GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordH); GLES20.glVertexAttribPointer(texCoordH, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glDepthMask(false); GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, indicesCount); GLES20.glDepthMask(true)
        GLES20.glDisableVertexAttribArray(posH); GLES20.glDisableVertexAttribArray(texCoordH)
    }
}
