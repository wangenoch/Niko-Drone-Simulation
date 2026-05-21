package com.horizon.caadronesimulator.render.util

import android.opengl.GLES20

/**
 * [v1.7.6] FBO (Framebuffer Object) 管理器
 * 用於優化 PiP 渲染效能，實現離屏渲染與紋理合成。
 */
class FboManager(val width: Int, val height: Int) {
    private var framebufferId = -1
    private var textureId = -1
    private var depthBufferId = -1

    fun init() {
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        framebufferId = fbo[0]

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val depth = IntArray(1)
        GLES20.glGenRenderbuffers(1, depth, 0)
        depthBufferId = depth[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBufferId)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthBufferId)

        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            // FBO 初始化失敗處理
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glViewport(0, 0, width, height)
    }

    fun unbind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun getTextureId(): Int = textureId

    fun release() {
        if (framebufferId != -1) GLES20.glDeleteFramebuffers(1, intArrayOf(framebufferId), 0)
        if (textureId != -1) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        if (depthBufferId != -1) GLES20.glDeleteRenderbuffers(1, intArrayOf(depthBufferId), 0)
    }
}
