package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.horizon.caadronesimulator.model.DroneRegistry
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class DroneSimulationRenderer(private val onFlightDataUpdate: (Float, Float, Float, Float, Float, Float, Float, Boolean) -> Unit) : GLSurfaceView.Renderer {
    private var program = 0
    private val vMatrix = FloatArray(16)
    private val pMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    private var viewWidth = 0
    private var viewHeight = 0
    private var lastFrameTime = 0L
    
    // 飛行狀態
    private var curX = 0f; private var curY = 0.05f; private var curZ = -6.0f; private var curYaw = 0f
    private var velX = 0f; private var velY = 0f; private var velZ = 0f
    private var visPitch = 0f; private var visRoll = 0f
    private var flightTime = 0f
    
    // 外部控制參數
    var ctrlThrottle = 0f; var ctrlYaw = 0f; var ctrlPitch = 0f; var ctrlRoll = 0f
    var isMotorLocked = true; var droneType = "QUAD_STANDARD"
    var cameraMode = "站位視角 (追蹤)"; var zoomFactor = 1.5f; var cameraTilt = 0f
    var windLevel = 0; var windDirection = "無"; var windVariation = 0f; var windDirVariation = 0f; var timeOfDay = "中午"
    var showShadow = true
    var shadowIntensity = 0.5f
    var showObstacles = false
    var isPaused = false
    var applyPhysicalSpecs = false

    // 子畫面 (PiP) 參數
    var pipRect: android.graphics.Rect? = null

    private var randomWindPhase = 0f
    private var randomDirAngle = 0f
    private var randomDirTarget = 0f
    private var randomDirVelocity = 0f
    
    private val hZ = -6.0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vShader = "attribute vec4 vPosition; uniform mat4 uMVPMatrix; void main() { gl_Position = uMVPMatrix * vPosition; }"
        val fShader = "precision mediump float; uniform vec4 vColor; void main() { gl_FragColor = vColor; }"
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vShader)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fShader)
        program = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, vs); GLES20.glAttachShader(it, fs); GLES20.glLinkProgram(it) }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1.0f, -1.0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width; viewHeight = height; GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        if (lastFrameTime == 0L) lastFrameTime = now
        val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameTime = now

        if (!isPaused) {
            updatePhysics(dt, getGroundY())
        }

        updateEnvironmentLighting()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 1. 繪製主畫面
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        val mainFov = if (cameraMode == "FPV 視角") (if (droneType == "HEAVY_LIFT") 110f else 85f) else 60f
        Matrix.perspectiveM(pMatrix, 0, mainFov / zoomFactor, viewWidth.toFloat() / viewHeight, 1.0f, 1000f)
        updateCameraMatrix(cameraMode)
        renderScene()

        // 2. 繪製子畫面 (PiP) - FPV 視角
        pipRect?.let { rect ->
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            // 轉換坐標系 (OpenGL 底部為 0)
            val glY = viewHeight - rect.bottom
            GLES20.glScissor(rect.left, glY, rect.width(), rect.height())
            GLES20.glViewport(rect.left, glY, rect.width(), rect.height())
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            // 子畫面 FOV 固定為 FPV 標準
            val pipFov = if (droneType == "HEAVY_LIFT") 110f else 85f
            Matrix.perspectiveM(pMatrix, 0, pipFov, rect.width().toFloat() / rect.height(), 0.5f, 500f)
            updateCameraMatrix("FPV 視角")
            renderScene()
            
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        }
    }

    private fun renderScene() {
        Matrix.multiplyMM(mvpMatrix, 0, pMatrix, 0, vMatrix, 0)
        GLES20.glUseProgram(program)
        val posH = GLES20.glGetAttribLocation(program, "vPosition")
        val colorH = GLES20.glGetUniformLocation(program, "vColor")
        val mvpH = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        
        // 獲取當前風力的實際向量，用於計算旗幟偏向
        // 在目前的坐標系中 (+X是左, +Z是前)，旗幟模型沿 +X 延伸
        // 經推導，旋轉角應為 atan2(-windZ, windX) 才能與 3D 空間受力對齊
        val windVec = getCurrentWindVector()
        val windAngleRad = atan2(-windVec[1].toDouble(), windVec[0].toDouble()).toFloat()
        val windAngleDeg = Math.toDegrees(windAngleRad.toDouble()).toFloat()
        
        FieldRenderer.drawField(posH, colorH, mvpH, mvpMatrix, windLevel, windDirection, windAngleDeg, flightTime, showObstacles)
        DroneRenderer.drawDroneShadow(posH, colorH, mvpH, mvpMatrix, droneType, curX, curY, curZ, timeOfDay, showShadow, shadowIntensity)
        DroneRenderer.drawActiveDrone(posH, colorH, mvpH, mvpMatrix, droneType, curX, curY, curZ, curYaw, visPitch, visRoll, flightTime, isMotorLocked)
    }

    private fun getCurrentWindVector(): FloatArray {
        if (windLevel <= 0) return floatArrayOf(0f, 0f)
        return if (windDirection == "隨機") {
            floatArrayOf(sin(randomDirAngle), cos(randomDirAngle))
        } else {
            when(windDirection) {
                "北風" -> floatArrayOf(0f, -1f)
                "南風" -> floatArrayOf(0f, 1f)
                "東風" -> floatArrayOf(1f, 0f)
                "西風" -> floatArrayOf(-1f, 0f)
                else -> floatArrayOf(0f, 0f)
            }
        }
    }

    private fun updatePhysics(dt: Float, groundY: Float) {
        val mass = DroneRegistry.getActiveMass(droneType, applyPhysicalSpecs)
        val power = DroneRegistry.getActivePower(droneType, applyPhysicalSpecs)
        val damping = DroneRegistry.getActiveDamping(droneType, applyPhysicalSpecs)

        if (!isMotorLocked) {
            flightTime += dt
            
            val prevVelY = velY
            velY += ((ctrlThrottle * 8.0f) - velY) * (5.0f / mass) * dt
            
            var nextY = curY + velY * dt
            val maxAlt = groundY + 30.0f
            if (nextY > maxAlt) { nextY = maxAlt; velY = 0f }

            var isImpact = false
            var reportedSpeed = 0f

            val spec = DroneRegistry.getSpec(droneType)
            val tiltRad = max(abs(visPitch), abs(visRoll)) * (Math.PI.toFloat() / 180f)
            val tiltOffset = spec.collisionRadius * sin(tiltRad)
            val effectiveBottom = nextY - tiltOffset

            if (nextY < groundY || effectiveBottom < 0.05f) {
                val totalSpeed = sqrt(velX * velX + velY * velY + velZ * velZ)
                if (abs(prevVelY) > 2.2f || (sqrt(velX * velX + velZ * velZ) > 3.5f && effectiveBottom < 0.1f) || (max(abs(visPitch), abs(visRoll)) > 25f && effectiveBottom < 0.15f)) {
                    isImpact = true
                    reportedSpeed = totalSpeed
                }
                if (nextY < groundY) { curY = groundY; velY = 0f } else { curY = nextY }
            } else { curY = nextY }

            if (curY > groundY + 0.01f) {
                curYaw -= ctrlYaw * 120.0f * dt
                val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                val cosY = cos(rad); val sinY = sin(rad)
                val rollInput = -ctrlRoll; val pitchInput = ctrlPitch
                visPitch += (pitchInput * 25f - visPitch) * 8f * dt; visRoll += (rollInput * 25f - visRoll) * 8f * dt
                val accX = (cosY * rollInput + sinY * pitchInput) * power
                val accZ = (-sinY * rollInput + cosY * pitchInput) * power
                velX += accX * dt; velZ += accZ * dt
                applyWindForces(dt, mass)
                velX *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
                velZ *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
                curX += velX * dt; curZ += velZ * dt
                if (!isImpact) reportedSpeed = sqrt(velX * velX + velY * velY + velZ * velZ)
                onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, reportedSpeed, isImpact)
            } else { 
                velX = 0f; velZ = 0f
                if (!isImpact) reportedSpeed = abs(velY)
                onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, reportedSpeed, isImpact)
            }
        } else { 
            curY = groundY; velX = 0f; velY = 0f; velZ = 0f
            onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, 0f, false) 
        }
    }

    private fun updateEnvironmentLighting() {
        when(timeOfDay) {
            "早晨" -> GLES20.glClearColor(1f, 0.7f, 0.5f, 1.0f)
            "中午" -> GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
            "下午" -> GLES20.glClearColor(1f, 0.6f, 0.3f, 1.0f)
            else -> GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
        }
    }

    private var windChangeTimer = 0f
    private fun applyWindForces(dt: Float, mass: Float) {
        if (windLevel <= 0) return
        val beaufortFactor = (windLevel.toFloat().pow(1.5f)) * 0.45f
        val dragArea = when { mass > 2.5f -> 1.8f; mass > 1.5f -> 1.4f; else -> 1.0f }
        val windBase = (beaufortFactor * dragArea) / mass
        randomWindPhase += dt * (1.2f + windVariation * 0.6f)
        val wave1 = sin(randomWindPhase); val wave2 = sin(randomWindPhase * 0.45f + 2.0f)
        val gust = 1.0f + (windVariation * 0.35f) * (wave1 * 0.6f + wave2 * 0.4f)
        
        val wVec = getCurrentWindVector()
        velX += windBase * wVec[0] * gust * dt
        velZ += windBase * wVec[1] * gust * dt

        if (windDirection == "隨機") {
            windChangeTimer -= dt
            if (windChangeTimer <= 0f) {
                val drift = (if ((0..1).random() == 0) 1 else -1) * (30f + (0..30).random().toFloat())
                randomDirTarget += drift * (PI.toFloat() / 180f)
                windChangeTimer = 3.0f + (1.0f - windDirVariation / 5f) * 4.0f
                randomDirVelocity = 0.1f + windDirVariation * 0.1f
            }
            val angleDiff = randomDirTarget - randomDirAngle
            randomDirAngle += angleDiff * (0.5f + windDirVariation * 0.5f) * dt
        }
    }

    private fun updateCameraMatrix(mode: String) {
        when (mode) {
            "站位視角 (追蹤)" -> Matrix.setLookAtM(vMatrix, 0, 0f, 6f, -22f, curX, curY, curZ, 0f, 1f, 0f)
            "站位視角 (固定)" -> Matrix.setLookAtM(vMatrix, 0, 0f, 6f, -22f, 0f, 0f, 0f, 0f, 1f, 0f)
            "跟隨視角" -> {
                val rad = Math.toRadians(curYaw.toDouble()).toFloat(); val camX = curX - sin(rad) * 5f; val camZ = curZ - cos(rad) * 5f
                Matrix.setLookAtM(vMatrix, 0, camX, curY + 2.5f, camZ, curX, curY, curZ, 0f, 1f, 0f)
            }
            "FPV 視角" -> {
                val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                val tRad = Math.toRadians(cameraTilt.toDouble()).toFloat()
                val s = DroneRegistry.getSpec(droneType).scale
                val fOff = if (droneType == "HEAVY_LIFT") 0.38f * s else 0.4f * s
                val eY = curY + 0.1f * s
                val eX = curX + sin(rad) * fOff
                val eZ = curZ + cos(rad) * fOff
                val lX = eX + sin(rad) * cos(tRad) * 10f
                val lZ = eZ + cos(rad) * cos(tRad) * 10f
                val lY = eY + sin(tRad) * 10f
                Matrix.setLookAtM(vMatrix, 0, eX, eY, eZ, lX, lY, lZ, -sin(rad) * sin(tRad), cos(tRad), -cos(rad) * sin(tRad))
            }
            else -> Matrix.setLookAtM(vMatrix, 0, 0f, 6f, -22f, 0f, 0f, 0f, 0f, 1f, 0f)
        }
    }

    fun updateControls(yaw: Float, throttle: Float, roll: Float, pitch: Float) { ctrlYaw = yaw; ctrlThrottle = throttle; ctrlRoll = roll; ctrlPitch = pitch }
    private fun getGroundY(): Float = DroneRegistry.getSpec(droneType).groundOffset
    private fun loadShader(type: Int, code: String): Int = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, code); GLES20.glCompileShader(it) }

    fun resetFlight() { 
        curX = 0f; curY = getGroundY(); curZ = hZ; curYaw = 0f; velX = 0f; velY = 0f; velZ = 0f; visPitch = 0f; visRoll = 0f; zoomFactor = 1.5f
        onFlightDataUpdate(curY, 0f, hZ, 0f, 0f, 0f, 0f, false)
    }
}
