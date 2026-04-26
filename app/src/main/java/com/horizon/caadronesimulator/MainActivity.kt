package com.horizon.caadronesimulator

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import kotlin.math.*
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.horizon.caadronesimulator.audio.DroneSoundManager
import com.horizon.caadronesimulator.model.Constants
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.SettingsManager
import com.horizon.caadronesimulator.render.DroneSimulationRenderer
import com.horizon.caadronesimulator.ui.DroneHUD
import androidx.compose.foundation.gestures.detectTransformGestures
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var droneState by mutableStateOf(DroneState())
    private var showSplash by mutableStateOf(true)
    private lateinit var renderer: DroneSimulationRenderer
    private lateinit var soundManager: DroneSoundManager
    private lateinit var settingsManager: SettingsManager
    private val uiHandler = Handler(Looper.getMainLooper())
    private val axisSnapshots = mutableMapOf<Int, Float>()
    private var lastResetTime = 0L

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun updateSystemUI() {
        if (droneState.showStatusBar) showSystemUI() else hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        settingsManager = SettingsManager(this)
        
        droneState = settingsManager.loadSettings(DroneState())
        updateSystemUI()

        soundManager = DroneSoundManager()
        soundManager.start()

        renderer = DroneSimulationRenderer { alt, x, z, yaw, pitch, roll, speed, isImpact ->
            uiHandler.post {
                val current = droneState
                
                // 1. 如果處於重置保護期 (0.5秒)，忽略所有數據更新中的碰撞判定
                val isProtecting = (System.currentTimeMillis() - lastResetTime < 500)
                
                // 2. 如果目前正在顯示設定視窗或已撞毀，則定格狀態不更新
                if ((current.showSettings || current.isCollision) && !isProtecting) return@post

                // 3. 整合碰撞判定
                val collisionTriggered = isImpact || checkCollision(alt, x, z, pitch, roll)

                if (collisionTriggered && !isProtecting) {
                    droneState = current.copy(
                        isCollision = true,
                        isMotorLocked = true,
                        infoMessage = "已撞毀",
                        altitude = alt, 
                        posX = x, 
                        posZ = z, 
                        speed = speed
                    )
                } else {
                    val spec = com.horizon.caadronesimulator.model.DroneRegistry.getSpec(current.droneType)
                    val heightAboveGround = alt - spec.groundOffset
                    val isAtMaxAlt = heightAboveGround >= 29.99f
                    val newInfoMessage = if (isAtMaxAlt && current.infoMessage == null) "已達限高 30m" else current.infoMessage

                    droneState = current.copy(
                        altitude = alt, posX = x, posZ = z,
                        yaw = yaw, pitch = pitch, roll = roll,
                        speed = speed,
                        isCollision = false,
                        infoMessage = newInfoMessage
                    )
                }
            }
        }

        setContent {
            LaunchedEffect(
                droneState.joystickMode,
                droneState.droneType,
                droneState.halfThrottle,
                droneState.joystickDeadzone,
                droneState.windLevel,
                droneState.windDirection,
                droneState.windVariation,
                droneState.windDirVariation,
                droneState.timeOfDay,
                droneState.isMuted,
                droneState.showShadow,
                droneState.zoomFactor,
                droneState.cameraMode,
                droneState.showStatusBar,
                droneState.showVirtualJoysticks,
                droneState.mappingLY,
                droneState.mappingLX,
                droneState.mappingRY,
                droneState.mappingRX,
                droneState.showTutorial
            ) {
                settingsManager.saveSettings(droneState)
            }

            LaunchedEffect(droneState.showStatusBar) {
                updateSystemUI()
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val delta = event.changes.first().scrollDelta.y
                                    val newZoom = (droneState.zoomFactor - delta * 0.1f).coerceIn(0.5f, 5.0f)
                                    droneState = droneState.copy(zoomFactor = newZoom)
                                }
                            }
                        }
                    }.pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) {
                                val newZoom = (droneState.zoomFactor * zoom).coerceIn(0.5f, 5.0f)
                                droneState = droneState.copy(zoomFactor = newZoom)
                            }
                        }
                    }) {
                        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                setEGLContextClientVersion(2)
                                setRenderer(renderer)
                                isFocusable = true
                                isFocusableInTouchMode = true
                                requestFocus()
                                setOnGenericMotionListener { _, event ->
                                    if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                                        val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                        if (scroll != 0f) {
                                            val newZoom = (droneState.zoomFactor + scroll * 0.15f).coerceIn(0.5f, 5.0f)
                                            droneState = droneState.copy(zoomFactor = newZoom)
                                            return@setOnGenericMotionListener true
                                        }
                                    }
                                    false
                                }
                            }
                        }, update = {
                            val isPaused = (droneState.showSettings && droneState.pauseInSettings) || droneState.isCollision
                            
                            renderer.isPaused = isPaused
                            renderer.applyPhysicalSpecs = droneState.applyPhysicalSpecs
                            renderer.isMotorLocked = droneState.isMotorLocked
                            renderer.showShadow = droneState.showShadow
                            renderer.shadowIntensity = droneState.shadowIntensity
                            renderer.showObstacles = droneState.showObstacles
                            renderer.droneType = droneState.droneType
                            renderer.windLevel = droneState.windLevel
                            renderer.windDirection = droneState.windDirection
                            renderer.windVariation = droneState.windVariation.toFloat()
                            renderer.windDirVariation = droneState.windDirVariation.toFloat()
                            renderer.timeOfDay = droneState.timeOfDay
                            renderer.cameraMode = droneState.cameraMode
                            renderer.zoomFactor = droneState.zoomFactor
                            renderer.cameraTilt = droneState.cameraTilt
                            
                            if (isPaused) {
                                renderer.updateControls(yaw = 0f, throttle = -1f, roll = 0f, pitch = 0f)
                                soundManager.update(isLocked = true, throttle = -1f, speed = 0f, windLevel = 0, isMuted = true)
                            } else {
                                renderer.updateControls(yaw = droneState.stickYaw, throttle = droneState.stickThrottle, roll = droneState.stickRoll, pitch = droneState.stickPitch)
                                soundManager.update(isLocked = droneState.isMotorLocked, throttle = droneState.stickThrottle, speed = droneState.speed, windLevel = droneState.windLevel, isMuted = droneState.isMuted)
                            }
                        })
                        DroneHUD(
                            state = droneState, 
                            onUpdateState = { transform -> droneState = transform(droneState) }, 
                            onUpdatePipRect = { rect -> renderer.pipRect = rect },
                            onReset = { 
                            lastResetTime = System.currentTimeMillis()
                            renderer.resetFlight()
                            droneState = droneState.copy(isCollision = false, isMotorLocked = true, zoomFactor = 1.5f) 
                        })
                    }
                    if (showSplash) SplashScreen(onTimeout = { showSplash = false })
                }
            }
        }
    }

    @Composable
    private fun SplashScreen(onTimeout: () -> Unit) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true; delay(2000); visible = false; delay(600); onTimeout() }
        val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(durationMillis = 800), label = "splashAlpha")
        val nikoGreen = Color(0xFF1B4332)
        
        Box(modifier = Modifier.fillMaxSize().background(Color.White).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha)) {
                // Simplified NI Logo representation
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "NI", color = nikoGreen, fontSize = 80.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-5).sp)
                    // Smile curve
                    Canvas(modifier = Modifier.size(40.dp, 10.dp)) {
                        drawArc(
                            color = nikoGreen,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Three checkmarks
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        Text("✓", color = nikoGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(text = "Niko", color = nikoGreen, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(text = "無人機考場模擬", color = nikoGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Professional Drone Training System", color = nikoGreen.copy(alpha = 0.6f), fontSize = 14.sp, letterSpacing = 1.sp)
            }
        }
    }

    private fun checkCollision(alt: Float, x: Float, z: Float, pitch: Float, roll: Float): Boolean {
        val spec = com.horizon.caadronesimulator.model.DroneRegistry.getSpec(droneState.droneType)
        val collisionRadius = spec.collisionRadius
        
        // 1. 地面碰撞 (考慮傾斜)
        // 當機身傾斜時，螺旋槳或機臂會先觸地。
        // 門檻：中心高度 - (半徑 * sin(傾角)) < 基礎高度的一半 且 傾角大於 15 度
        val maxTilt = max(abs(pitch), abs(roll))
        val tiltRad = maxTilt * (PI.toFloat() / 180f)
        val tiltBottomOffset = collisionRadius * sin(tiltRad)
        
        // 如果傾斜觸地，且傾角過大 (非正常著陸)
        if (alt - tiltBottomOffset < 0.05f && maxTilt > 15f) return true
        
        val coneHeight = 0.8f
        val bottomAlt = alt - spec.groundOffset
        for (cone in Constants.CONE_POSITIONS) {
            val dx = x - cone[0]
            val dz = z - cone[1]
            val distSq = dx * dx + dz * dz
            // 錐桶碰撞也考慮傾斜半徑
            if (distSq < (collisionRadius * 1.2f).pow(2) && bottomAlt < coneHeight) return true
        }

        // 障礙物碰撞檢查 (僅在開啟時)
        if (droneState.showObstacles) {
            val obstacles = arrayOf(
                floatArrayOf(-32f, 10f, 13f, 3.0f), // 大建築
                floatArrayOf(-28f, 25f, 8f, 2.5f),  // 樹A
                floatArrayOf(32f, 5f, 10f, 2.5f),   // 中建築
                floatArrayOf(28f, -5f, 7f, 2.5f),   // 樹B
                floatArrayOf(35f, 20f, 11f, 2.5f)   // 樹A
            )

            for (obs in obstacles) {
                val dx = x - obs[0]; val dz = z - obs[1]
                val distSq = dx * dx + dz * dz
                val obsRadius = obs[3]; val obsHeight = obs[2]
                if (distSq < (collisionRadius + obsRadius).pow(2.0f) && alt < obsHeight) return true
            }
        }

        if (abs(x) > 35f || z < -13f || z > 30f) return true
        // 基礎跌落檢查 (垂直高度過低)
        return alt < spec.groundOffset * 0.5f && maxTilt > 10f
    }

    private val axisList = (0..28).toList()
    private fun getAxisLabel(axis: Int): String = "Axis $axis"

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0) return onGenericMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) == 0 && event.actionMasked != MotionEvent.ACTION_SCROLL) return false
        
        if (event.action == MotionEvent.ACTION_MOVE) {
            var activeAx = "NONE"
            var maxChange = 0f
            for (axis in axisList) {
                if (axis == 13 || axis == 22 || axis == 23) continue 
                val currentVal = event.getAxisValue(axis)
                val initialVal = axisSnapshots[axis] ?: 0f
                val change = abs(currentVal - initialVal)
                if (change > 0.15f && change > maxChange) {
                    maxChange = change
                    activeAx = getAxisLabel(axis)
                }
            }
            if (droneState.activeAxisLabel != activeAx) {
                droneState = droneState.copy(activeAxisLabel = activeAx)
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val scroll = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (scroll != 0f) {
                val newZoom = (droneState.zoomFactor + scroll * 0.1f).coerceIn(0.5f, 5.0f)
                droneState = droneState.copy(zoomFactor = newZoom)
                return true
            }
        }
        val isJoystick = (event.source and InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK
        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
            if (droneState.setupWizardStep > 0 && droneState.wizardWaitingForNeutral) return true

            var activeAx = "NONE"
            var maxV = 0f
            for (axis in axisList) {
                if (axis == 13 || axis == 22 || axis == 23) continue 
                val v = abs(event.getAxisValue(axis))
                if (v > 0.15f && v > maxV) {
                    maxV = v
                    activeAx = getAxisLabel(axis)
                }
            }
            if (droneState.activeAxisLabel != activeAx) {
                droneState = droneState.copy(activeAxisLabel = activeAx)
            }

            if (droneState.setupWizardStep > 0) {
                if (axisSnapshots.isEmpty()) {
                    for (axis in axisList) axisSnapshots[axis] = event.getAxisValue(axis)
                    return true
                }
                
                var triggeredAxis = -1
                var moveVal = 0f
                for (axis in axisList) {
                    if (axis == 13 || axis == 22 || axis == 23) continue
                    val initial = axisSnapshots[axis] ?: 0f
                    val current = event.getAxisValue(axis)
                    val delta = current - initial
                    if (abs(delta) > 0.5f) {
                        triggeredAxis = axis
                        moveVal = delta
                        break
                    }
                }
                
                if (triggeredAxis != -1) {
                    val step = droneState.setupWizardStep
                    val isYStep = (step == 1 || step == 3)
                    val logicValue = if (isYStep) -moveVal else moveVal
                    
                    val mapping = com.horizon.caadronesimulator.model.ChannelMapping(
                        axis = triggeredAxis,
                        inverted = logicValue < 0,
                        label = getAxisLabel(triggeredAxis)
                    )
                    
                    droneState = when(step) {
                        1 -> droneState.copy(mappingLY = mapping, wizardWaitingForNeutral = true)
                        2 -> droneState.copy(mappingLX = mapping, wizardWaitingForNeutral = true)
                        3 -> droneState.copy(mappingRY = mapping, wizardWaitingForNeutral = true)
                        4 -> droneState.copy(mappingRX = mapping, wizardWaitingForNeutral = true)
                        else -> droneState.copy(setupWizardStep = 0)
                    }
                    axisSnapshots.clear()
                }
                return true
            }

            droneState.isAutoBinding?.let { bindingKey ->
                if (axisSnapshots.isEmpty()) { 
                    for (axis in axisList) axisSnapshots[axis] = event.getAxisValue(axis)
                    return true 
                }
                var triggeredAxis = -1
                for (axis in axisList) {
                    if (axis == 13 || axis == 22 || axis == 23) continue
                    val initialValue = axisSnapshots[axis] ?: 0f
                    val currentValue = event.getAxisValue(axis)
                    if (abs(currentValue - initialValue) > 0.5f) { 
                        triggeredAxis = axis; break 
                    }
                }
                if (triggeredAxis != -1) {
                    val newMapping = com.horizon.caadronesimulator.model.ChannelMapping(axis = triggeredAxis, inverted = false, label = getAxisLabel(triggeredAxis))
                    droneState = when(bindingKey) {
                        "ly" -> droneState.copy(mappingLY = newMapping, isAutoBinding = null)
                        "lx" -> droneState.copy(mappingLX = newMapping, isAutoBinding = null)
                        "ry" -> droneState.copy(mappingRY = newMapping, isAutoBinding = null)
                        "rx" -> droneState.copy(mappingRX = newMapping, isAutoBinding = null)
                        else -> droneState.copy(isAutoBinding = null)
                    }
                    axisSnapshots.clear()
                }
                return true
            }
            if (axisSnapshots.isNotEmpty()) axisSnapshots.clear()

            if (droneState.isCalibrating) {
                fun updateCalib(m: com.horizon.caadronesimulator.model.ChannelMapping): com.horizon.caadronesimulator.model.ChannelMapping {
                    if (m.axis == -1) return m
                    val v = event.getAxisValue(m.axis)
                    return when(droneState.calibrationStep) {
                        1 -> m.copy(center = v, min = v, max = v)
                        2 -> {
                            // 增加死區忽略，防止微小抖動擴大 min/max
                            val newMin = if (v < m.min) v else m.min
                            val newMax = if (v > m.max) v else m.max
                            m.copy(min = newMin, max = newMax)
                        }
                        else -> m
                    }
                }
                droneState = droneState.copy(mappingLY = updateCalib(droneState.mappingLY), mappingLX = updateCalib(droneState.mappingLX), mappingRY = updateCalib(droneState.mappingRY), mappingRX = updateCalib(droneState.mappingRX))
            }

            val dz = droneState.joystickDeadzone
            fun getRawPhysicalValue(m: com.horizon.caadronesimulator.model.ChannelMapping, defAxis: Int, isYAxis: Boolean = false): Float {
                val axis = if (m.axis != -1) m.axis else defAxis
                if (axis == -1) return 0f
                val raw = event.getAxisValue(axis)
                var value = if (m.axis != -1 && abs(m.max - m.min) > 0.2f) {
                    val center = m.center
                    if (raw >= center) { if (m.max > center) (raw - center) / (m.max - center) else 0f } 
                    else { if (center > m.min) (raw - center) / (center - m.min) else 0f }
                } else raw
                if (m.axis == -1 && (axis == MotionEvent.AXIS_RTRIGGER || axis == MotionEvent.AXIS_LTRIGGER)) value = raw * 2f - 1f
                
                if (isYAxis) value = -value
                
                value = value.coerceIn(-1f, 1f)
                value = when { value > dz -> (value - dz) / (1f - dz); value < -dz -> (value + dz) / (1f - dz); else -> 0f }
                
                return value 
            }

            val (dLY, dLX, dRY, dRX) = listOf(MotionEvent.AXIS_Y, MotionEvent.AXIS_X, MotionEvent.AXIS_RZ, MotionEvent.AXIS_Z)
            
            val rawLY = getRawPhysicalValue(droneState.mappingLY, dLY, isYAxis = true)
            val rawLX = getRawPhysicalValue(droneState.mappingLX, dLX)
            val rawRY = getRawPhysicalValue(droneState.mappingRY, dRY, isYAxis = true)
            val rawRX = getRawPhysicalValue(droneState.mappingRX, dRX)

            if (droneState.isCollision) return true

            droneState = droneState.copy(
                rawLY = rawLY, rawLX = rawLX, rawRY = rawRY, rawRX = rawRX,
                controllerConnected = true
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() { super.onDestroy(); if (::soundManager.isInitialized) soundManager.stop() }
}
