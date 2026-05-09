package com.horizon.caadronesimulator.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.horizon.caadronesimulator.model.DroneRegistry
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class DroneSimulationRenderer(private val onFlightDataUpdate: (Float, Float, Float, Float, Float, Float, Float, Boolean, Float, Int, androidx.compose.ui.geometry.Offset?, Float?, Float?, Float?, Float?) -> Unit) : GLSurfaceView.Renderer {
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
    var useSmartObserver = false // [v1.6.0] 智慧觀察員狀態同步
    var sunPosition = 0.5f
    var observerTilt = 0f
    var useSimplifiedMarkers = false
    var showSpecialTitle = false
    var useFlightLimit = true
    var mainFOV = 45f
    var showGroundAnchor = false
    var batteryVoltage = 4.2f
    var batteryPercent = 100
    var isThrottleHoldActive = true // [v1.5.0] 熄火狀態同步
    var motorRpmFactor = 0f          // [v1.5.0] 馬達轉速比例
    var lastManualTouchTime = 0L      // [v1.6.0] 手動觸發時間同步

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

    // [v1.5.6] 視覺偏移與目標定錨緩衝
    private var smoothedTargetY = 0f
    private var smoothedTargetZ = 0f

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

        // [v1.6.0] 相機導演系統：每幀視覺參數更新
        com.horizon.caadronesimulator.logic.CameraDirector.update(
            curX, curY - getGroundY(), curZ,
            observerHeight, observerTilt, zoomFactor, mainFOV,
            cameraMode, useSmartObserver, lastManualTouchTime, droneType, dt,
            // 這裡傳入一個偽 DroneState，因為我們主要靠回調更新 UI
            com.horizon.caadronesimulator.model.DroneState() 
        )

        // 將導演系統的平滑數據回傳給 UI (解決拉桿還原問題)
        onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, 0f, false, batteryVoltage, batteryPercent, specialTitleScreenPos, 
            com.horizon.caadronesimulator.logic.CameraDirector.smoothedHeight,
            com.horizon.caadronesimulator.logic.CameraDirector.smoothedTilt,
            com.horizon.caadronesimulator.logic.CameraDirector.smoothedZoom,
            com.horizon.caadronesimulator.logic.CameraDirector.smoothedFov
        )

        // 1. 繪製主畫面
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        
        // 使用導演輸出的平滑 FOV 與 Zoom
        val finalFov = if (cameraMode == "FPV 視角") {
            if (droneType == "HEAVY_LIFT") 110f else 85f
        } else {
            com.horizon.caadronesimulator.logic.CameraDirector.smoothedFov
        }
        Matrix.perspectiveM(pMatrix, 0, finalFov / com.horizon.caadronesimulator.logic.CameraDirector.smoothedZoom, viewWidth.toFloat() / viewHeight, 1.0f, 1000f)
        
        // 由導演系統計算視圖矩陣
        com.horizon.caadronesimulator.logic.CameraDirector.computeMainViewMatrix(
            vMatrix, cameraMode, curX, curY, curZ, curYaw,
            curX + velX * 0.12f, curZ + velZ * 0.12f, cameraTilt, droneType
        )
        
        System.arraycopy(pMatrix, 0, mainPMatrix, 0, 16)
        System.arraycopy(vMatrix, 0, mainVMatrix, 0, 16)
        calculateProjectedTitlePos()
        renderScene()

        // 2. 繪製子畫面 (PiP) - FPV 視角
        pipRect?.let { rect ->
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            val glY = viewHeight - rect.bottom
            GLES20.glScissor(rect.left, glY, rect.width(), rect.height())
            GLES20.glViewport(rect.left, glY, rect.width(), rect.height())
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            val pipFov = if (droneType == "HEAVY_LIFT") 110f else 85f
            Matrix.perspectiveM(pMatrix, 0, pipFov, rect.width().toFloat() / rect.height(), 0.5f, 500f)
            com.horizon.caadronesimulator.logic.CameraDirector.computeMainViewMatrix(
                vMatrix, "FPV 視角", curX, curY, curZ, curYaw,
                curX + velX * 0.12f, curZ + velZ * 0.12f, cameraTilt, droneType
            )
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
            
            // 使用導演系統計算的動態姿態 FOV
            val dynamicFov = com.horizon.caadronesimulator.logic.CameraDirector.smoothedZoomPipFov
            Matrix.perspectiveM(pMatrix, 0, dynamicFov, rect.width().toFloat() / rect.height(), 0.1f, 1000f)
            
            // [v1.6.0修正] 姿態輔助採用「精準模式」：100% 鎖定飛機中心，無視主畫面構圖偏移
            com.horizon.caadronesimulator.logic.CameraDirector.computePrecisionViewMatrix(
                vMatrix, curX, curY, curZ
            )
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
        DroneRenderer.drawActiveDrone(posH, colorH, mvpH, mvpMatrix, droneType, curX, curY, curZ, curYaw, visPitch, visRoll, flightTime, isMotorLocked, motorRpmFactor)
    }

    private fun getCurrentWindVector(): FloatArray {
        return com.horizon.caadronesimulator.logic.WindManager.calculateWindVector(
            windLevel, windDirection, windVariation.toInt(), windDirVariation.toInt(), flightTime, randomDirAngle
        )
    }

    private fun updatePhysics(dt: Float, groundY: Float) {
        val mass = DroneRegistry.getActiveMass(droneType, applyPhysicalSpecs)
        val damping = DroneRegistry.getActiveDamping(droneType, applyPhysicalSpecs)
        val spec = DroneRegistry.getSpec(droneType)

        // [v1.5.0] 專業直昇機/多旋翼動力狀態機
        val isHeli = spec.category == com.horizon.caadronesimulator.model.DroneCategory.HELI
        val isPowerOn = !isMotorLocked && !isThrottleHoldActive
        
        if (isPowerOn) {
            // 軟啟動：1.5 秒達到滿轉
            motorRpmFactor = (motorRpmFactor + dt / 1.5f).coerceIn(0f, 1f)
        } else {
            if (isHeli && curY > groundY + 1.0f) {
                // [自轉物理] 在空中熄火時，根據下墜速度與螺距(ctrlThrottle)維持轉速
                val descentFactor = (-velY / 15.0f).coerceIn(0f, 1f)
                // 若螺距低（阻力小），轉速下降較慢；若螺距大，轉速消耗快
                val pitchDrag = (ctrlThrottle + 0.2f).coerceIn(0.1f, 1.2f)
                val decay = (dt / 8.0f) * pitchDrag // 自然衰減較慢 (8秒)
                val windCharge = descentFactor * dt * 0.15f // 下坠氣流帶動旋翼
                
                motorRpmFactor = (motorRpmFactor - decay + windCharge).coerceIn(0.1f, 1f)
            } else {
                // 停機：1.0 秒完全停轉
                motorRpmFactor = (motorRpmFactor - dt / 1.0f).coerceIn(0f, 1f)
            }
        }

        if (!isMotorLocked) {
            flightTime += dt
            
            val prevVelY = velY
            
            // [v1.3.7] 硬核物理：垂直氣墊效應 (Ground Effect)
            var geLift = 0f
            val tiltRad = max(abs(visPitch), abs(visRoll)) * (Math.PI.toFloat() / 180f)
            val tiltOffset = spec.collisionRadius * sin(tiltRad)
            val effectiveBottom = curY - groundY - tiltOffset
            
            if (useHardcorePhysics && effectiveBottom < 0.6f && effectiveBottom > 0f) {
                val geFactor = (1.0f - (effectiveBottom / 0.6f)).coerceIn(0f, 1f)
                geLift = (9.8f * 0.2f) * geFactor
            }

            // [核心自轉升力] 最終動力輸出由 motorRpmFactor 決定 (即使 PowerOff 仍有慣性升力)
            val liftEfficiency = if (isHeli) motorRpmFactor else (if(isPowerOn) motorRpmFactor else 0f)
            val effectiveThrottle = ctrlThrottle * liftEfficiency
            val power = DroneRegistry.getActivePower(droneType, applyPhysicalSpecs)
            velY += (((effectiveThrottle * 8.0f) - velY) * (5.0f / mass) + geLift) * dt
            
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
                onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, reportedSpeed, isImpact, batteryVoltage, batteryPercent, specialTitleScreenPos, null, null, null, null)
            } else { 
                velX = 0f; velZ = 0f
                if (!isImpact) reportedSpeed = abs(velY)
                onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, reportedSpeed, isImpact, batteryVoltage, batteryPercent, specialTitleScreenPos, null, null, null, null)
            }
        } else { 
            curY = groundY; velX = 0f; velY = 0f; velZ = 0f
            onFlightDataUpdate(curY, curX, curZ, curYaw, visPitch, visRoll, 0f, false, batteryVoltage, batteryPercent, specialTitleScreenPos, null, null, null, null)
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


    fun updateControls(yaw: Float, throttle: Float, roll: Float, pitch: Float) { ctrlYaw = yaw; ctrlThrottle = throttle; ctrlRoll = roll; ctrlPitch = pitch }
    private fun getGroundY(): Float = DroneRegistry.getSpec(droneType).groundOffset
    private fun loadShader(type: Int, code: String): Int = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, code); GLES20.glCompileShader(it) }

    fun resetFlight() { 
        curX = 0f; curY = getGroundY(); curZ = hZ; curYaw = 0f; velX = 0f; velY = 0f; velZ = 0f; visPitch = 0f; visRoll = 0f; flightTime = 0f
        batteryPercent = 100; batteryVoltage = 4.2f
        motorRpmFactor = 0f // [v1.5.0] 重置轉速
        smoothedTargetY = curY; smoothedTargetZ = hZ // [v1.5.6] 初始化視覺緩衝
        randomDirAngle = (0..359).random().toFloat() // 隨機風向重置
        onFlightDataUpdate(curY, 0f, hZ, 0f, 0f, 0f, 0f, false, 4.2f, 100, null, null, null, null, null)
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
