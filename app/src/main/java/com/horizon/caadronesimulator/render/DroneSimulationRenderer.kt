package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.horizon.caadronesimulator.logic.PhysicsEngine
import com.horizon.caadronesimulator.model.DronePhysicsState
import com.horizon.caadronesimulator.model.DroneRegistry
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * [v1.5.9] 模擬器渲染主引擎 - 科學氣候修復版
 * 修正：徹底解決 U/V 捲動接縫與層雲可見度問題。
 */
class DroneSimulationRenderer(private val onFlightDataUpdate: (Float, Float, Float, Float, Float, Float, Float, Boolean, Float, Int, androidx.compose.ui.geometry.Offset?, Float?, Float?, Float?, Float?) -> Unit) : GLSurfaceView.Renderer {
    private var program = 0
    private val vMatrix = FloatArray(16); private val pMatrix = FloatArray(16); private val mvpMatrix = FloatArray(16)
    private val mainVMatrix = FloatArray(16); private val mainPMatrix = FloatArray(16)
    private var viewWidth = 0; private var viewHeight = 0; private var lastFrameTime = 0L
    var physicsState = DronePhysicsState(posX = 0f, posZ = 0f)
    private val sunRenderer = SunRenderer(); private val cloudRenderer = CloudRenderer(); private val backdropRenderer = BackdropRenderer()
    
