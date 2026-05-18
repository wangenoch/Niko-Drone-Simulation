package com.horizon.caadronesimulator.ui.common

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.horizon.caadronesimulator.render.DroneSimulationRenderer

/**
 * [v1.5.9] 專屬 3D 渲染視圖組件
 * 提供穩定的 OpenGL ES 2.0 上下文與連續渲染模式。
 */
class SimulationSurfaceView @JvmOverloads constructor(
    context: Context,
    renderer: DroneSimulationRenderer,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }
}
