package com.horizon.caadronesimulator.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.mission.MissionManager
import com.horizon.caadronesimulator.ui.joystick.JoystickOverlay
import com.horizon.caadronesimulator.ui.instruments.InstrumentsLayer
import com.horizon.caadronesimulator.ui.tutorial.WelcomeTutorial
import com.horizon.caadronesimulator.ui.tutorial.JoystickSettingsTutorial
import com.horizon.caadronesimulator.ui.tutorial.ClimateSettingsTutorial
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.*

/**
 * [v1.2.68] 純粹化飛行儀表層 (Heads-Up Display)
 * 實作建議 1 & 2：視覺限流、組件拆解、移除所有非顯示邏輯。
 */
@Composable
fun DroneHUD(
    state: DroneState,
    stickState: StickInputState,
    isStatusVisible: Boolean,
    tutorialTargets: Map<String, Rect>,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onToggleStatus: () -> Unit,
    onUpdatePipRect: (android.graphics.Rect?) -> Unit = {},
    onUpdateZoomPipRect: (android.graphics.Rect?) -> Unit = {}
) {
    Box(modifier = modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ ->
                // [v1.2.82] 實施雙指縮放 (Pinch-to-Zoom)：將單指滑動升級為標準變換手勢
                if (zoom != 1f) {
                    val newZoom = (state.zoomFactor * zoom).coerceIn(1.0f, 3.0f)
                    onUpdateState { zoomFactor = newZoom }
                }
            }
        }
    ) {
        // --- 1. 高頻數據欄 (v1.4.1 強制絕對水平置中) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp + 16.dp)
                .wrapContentWidth(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            ThrottledStatusInfoBar(
                altitude = state.altitude,
                posX = state.posX,
                posZ = state.posZ,
                speed = state.speed,
                windLevel = state.windLevel,
                windDirection = state.windDirection,
                isMotorLocked = state.isMotorLocked,
                droneType = state.droneType,
                useFlightLimit = state.useFlightLimit,
                batteryPercent = state.batteryPercent,
                batteryVoltage = state.batteryVoltage,
                isVisible = isStatusVisible,
                onToggle = { onToggleStatus() }
            )
        }

        // --- 2. 飛行儀表區 (雷達、OSD等) ---
        InstrumentsLayer(state, onUpdatePipRect, onUpdateState)

        // --- 4. 遠距姿態輔助視窗 (Smart Zoom PiP) ---
        val horizontalDist = sqrt(state.posX.pow(2) + (state.posZ + 6f).pow(2))
        val isInZoomZone = state.enableZoomAssistant && horizontalDist > 10.0f && state.cameraMode != "FPV 視角" && state.cameraMode != "跟隨視角"
        
        if (isInZoomZone && !state.isMenuExpanded) {
            // [v1.4.1 修正] 避讓邏輯：由於現在採用相對偏移，observerTilt < -5 代表飛機位於螢幕上方
            val shouldRelocate = state.autoPiPRelocate && (state.observerTilt < -5f || state.altitude > 10f)
            val alignment = if (shouldRelocate) Alignment.TopEnd else Alignment.TopCenter
            val topPadding = 16.dp
            val endPadding = if (shouldRelocate) 80.dp else 0.dp

            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(top = topPadding, end = endPadding)
                    .size(150.dp, 100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x55111111))
                    .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
            ) {
                // 關鍵修正：增加內縮 padding，確保方形的 OpenGL 畫面縮在圓角邊框內
                Box(modifier = Modifier.fillMaxSize().padding(3.dp).onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow(); val size = coords.size
                    onUpdateZoomPipRect(android.graphics.Rect(pos.x.toInt(), pos.y.toInt(), (pos.x + size.width).toInt(), (pos.y + size.height).toInt()))
                })

                Text(
                    "姿態輔助 (ZOOM)", 
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), 
                    color = Color(0xFFFF9800).copy(0.8f), 
                    fontSize = 8.sp, 
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            SideEffect { onUpdateZoomPipRect(null) }
        }

        // --- 3. 虛擬搖桿層 ---
        if (state.showVirtualJoysticks) {
            JoystickOverlay(state, stickState)
        }

        MissionManager.RenderOverlay(state, onUpdateState)

        // --- 5. 邊界預警提示 (v1.3.9) ---
        androidx.compose.animation.AnimatedVisibility(
            visible = state.isNearBoundary && !state.isCollision,
            modifier = Modifier.align(Alignment.Center),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "warning_text")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "text_pulse"
            )
            Text(
                "⚠️ 接近空域邊界",
                color = Color.Yellow.copy(alpha = alpha),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 10f)
                )
            )
        }

    }
}

/**
 * [優化 1] 視覺限流狀態列
 */
@Composable
fun ThrottledStatusInfoBar(
    altitude: Float, posX: Float, posZ: Float, speed: Float,
    windLevel: Int, windDirection: String,
    isMotorLocked: Boolean, droneType: String,
    useFlightLimit: Boolean,
    batteryPercent: Int,
    batteryVoltage: Float,
    isVisible: Boolean, onToggle: () -> Unit
) {
    // 關鍵優化：使用 LaunchedEffect 進行 10FPS (100ms) 的視覺限流
    var displayAlt by remember { mutableFloatStateOf(0f) }
    var displaySpeed by remember { mutableFloatStateOf(0f) }
    var displayDist by remember { mutableFloatStateOf(0f) }

    val groundOffset = remember(droneType) { DroneRegistry.getSpec(droneType).groundOffset }

    LaunchedEffect(altitude, posX, posZ, speed) {
        displayAlt = (altitude - groundOffset).coerceAtLeast(0f)
        displaySpeed = speed
        displayDist = sqrt(posX.pow(2) + (posZ + 6f).pow(2))
        delay(100) 
    }

    if (isVisible) {
        Row(
            modifier = Modifier
                .background(Color(0xAA111111), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [v1.4.1 修正] 前方加入對稱空白，抵銷後方 Icon 寬度以達成絕對置中
            Spacer(modifier = Modifier.width(22.dp)) 

            StatusItem("高度", String.format(Locale.US, "%.1f m", displayAlt), if(displayAlt >= 29.9f) Color.Red else Color.Cyan)
            StatusVerticalDivider()
            StatusItem("速度", String.format(Locale.US, "%.1f m/s", displaySpeed), Color.White)
            StatusVerticalDivider()
            StatusItem("距離", String.format(Locale.US, "%.1f m", displayDist), Color.White)
            StatusVerticalDivider()
            com.horizon.caadronesimulator.ui.components.WindIndicator(
                level = windLevel,
                direction = windDirection
            )
            
            if (useFlightLimit) {
                StatusVerticalDivider()
                com.horizon.caadronesimulator.ui.components.BatteryIndicator(
                    percent = batteryPercent,
                    voltage = batteryVoltage
                )
            }

            StatusVerticalDivider()
            StatusItem("馬達", if(isMotorLocked) "鎖定" else "運轉", if(isMotorLocked) Color.Red else Color(0xFFC6FF00))
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(16.dp))
        }
    } else {
        Box(modifier = Modifier.padding(bottom = 2.dp).size(44.dp, 20.dp).background(Color(0xAA111111), RoundedCornerShape(10.dp)).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(10.dp)).clickable { onToggle() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun StatusVerticalDivider() { Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x22FFFFFF))) }

@Composable
fun StatusItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) { 
        Text(label, color = Color.Gray, fontSize = 8.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) 
    }
}