    // --- 渲染控制屬性 ---
    var ctrlThrottle = 0f; var ctrlYaw = 0f; var ctrlPitch = 0f; var ctrlRoll = 0f
    var isMotorLocked = true; var droneType = "QUAD_STANDARD"; var motorRpmFactor = 0f
    var cameraMode = "站位視角 (追蹤)"; var zoomFactor = 1.5f; var cameraTilt = 0f; var observerHeight = 6.0f
    var windLevel = 0; var windDirection = "無"; var windVariation = 0f; var windDirVariation = 0f; var timeOfDay = "中午"
    var showShadow = true; var shadowIntensity = 0.5f; var showObstacles = false; var isPaused = false; var applyPhysicalSpecs = false
    var enableVerticalDraft = false; var useHardcorePhysics = false; var isSunSimEnabled = false; var sunPosition = 0.5f
    var observerTilt = 0f; var showClouds = true; var cloudDensity = 0.5f; var showMountains = true 
    var useSimplifiedMarkers = false; var showSpecialTitle = false; var currentTitleText = ""; private var renderedTitleText = ""; var useFlightLimit = true; var mainFOV = 45f
    var showGroundAnchor = false; var isThrottleHoldActive = true; var lastManualTouchTime = 0L      
    var pipRect: android.graphics.Rect? = null; var zoomPipRect: android.graphics.Rect? = null
    private var randomWindPhase = 0f; private var turbulencePhase = 0f; private var specialTitleScreenPos: androidx.compose.ui.geometry.Offset? = null
    private var titleTextureId = -1; private var texH = -1; private var texCoordH = -1; private var useTexH = -1
    private var flagVisualAngle = 0f; private var cloudTextureId = -1; private var mountainTextureId = -1
    var weatherMode = 0; private var lastWeatherMode = -1; private var lastDensity = -1f; var cloudU = 0f; var cloudV = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vShader = "attribute vec4 vPosition; attribute vec2 aTexCoord; uniform mat4 uMVPMatrix; varying vec2 vTexCoord; void main() { gl_Position = uMVPMatrix * vPosition; vTexCoord = aTexCoord; }".trimIndent()
        val fShader = "precision mediump float; uniform vec4 vColor; uniform sampler2D uTexture; uniform bool uUseTex; varying vec2 vTexCoord; void main() { if (uUseTex) { gl_FragColor = texture2D(uTexture, vTexCoord); } else { gl_FragColor = vColor; } }".trimIndent()
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vShader); val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fShader)
        program = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, vs); GLES20.glAttachShader(it, fs); GLES20.glLinkProgram(it) }
        texH = GLES20.glGetUniformLocation(program, "uTexture"); texCoordH = GLES20.glGetAttribLocation(program, "aTexCoord"); useTexH = GLES20.glGetUniformLocation(program, "uUseTex")
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA); GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL); GLES20.glPolygonOffset(-1.0f, -1.0f)
        sunRenderer.init(); cloudRenderer.init(); backdropRenderer.init()
        if (currentTitleText.isBlank()) currentTitleText = com.horizon.caadronesimulator.model.AppConfig.SPECIAL_TITLE
        generateTitleTexture(); generateCloudTexture(); generateMountainTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { viewWidth = width; viewHeight = height; GLES20.glViewport(0, 0, width, height) }

    override fun onDrawFrame(gl: GL10?) {
        if (currentTitleText != renderedTitleText) generateTitleTexture()
        if (weatherMode != lastWeatherMode || abs(cloudDensity - lastDensity) > 0.05f) {
            generateCloudTexture()
            lastWeatherMode = weatherMode; lastDensity = cloudDensity
        }
        val now = System.nanoTime(); if (lastFrameTime == 0L) lastFrameTime = now
        val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f); lastFrameTime = now
        if (!isPaused) {
            randomWindPhase += dt * (1.2f + windVariation * 0.6f); turbulencePhase += dt * (0.8f + windVariation * 0.4f)
            val atmos = PhysicsEngine.AtmosConfig(windLevel, windDirection, windVariation.toInt(), windDirVariation.toInt(), enableVerticalDraft, useFlightLimit, randomWindPhase, turbulencePhase, 0f, applyPhysicalSpecs, isMotorLocked, useHardcorePhysics, com.horizon.caadronesimulator.model.DroneState.getInstance().useStrictLanding, showObstacles)
            val result = PhysicsEngine.step(dt, physicsState, PhysicsEngine.ControlInput(ctrlThrottle, ctrlYaw, ctrlPitch, ctrlRoll), atmos, droneType)
            com.horizon.caadronesimulator.logic.CameraDirector.update(physicsState.posX, physicsState.posY - getGroundY(), physicsState.posZ, observerHeight, observerTilt, zoomFactor, mainFOV, cameraMode, lastManualTouchTime, droneType, dt, com.horizon.caadronesimulator.model.DroneState.getInstance())
            // [v1.5.9] 終極修正：直接透傳 PhysicsEngine 返回的 impactSpeed。
            // 禁止在 Renderer 重新計算速度，因為此時物理引擎可能已經為了地面摩擦而將 velocity 歸零。
            onFlightDataUpdate(physicsState.posY, physicsState.posX, physicsState.posZ, physicsState.yaw, physicsState.visPitch, physicsState.visRoll, result.impactSpeed, result.isImpact, physicsState.batteryVoltage, physicsState.batteryPercent, specialTitleScreenPos, com.horizon.caadronesimulator.logic.CameraDirector.smoothedHeight, com.horizon.caadronesimulator.logic.CameraDirector.smoothedTilt, com.horizon.caadronesimulator.logic.CameraDirector.smoothedZoom, com.horizon.caadronesimulator.logic.CameraDirector.smoothedFov)
        }
        updateEnvironmentLighting(); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        val spec = DroneRegistry.getSpec(droneType); val finalFov = if (cameraMode == "FPV 視角") spec.fpvFov else com.horizon.caadronesimulator.logic.CameraDirector.smoothedFov
        Matrix.perspectiveM(pMatrix, 0, finalFov / com.horizon.caadronesimulator.logic.CameraDirector.smoothedZoom, viewWidth.toFloat() / viewHeight, 1.0f, 6000f)
        com.horizon.caadronesimulator.logic.CameraDirector.computeMainViewMatrix(vMatrix, cameraMode, physicsState.posX, physicsState.posY, physicsState.posZ, physicsState.yaw, physicsState.posX + physicsState.velX * 0.12f, physicsState.posZ + physicsState.velZ * 0.12f, cameraTilt, droneType)
        System.arraycopy(pMatrix, 0, mainPMatrix, 0, 16); System.arraycopy(vMatrix, 0, mainVMatrix, 0, 16); calculateProjectedTitlePos(); renderScene()
        pipRect?.let { rect -> GLES20.glEnable(GLES20.GL_SCISSOR_TEST); val glY = viewHeight - rect.bottom; GLES20.glScissor(rect.left, glY, rect.width(), rect.height()); GLES20.glViewport(rect.left, glY, rect.width(), rect.height()); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT); Matrix.perspectiveM(pMatrix, 0, spec.fpvFov, rect.width().toFloat() / rect.height(), 0.5f, 500f); com.horizon.caadronesimulator.logic.CameraDirector.computeMainViewMatrix(vMatrix, "FPV 視角", physicsState.posX, physicsState.posY, physicsState.posZ, physicsState.yaw, physicsState.posX + physicsState.velX * 0.12f, physicsState.posZ + physicsState.velZ * 0.12f, cameraTilt, droneType); renderScene(); GLES20.glDisable(GLES20.GL_SCISSOR_TEST) }
        zoomPipRect?.let { rect -> GLES20.glEnable(GLES20.GL_SCISSOR_TEST); val glY = viewHeight - rect.bottom; GLES20.glScissor(rect.left, glY, rect.width(), rect.height()); GLES20.glViewport(rect.left, glY, rect.width(), rect.height()); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT); Matrix.perspectiveM(pMatrix, 0, com.horizon.caadronesimulator.logic.CameraDirector.smoothedZoomPipFov, rect.width().toFloat() / rect.height(), 0.1f, 1000f); com.horizon.caadronesimulator.logic.CameraDirector.computePrecisionViewMatrix(vMatrix, physicsState.posX, physicsState.posY, physicsState.posZ); renderScene(); GLES20.glDisable(GLES20.GL_SCISSOR_TEST) }
        if (isSunSimEnabled) sunRenderer.drawLensFlare(mainPMatrix, mainVMatrix, sunPosition)
    }

    private fun updateEnvironmentLighting() {
        if (isSunSimEnabled) { val angle = Math.toRadians((sunPosition * 180f).toDouble()).toFloat(); val s = sin(angle); val r = (1.0f - (1.0f - 0.53f) * s).coerceIn(0.53f, 1.0f); val g = (0.6f + 0.21f * s).coerceIn(0.6f, 0.81f); val b = (0.3f + 0.62f * s).coerceIn(0.3f, 0.92f); GLES20.glClearColor(r, g, b, 1.0f) }
        else { val skyR: Float; val skyG: Float; val skyB: Float; if (weatherMode == 3) { skyR = 0.4f; skyG = 0.45f; skyB = 0.5f } else { when(timeOfDay) { "早晨" -> { skyR = 1f; skyG = 0.7f; skyB = 0.5f }; "下午" -> { skyR = 1f; skyG = 0.6f; skyB = 0.3f }; else -> { skyR = 0.53f; skyG = 0.81f; skyB = 0.92f } } }
        GLES20.glClearColor(skyR, skyG, skyB, 1.0f) }
    }

    private fun renderScene() {
        Matrix.multiplyMM(mvpMatrix, 0, pMatrix, 0, vMatrix, 0); GLES20.glUseProgram(program); val posH = GLES20.glGetAttribLocation(program, "vPosition"); val colorH = GLES20.glGetUniformLocation(program, "vColor"); val mvpH = GLES20.glGetUniformLocation(program, "uMVPMatrix"); GLES20.glUniform1i(useTexH, 0)
        sunRenderer.drawSun(pMatrix, vMatrix, sunPosition); GLES20.glUseProgram(program)
        if (showClouds) {
            val actualDensity = cloudDensity.coerceIn(0f, 1f)
            val cColor = if (weatherMode == 3) floatArrayOf(0.7f, 0.7f, 0.75f, 0.85f) else floatArrayOf(1f, 1f, 1f, 0.7f)
            cloudRenderer.draw(pMatrix, vMatrix, cloudTextureId, Pair(cloudU, cloudV), cColor, actualDensity); GLES20.glUseProgram(program)
        }
        if (showMountains) { backdropRenderer.draw(pMatrix, vMatrix, mountainTextureId, timeOfDay); GLES20.glUseProgram(program) }
        FieldRenderer.drawField(posH, colorH, mvpH, mvpMatrix, windLevel, windDirection, flagVisualAngle, physicsState.flightTime, showObstacles, isSunSimEnabled, sunPosition, useSimplifiedMarkers, if(showSpecialTitle) titleTextureId else -1, texH, texCoordH, useTexH)
        ArAnchorRenderer.drawAnchor(mvpMatrix, physicsState.posX, physicsState.posY, physicsState.posZ, 0f, posH, colorH, mvpH, showGroundAnchor)
        DroneRenderer.drawDroneShadow(posH, colorH, mvpH, mvpMatrix, droneType, physicsState.posX, physicsState.posY, physicsState.posZ, timeOfDay, showShadow, shadowIntensity, isSunSimEnabled, sunPosition)
        DroneRenderer.drawActiveDrone(posH, colorH, mvpH, mvpMatrix, droneType, physicsState.posX, physicsState.posY, physicsState.posZ, physicsState.yaw, physicsState.visPitch, physicsState.visRoll, physicsState.flightTime, isMotorLocked, this.motorRpmFactor)
    }

    fun updateControls(yaw: Float, throttle: Float, roll: Float, pitch: Float) { ctrlYaw = yaw; ctrlThrottle = throttle; ctrlRoll = roll; ctrlPitch = pitch }
    private fun getGroundY(): Float = DroneRegistry.getSpec(droneType).groundOffset
    
    /** [v1.6.1] 重新擲骰子：生成新的隨機風向基準角並存入全域狀態 */
    fun rerollWindDirection() {
        val ds = com.horizon.caadronesimulator.model.DroneState.getInstance()
        ds.env.randomWindAngle = (java.util.Random().nextFloat() * 360f)
    }

    fun resetFlight() { physicsState.reset(getGroundY(), 0f); onFlightDataUpdate(physicsState.posY, 0f, 0f, 0f, 0f, 0f, 0f, false, 4.2f, 100, null, null, null, null, null) }
    private fun calculateProjectedTitlePos() { if (!showSpecialTitle) { specialTitleScreenPos = null; return }; val worldPos = floatArrayOf(0f, 0.015f, 3.0f, 1.0f); val mvp = FloatArray(16); Matrix.multiplyMM(mvp, 0, mainPMatrix, 0, mainVMatrix, 0); val screenPos = FloatArray(4); Matrix.multiplyMV(screenPos, 0, mvp, 0, worldPos, 0); if (screenPos[3] > 0) { val ndcX = screenPos[0] / screenPos[3]; val ndcY = screenPos[1] / screenPos[3]; specialTitleScreenPos = androidx.compose.ui.geometry.Offset((ndcX + 1f) / 2f * viewWidth, (1f - ndcY) / 2f * viewHeight) } else { specialTitleScreenPos = null } }

    private fun generateCloudTexture() {
        val size = 512; val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888); val canvas = android.graphics.Canvas(bitmap); val paint = android.graphics.Paint().apply { isAntiAlias = true }
        val random = java.util.Random(weatherMode.toLong() + 42)
        val coverage = (cloudDensity * 100).toInt().coerceAtLeast(5)

        fun drawCloudElement(cx: Float, cy: Float, rotation: Float, action: (android.graphics.Canvas) -> Unit) {
            val s = size.toFloat()
            for (dx in listOf(-s, 0f, s)) {
                for (dy in listOf(-s, 0f, s)) {
                    canvas.save(); canvas.translate(cx + dx, cy + dy); canvas.rotate(rotation); action(canvas); canvas.restore()
                }
            }
        }

        when (weatherMode) {
            1 -> { // 晴空卷雲 (Cirrus)
                repeat(coverage / 2) {
                    val cx = random.nextFloat() * size; val cy = random.nextFloat() * size; val rw = 180f + random.nextFloat() * 220f; val rh = 6f + random.nextFloat() * 10f; val alpha = 85 + random.nextInt(60); val rot = random.nextFloat() * 30f - 15f
                    paint.shader = android.graphics.RadialGradient(0f, 0f, rw, android.graphics.Color.argb(alpha, 255, 255, 255), android.graphics.Color.argb(0, 255, 255, 255), android.graphics.Shader.TileMode.CLAMP)
                    drawCloudElement(cx, cy, rot) { it.drawOval(-rw, -rh, rw, rh, paint) }
                }
            }
            2 -> { // 積雲密佈 (Cumulus)
                repeat(coverage / 3) {
                    val bCX = random.nextFloat() * size; val bCY = random.nextFloat() * size
                    repeat(6) {
                        val offsetCX = random.nextFloat() * 70f - 35f; val offsetCY = random.nextFloat() * 50f - 25f; val r = 35f + random.nextFloat() * 50f; val alpha = 180 + random.nextInt(75)
                        paint.shader = android.graphics.RadialGradient(0f, 0f, r, android.graphics.Color.argb(alpha, 255, 255, 255), android.graphics.Color.argb(0, 255, 255, 255), android.graphics.Shader.TileMode.CLAMP)
                        drawCloudElement(bCX + offsetCX, bCY + offsetCY, 0f) { it.drawCircle(0f, 0f, r, paint) }
                        paint.shader = android.graphics.RadialGradient(0f, 0f, r, android.graphics.Color.argb(95, 40, 40, 60), android.graphics.Color.argb(0, 0, 0, 0), android.graphics.Shader.TileMode.CLAMP)
                        drawCloudElement(bCX + offsetCX, bCY + offsetCY + r * 0.4f, 0f) { it.drawCircle(0f, 0f, r, paint) }
                    }
                }
            }
            3 -> { // 低空層雲 (Stratus): 捨棄減法，改用「多層重疊法」
                bitmap.eraseColor(android.graphics.Color.argb(225, 145, 150, 165))
                repeat(coverage * 2) {
                    val cx = random.nextFloat() * size; val cy = random.nextFloat() * size; val r = 120f + random.nextFloat() * 180f; val alpha = 35 + random.nextInt(55)
                    paint.shader = android.graphics.RadialGradient(0f, 0f, r, android.graphics.Color.argb(alpha, 255, 255, 255), android.graphics.Color.argb(0, 255, 255, 255), android.graphics.Shader.TileMode.CLAMP)
                    drawCloudElement(cx, cy, 0f) { it.drawCircle(0f, 0f, r, paint) }
                }
            }
            else -> {
                repeat(coverage) {
                    val cx = random.nextFloat() * size; val cy = random.nextFloat() * size; val r = 35f + random.nextFloat() * 60f
                    paint.shader = android.graphics.RadialGradient(0f, 0f, r, android.graphics.Color.argb(130, 255, 255, 255), android.graphics.Color.argb(0, 255, 255, 255), android.graphics.Shader.TileMode.CLAMP)
                    drawCloudElement(cx, cy, 0f) { it.drawCircle(0f, 0f, r, paint) }
                }
            }
        }
        
        if (cloudTextureId != -1) GLES20.glDeleteTextures(1, intArrayOf(cloudTextureId), 0)
        val textures = IntArray(1); GLES20.glGenTextures(1, textures, 0); cloudTextureId = textures[0]; GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cloudTextureId); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT); android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0); bitmap.recycle()
    }

    private fun generateMountainTexture() {
        val w = 2056; val hSec = 512; val hTotal = hSec * 3; val bitmap = android.graphics.Bitmap.createBitmap(w, hTotal, android.graphics.Bitmap.Config.ARGB_8888); val canvas = android.graphics.Canvas(bitmap); val paint = android.graphics.Paint().apply { isAntiAlias = true }
        val styles = listOf(Triple("早晨", intArrayOf(android.graphics.Color.argb(0, 0, 0, 0), android.graphics.Color.argb(120, 140, 120, 200), android.graphics.Color.argb(255, 20, 30, 60)), android.graphics.Color.argb(150, 200, 180, 255)), Triple("中午", intArrayOf(android.graphics.Color.argb(0, 0, 0, 0), android.graphics.Color.argb(120, 100, 160, 220), android.graphics.Color.argb(255, 25, 65, 35)), android.graphics.Color.argb(140, 160, 210, 255)), Triple("下午", intArrayOf(android.graphics.Color.argb(0, 0, 0, 0), android.graphics.Color.argb(120, 220, 160, 100), android.graphics.Color.argb(255, 60, 40, 20)), android.graphics.Color.argb(180, 255, 200, 120)))
        styles.forEachIndexed { i, style ->
            val yB = i * hSec.toFloat(); val shader = android.graphics.LinearGradient(0f, yB, 0f, yB + hSec, style.second, floatArrayOf(0f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP); paint.shader = shader; canvas.drawRect(0f, yB, w.toFloat(), yB + hSec, paint); paint.shader = null; paint.style = android.graphics.Paint.Style.FILL; paint.color = style.second.last()
            fun getH(x: Float): Float { val fx = (x - 4f); val f1 = 2f * PI.toFloat() / 2048f; val f2 = 8f * PI.toFloat() / 2048f; val hV = sin(fx * f1) * 80f + sin(fx * f2 + 1.5f) * 45f; var p = 0f; val dN = abs(((fx + 2048f) % 2048f) - 512f); if (dN < 300f) p = 220f * (1f + cos((dN / 300f) * PI.toFloat())) / 2f; return yB + hSec * 0.78f - hV - p }
            val fillPath = android.graphics.Path(); fillPath.moveTo(-10f, yB + hSec); for (x in -10..2066 step 2) { fillPath.lineTo(x.toFloat(), getH(x.toFloat())) }; fillPath.lineTo(2066f, yB + hSec); fillPath.close(); canvas.drawPath(fillPath, paint)
            paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 3.5f; paint.color = style.third; val edgePath = android.graphics.Path(); for (x in -10..2066 step 2) { if (x == -10) edgePath.moveTo(-10f, getH(-10f)) else edgePath.lineTo(x.toFloat(), getH(x.toFloat())) }; canvas.drawPath(edgePath, paint)
        }
        val textures = IntArray(1); GLES20.glGenTextures(1, textures, 0); mountainTextureId = textures[0]; GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mountainTextureId); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE); android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0); bitmap.recycle()
    }

    private fun generateTitleTexture() {
        val textToRender = if (currentTitleText.isNotBlank()) currentTitleText else com.horizon.caadronesimulator.model.AppConfig.SPECIAL_TITLE
        val bitmap = android.graphics.Bitmap.createBitmap(2048, 256, android.graphics.Bitmap.Config.ARGB_8888); val canvas = android.graphics.Canvas(bitmap); val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 140f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; alpha = 255 }
        canvas.drawText(textToRender, 1024f, 170f, paint)
        if (titleTextureId != -1) GLES20.glDeleteTextures(1, intArrayOf(titleTextureId), 0)
        val textures = IntArray(1); GLES20.glGenTextures(1, textures, 0); titleTextureId = textures[0]; GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, titleTextureId); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR); android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0); bitmap.recycle(); renderedTitleText = textToRender
    }

    private fun loadShader(type: Int, code: String): Int = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, code); GLES20.glCompileShader(it) }
}
