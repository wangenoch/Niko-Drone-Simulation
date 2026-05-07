package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.horizon.caadronesimulator.model.DroneRegistry
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class DroneSimulationRenderer(private val onFlightDataUpdate: (Float, Float, Float, Float, Float, Float, Float, Boolean, Float, Int, androidx.compose.ui.geometry.Offset?) -> Unit) : GLSurfaceView.Renderer {
    private var program = 0
    private val vMatrix = FloatArray(16)
    private val pMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    // [v1.3.7] 矩陣狀態隔離：用於全螢幕特效對齊
    private val mainVMatrix = FloatArray(16)
    private val mainPMatrix = FloatArray(16)
    
    private var viewWidth = 0
    private var viewHeight = 0
    private var lastFrameTime = 0L
    
    // 飛行狀態
    private var curX = 0f; private var curY = 0.05f; private var curZ = -6.0f; private var curYaw = 0f
    private var velX = 0f; private var velY = 0f; private var velZ = 0f
    private var visPitch = 0f; private var visRoll = 0f
    private var flightTime = 0f
    
    private val sunRenderer = SunRenderer()
    
    // 外部控制參數
    var ctrlThrottle = 0f; var ctrlYaw = 0f; var ctrlPitch = 0f; var ctrlRoll = 0f
    var isMotorLocked = true; var droneType = "QUAD_STANDARD"
    var cameraMode = "站位視角 (追蹤)"; var zoomFactor = 1.5f; var cameraTilt = 0f; var observerHeight = 6.0f
    var windLevel = 0; var windDirection = "無"; var windVariation = 0f; var windDirVariation = 0f; var timeOfDay = "中午"
    var showShadow = true
    var shadowIntensity = 0.5f
    var showObstacles = false
    var isPaused = false
    var applyPhysicalSpecs = false
    var enableVerticalDraft = false
    var useHardcorePhysics = false
    var isSunSimEnabled = false
    var sunPosition = 0.5f
    var observerTilt = 0f
    var useSimplifiedMarkers = false
    var showSpecialTitle = false
    var useFlightLimit = true
    var mainFOV = 45f
    var showGroundAnchor = false
    var batteryVoltage = 4.2f
    var batteryPercent = 100

    // 子畫面 (PiP) 參數
    var pipRect: android.graphics.Rect? = null
    var zoomPipRect: android.graphics.Rect? = null

    private var randomWindPhase = 0f
    private var turbulencePhase = 0f
    private var specialTitleScreenPos: androidx.compose.ui.geometry.Offset? = null
    
    //材質相關
    private var titleTextureId = -1
    private var texH = -1
    private var texCoordH = -1
    private var useTexH = -1
    private var randomDirAngle = 0f
    private var randomDirTarget = 0f
    private var randomDirVelocity = 0f
    
    // [v1.3.7] 旗幟物理慣性
    private var flagVisualAngle = 0f
    private var flagDroopAngle = 0f

    private val hZ = -6.0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
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
            precision mediump float; 
            uniform vec4 vColor; 
            uniform sampler2D uTexture;
            uniform bool uUseTex;
            varying vec2 vTexCoord;
            void main() { 
                if (uUseTex) {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                } else {
                    gl_FragColor = vColor; 
                }
            }
        """.trimIndent()
        
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vShader)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fShader)
        program = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, vs); GLES20.glAttachShader(it, fs); GLES20.glLinkProgram(it) }
        
        // 獲取材質相關變量位置
        texH = GLES20.glGetUniformLocation(program, "uTexture")
        texCoordH = GLES20.glGetAttribLocation(program, "aTexCoord")
        useTexH = GLES20.glGetUniformLocation(program, "uUseTex")

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1.0f, -1.0f)
        
        sunRenderer.init()
        
        // [v1.4.2] 預生成標題材質
        generateTitleTexture()
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

        // [v1.3.7] 更新旗幟物理動畫 (與物理引擎解耦，僅供視覺)
        val windVec = getCurrentWindVector()
        if (windLevel > 0) {
            val targetAngleRad = atan2(-windVec[1].toDouble(), windVec[0].toDouble()).toFloat()
            val targetAngleDeg = Math.toDegrees(targetAngleRad.toDouble()).toFloat()
            val angleDiff = (targetAngleDeg - flagVisualAngle + 540f) % 360f - 180f
            flagVisualAngle += angleDiff * 0.1f
            
            val targetDroop = (windLevel.toFloat() / 5f) * 90f
            flagDroopAngle += (targetDroop - flagDroopAngle) * 0.05f
        } else {
            flagDroopAngle *= 0.95f // 無風垂落
        }

        // 1. 繪製主畫面
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        // [v1.4.0] 加入自定義 FOV 邏輯：站位模式使用 state.mainFOV, FPV 使用固定廣角
        val finalFov = if (cameraMode == "FPV 視角") {
            if (droneType == "HEAVY_LIFT") 110f else 85f
        } else {
            mainFOV // 來自 DroneState 的使用者設定
        }
        Matrix.perspectiveM(pMatrix, 0, finalFov / zoomFactor, viewWidth.toFloat() / viewHeight, 1.0f, 1000f)
        updateCameraMatrix(cameraMode)
        
        // 儲存主畫面矩陣快照，供後續耀光特效與座標投影使用
        System.arraycopy(pMatrix, 0, mainPMatrix, 0, 16)
        System.arraycopy(vMatrix, 0, mainVMatrix, 0, 16)

        // [v1.4.1] 計算特殊標題的投影位置 (Z=-17.0, X=0.0)
        calculateProjectedTitlePos()

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

        // 3. 繪製姿態輔助 (Zoom PiP)
        zoomPipRect?.let { rect ->
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            val glY = viewHeight - rect.bottom
            GLES20.glScissor(rect.left, glY, rect.width(), rect.height())
            GLES20.glViewport(rect.left, glY, rect.width(), rect.height())
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            // 計算動態 FOV 使其看起來像在 2 公尺處 (高倍率姿態觀察)
            // [v1.3.9] 觀察點固定在新站位線 (0, observerHeight, -15)
            val distToCam = sqrt(curX.pow(2) + (observerHeight - curY).pow(2) + (curZ + 15f).pow(2))
            val dynamicFov = (120f / distToCam).coerceIn(3f, 45f)
            
            Matrix.perspectiveM(pMatrix, 0, dynamicFov, rect.width().toFloat() / rect.height(), 0.1f, 1000f)
            updateCameraMatrix("姿態輔助視角")
            renderScene()
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        }

        // 4. [v1.3.7] 全螢幕耀光特效 (最後渲染)
        if (isSunSimEnabled) {
            // 使用主畫面矩陣快照，確保耀光位置正確對齊
            sunRenderer.drawLensFlare(mainPMatrix, mainVMatrix, sunPosition)
        }
    }

    private fun renderScene() {
        Matrix.multiplyMM(mvpMatrix, 0, pMatrix, 0, vMatrix, 0)
        GLES20.glUseProgram(program)
        val posH = GLES20.glGetAttribLocation(program, "vPosition")
        val colorH = GLES20.glGetUniformLocation(program, "vColor")
        val mvpH = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        
        // 預設關閉材質使用
        GLES20.glUniform1i(useTexH, 0)

        // [v1.3.7] 太陽主體繪製 (歸入 Scene 渲染，解決 PiP 衝突)
        if (isSunSimEnabled) {
            sunRenderer.drawSun(pMatrix, vMatrix, sunPosition)
            GLES20.glUseProgram(program)
        }
        
        // [v1.4.2 標題渲染] 
        // 關鍵修正：將材質開關邏輯封裝在 FieldRenderer 內部以防污染
        FieldRenderer.drawField(posH, colorH, mvpH, mvpMatrix, windLevel, windDirection, flagVisualAngle, flightTime, showObstacles, isSunSimEnabled, sunPosition, useSimplifiedMarkers,
            if(showSpecialTitle) titleTextureId else -1, texH, texCoordH, useTexH)
        
        // [v1.4.0] 繪製地面位置投影 (AR Anchor)
        ArAnchorRenderer.drawAnchor(mvpMatrix, curX, curY, curZ, 0f, posH, colorH, mvpH, showGroundAnchor)

        DroneRenderer.drawDroneShadow(posH, colorH, mvpH, mvpMatrix, droneType, curX, curY, curZ, timeOfDay, showShadow, shadowIntensity, isSunSimEnabled, sunPosition)
        DroneRenderer.drawActiveDrone(posH, colorH, mvpH, mvpMatrix, droneType, curX, curY, curZ, curYaw, visPitch, visRoll, flightTime, isMotorLocked)
    }

    private fun getCurrentWindVector(): FloatArray {
        return com.horizon.caadronesimulator.logic.WindManager.calculateWindVector(
            windLevel, windDirection, windVariation.toInt(), windDirVariation.toInt(), flightTime, randomDirAngle
        )
    }

    private fun updatePhysics(dt: Float, groundY: Float) {
        val mass = DroneRegistry.getActiveMass(droneType, applyPhysicalSpecs)
        val power = DroneRegistry.getActivePower(droneType, applyPhysicalSpecs)
        val damping = DroneRegistry.getActiveDamping(droneType, applyPhysicalSpecs)

        if (!isMotorLocked) {
            flightTime += dt
            
            val prevVelY = velY
            
            // [v1.3.7] 硬核物理：垂直氣墊效應 (Ground Effect)
            var geLift = 0f
            val spec = DroneRegistry.getSpec(droneType)
            val tiltRad = max(abs(visPitch), abs(visRoll)) * (Math.PI.toFloat() / 180f)
            val tiltOffset = spec.collisionRadius * sin(tiltRad)
            val effectiveBottom = curY - groundY - tiltOffset
            
            if (useHardcorePhysics && effectiveBottom < 0.6f && effectiveBottom > 0f) {
                // 離地越近，下洗氣流產生的反向升力越強
                val geFactor = (1.0f - (effectiveBottom / 0.6f)).coerceIn(0f, 1f)
                geLift = (9.8f * 0.2f) * geFactor // 最大抵消 20% 重力加速度
            }

            velY += (((ctrlThrottle * 8.0f) - velY) * (5.0f / mass) + geLift) * dt
            
            var nextY = curY + velY * dt
            val maxAlt = groundY + 30.0f
            if (nextY > maxAlt) { nextY = maxAlt; velY = 0f }

            var isImpact = false
            var reportedSpeed = 0f

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
                applyWindForces(dt, mass, groundY)
                velX *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
                velZ *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
                curX += velX * dt; curZ += velZ * dt
                
                // [v1.3.9] 電池耗電模擬邏輯
                if (useFlightLimit) {
                    val totalSecs = 900f // 15分鐘
                    val warnSecs = 780f  // 13分鐘
                    
                    if (flightTime >= totalSecs) {
                        batteryPercent = 0
                        batteryVoltage = 3.4f
                        // 抵達 15 分鐘強制墜落
                        isMotorLocked = true
                    } else {
                        // 非線性模擬：前 13 分消耗 70%，後 2 分消耗 30%
                        if (flightTime < warnSecs) {
                            val ratio = flightTime / warnSecs
                            batteryPercent = (100 - (ratio * 70)).toInt()
                            batteryVoltage = 4.2f - (ratio * 0.6f) // 4.2 -> 3.6
                        } else {
                            val ratio = (flightTime - warnSecs) / (totalSecs - warnSecs)
                            batteryPercent = (30 - (ratio * 30)).toInt()
                            batteryVoltage = 3.6f - (ratio * 0.2f) // 3.6 -> 3.4
                        }
                    }
                } else {
                    batteryPercent = 100
                    batteryVoltage = 4.2f
                }

                if (!isImpact) reportedSpeed = sqrt(velX * velX + velY * velY + velZ * velZ)
                onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, reportedSpeed, isImpact, batteryVoltage, batteryPercent, specialTitleScreenPos)
            } else { 
                velX = 0f; velZ = 0f
                if (!isImpact) reportedSpeed = abs(velY)
                onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, reportedSpeed, isImpact, batteryVoltage, batteryPercent, specialTitleScreenPos)
            }
        } else { 
            curY = groundY; velX = 0f; velY = 0f; velZ = 0f
            onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, 0f, false, batteryVoltage, batteryPercent, specialTitleScreenPos)
        }
    }

    private fun updateEnvironmentLighting() {
        if (isSunSimEnabled) {
            // 太陽模擬模式下的動態天空色
            val angle = Math.toRadians((sunPosition * 180f).toDouble()).toFloat()
            val s = sin(angle)
            val r = (1.0f - (1.0f - 0.53f) * s).coerceIn(0.53f, 1.0f)
            val g = (0.6f + 0.21f * s).coerceIn(0.6f, 0.81f)
            val b = (0.3f + 0.62f * s).coerceIn(0.3f, 0.92f)
            GLES20.glClearColor(r, g, b, 1.0f)
        } else {
            when(timeOfDay) {
                "早晨" -> GLES20.glClearColor(1f, 0.7f, 0.5f, 1.0f)
                "中午" -> GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
                "下午" -> GLES20.glClearColor(1f, 0.6f, 0.3f, 1.0f)
                else -> GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
            }
        }
    }

    private var windChangeTimer = 0f
    private fun applyWindForces(dt: Float, mass: Float, groundY: Float) {
        if (windLevel <= 0 || windDirection == "無") return
        
        // [v1.3.7] 硬核物理：高度感應風切 (Log Wind Profile)
        var effectiveWindLevel = windLevel.toFloat()
        if (useHardcorePhysics) {
            effectiveWindLevel *= com.horizon.caadronesimulator.logic.WindManager.calculateHeightFactor(curY, groundY)
        }

        // [v1.3.7] 硬核物理：姿態感應受風面積 (Attitude-Based Drag)
        var areaFactor = 1.0f
        if (useHardcorePhysics) {
            val tilt = max(abs(visPitch), abs(visRoll))
            areaFactor = 1.0f + (tilt / 45f) * 0.8f // 傾斜 45 度時受風面積增加 80%
        }

        val beaufortFactor = (effectiveWindLevel.pow(1.5f)) * 0.45f
        val dragArea = when { mass > 2.5f -> 1.8f; mass > 1.5f -> 1.4f; else -> 1.0f }
        val windBase = (beaufortFactor * dragArea * areaFactor) / mass
        
        randomWindPhase += dt * (1.2f + windVariation * 0.6f)
        
        // [v1.3.7] 馮·卡門高頻湍流模擬 (與陣風計算整合)
        val gust = com.horizon.caadronesimulator.logic.WindManager.calculateGust(
            windVariation.toInt(), randomWindPhase, flightTime, windLevel, useHardcorePhysics
        )
        
        val wVec = getCurrentWindVector()
        velX += windBase * wVec[0] * gust * dt
        velZ += windBase * wVec[1] * gust * dt

        // [v1.3.5] 垂直氣流 (Vertical Draft) 智慧模擬系統
        if (enableVerticalDraft && windLevel > 0) {
            // 使用非同步相位，模擬不可預測的上升/下沉氣流
            turbulencePhase += dt * (0.8f + windVariation * 0.4f)
            
            // 基礎波：結合多個正弦波產生隨機感
            val noise = sin(turbulencePhase) * 0.7f + sin(turbulencePhase * 1.6f) * 0.3f
            
            // 智慧門檻判定：只有當雜訊強度超過一定值時才觸發「突發氣流」
            // 觸發門檻隨「激烈度 (windVariation)」動態調整
            val threshold = 0.65f - (windVariation * 0.05f) 
            val absNoise = abs(noise)
            
            if (absNoise > threshold) {
                // 計算氣流力道：隨風級與激烈度增強
                // 模擬上升氣流 (Positive) 與 下沉氣流 (Negative)
                val draftDirection = sign(noise)
                val draftIntensity = (absNoise - threshold) / (1f - threshold)
                
                // 最終加速度補償 (Y 軸)
                // 基礎強度由風級決定，下沉氣流通常比上升氣流更危險，賦予 1.2f 權重
                val baseForce = (windLevel * 0.6f) * (1.0f + windVariation * 0.5f)
                val finalForce = draftDirection * baseForce * draftIntensity * (if (draftDirection < 0) 1.2f else 0.8f)
                
                velY += (finalForce / mass) * dt
            }
        }

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
            "站位視角 (追蹤)" -> {
                // [v1.4.1 修復] 回歸相對追蹤邏輯：以無人機 Y 為基準加上仰角偏移
                // 這樣當 observerTilt = 0 時，無人機保證在螢幕正中央
                val rad = Math.toRadians(observerTilt.toDouble()).toFloat()
                val dist = sqrt(curX * curX + (curZ + 15f) * (curZ + 15f))
                val targetYOffset = tan(rad) * dist
                Matrix.setLookAtM(vMatrix, 0, 0f, observerHeight, -15f, curX, curY + targetYOffset, curZ, 0f, 1f, 0f)
            }
            "站位視角 (固定)" -> {
                // [v1.4.0 修正] 絕對仰角模式
                val rad = Math.toRadians(observerTilt.toDouble()).toFloat()
                val targetY = observerHeight + tan(rad) * 15f
                Matrix.setLookAtM(vMatrix, 0, 0f, observerHeight, -15f, 0f, targetY, 0f, 0f, 1f, 0f)
            }
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
            "姿態輔助視角" -> {
                // 根據當前模式決定「望遠鏡」的架設位置
                when {
                    cameraMode.contains("站位視角") -> {
                        // 從觀察者原位發射的望遠鏡視角
                        Matrix.setLookAtM(vMatrix, 0, 0f, observerHeight, -15f, curX, curY, curZ, 0f, 1f, 0f)
                    }
                    cameraMode == "跟隨視角" -> {
                        // 從跟隨攝影機位置放大的視角
                        val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                        val camX = curX - sin(rad) * 5f; val camZ = curZ - cos(rad) * 5f
                        Matrix.setLookAtM(vMatrix, 0, camX, curY + 2.5f, camZ, curX, curY, curZ, 0f, 1f, 0f)
                    }
                    else -> {
                        // FPV 或其他模式下，預設使用近距離追蹤以觀察機身
                        val rad = Math.toRadians(curYaw.toDouble()).toFloat()
                        val camX = curX - sin(rad) * 2.5f; val camZ = curZ - cos(rad) * 2.5f
                        Matrix.setLookAtM(vMatrix, 0, camX, curY + 1.2f, camZ, curX, curY, curZ, 0f, 1f, 0f)
                    }
                }
            }
            else -> Matrix.setLookAtM(vMatrix, 0, 0f, 6f, -22f, 0f, 0f, 0f, 0f, 1f, 0f)
        }
    }

    fun updateControls(yaw: Float, throttle: Float, roll: Float, pitch: Float) { ctrlYaw = yaw; ctrlThrottle = throttle; ctrlRoll = roll; ctrlPitch = pitch }
    private fun getGroundY(): Float = DroneRegistry.getSpec(droneType).groundOffset
    private fun loadShader(type: Int, code: String): Int = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, code); GLES20.glCompileShader(it) }

    fun resetFlight() { 
        curX = 0f; curY = getGroundY(); curZ = hZ; curYaw = 0f; velX = 0f; velY = 0f; velZ = 0f; visPitch = 0f; visRoll = 0f; flightTime = 0f
        batteryPercent = 100; batteryVoltage = 4.2f
        randomDirAngle = (0..359).random().toFloat() // 隨機風向重置
        onFlightDataUpdate(curY, 0f, hZ, 0f, 0f, 0f, 0f, false, 4.2f, 100, null)
    }

    private fun calculateProjectedTitlePos() {
        if (!showSpecialTitle) {
            specialTitleScreenPos = null
            return
        }
        
        // [v1.4.5 同步修正] 匹配使用者新位置 Z=-9.0
        val worldPos = floatArrayOf(0f, 0.015f, -9.0f, 1.0f)
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, mainPMatrix, 0, mainVMatrix, 0)
        
        val screenPos = FloatArray(4)
        Matrix.multiplyMV(screenPos, 0, mvp, 0, worldPos, 0)
        
        if (screenPos[3] > 0) {
            val ndcX = screenPos[0] / screenPos[3]
            val ndcY = screenPos[1] / screenPos[3]
            
            // 轉換為 Compose 座標系 (0,0 在左上角)
            val x = (ndcX + 1f) / 2f * viewWidth
            val y = (1f - ndcY) / 2f * viewHeight
            specialTitleScreenPos = androidx.compose.ui.geometry.Offset(x, y)
        } else {
            specialTitleScreenPos = null
        }
    }

    private fun generateTitleTexture() {
        // [v1.4.2] 提高材質寬度解析度，適配 15 米的巨型字體
        val bitmap = android.graphics.Bitmap.createBitmap(2048, 256, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 140f // 配合解析度放大字體
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            alpha = 220 
        }
        
        canvas.drawText(com.horizon.caadronesimulator.model.AppConfig.SPECIAL_TITLE, 1024f, 170f, paint)
        
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        titleTextureId = textures[0]
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, titleTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }
}
