package com.horizon.caadronesimulator.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.app.Activity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.ui.components.ClimateSettingsScreen
import com.horizon.caadronesimulator.ui.components.DroneSelectionScreen
import com.horizon.caadronesimulator.ui.components.JoystickMappingScreen
import com.horizon.caadronesimulator.ui.components.VirtualJoystick
import com.horizon.caadronesimulator.model.DroneRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

data class TutorialStep(
    val title: String,
    val description: String,
    val alignment: Alignment,
    val modifier: Modifier = Modifier
)

@Composable
fun DroneHUD(
    state: DroneState,
    onUpdateState: ((DroneState) -> DroneState) -> Unit,
    onReset: () -> Unit,
    onUpdatePipRect: (android.graphics.Rect?) -> Unit = {}
) {
    val latestState by rememberUpdatedState(state)
    var settingsExpanded by remember { mutableStateOf(false) }
    var viewExpanded by remember { mutableStateOf(false) }
    var isStatusVisible by remember { mutableStateOf(true) }
    var radarMode by remember { mutableIntStateOf(0) } // 0: Radar, 1: FPV Mode, 2: Attitude Ball, 3: Minimized
    var trayExpanded by remember { mutableStateOf(false) }
    var wasTrayOpenBeforeFlight by remember { mutableStateOf(true) } // 預設為 true，因為初始是停槳且選單開啟
    
    // 用於存儲解析度獨立的導覽目標位置
    var tutorialTargets by remember { mutableStateOf(mapOf<String, Rect>()) }

    // 根據馬達狀態自動收放選單
    LaunchedEffect(state.isMotorLocked) {
        if (!state.isMotorLocked) {
            // 起槳前紀錄當前選單狀態
            wasTrayOpenBeforeFlight = trayExpanded
            // 起槳：延遲一下再收起
            delay(500)
            trayExpanded = false
        } else {
            // 停槳：僅在起槳前有開啟選單的情況下才自動展開
            if (wasTrayOpenBeforeFlight) {
                trayExpanded = true
            }
        }
    }

    // CSC 偵測 (內八解鎖/手動上鎖)
    val isCSC = (state.stickThrottle < -0.7f && state.stickYaw > 0.7f && 
                state.stickPitch < -0.7f && state.stickRoll < -0.7f)
    val spec = DroneRegistry.getSpec(state.droneType)
    val isGrounded = state.altitude <= spec.groundOffset + 0.15f
    val sticksNeutral = abs(state.stickYaw) < 0.2f && abs(state.stickPitch) < 0.2f && abs(state.stickRoll) < 0.2f
    val isAutoStop = !state.isMotorLocked && isGrounded && state.stickThrottle < -0.95f && sticksNeutral

    // 內八計時解鎖/上鎖：一旦姿勢正確即啟動獨立計時，不依賴硬體事件觸發
    LaunchedEffect(isCSC) {
        if (isCSC) {
            delay(1500)
            val currentState = latestState
            if (currentState.isMotorLocked) {
                onUpdateState { it.copy(isMotorLocked = false, infoMessage = "已解鎖") }
            } else if (isGrounded) {
                onUpdateState { it.copy(isMotorLocked = true, infoMessage = "已上鎖") }
            }
        }
    }

    // 著地自動停槳計時
    LaunchedEffect(isAutoStop) {
        if (isAutoStop) {
            delay(1500)
            onUpdateState { it.copy(isMotorLocked = true, infoMessage = "已上鎖") }
        }
    }

    val orange = Color(0xFFFF9800)
    val lime = Color(0xFFC6FF00)

    val displayMessage = state.infoMessage ?: ""

    Box(modifier = Modifier.fillMaxSize()) {
        // 自定義置中提示框 (符合字串長度)
        if (displayMessage.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 130.dp) // 下移，避免與停槳按鈕重疊
                    .background(Color(0xCC333333), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayMessage,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 監聽提示訊息，2秒後自動消失
        LaunchedEffect(state.infoMessage) {
            if (state.infoMessage != null) {
                delay(2000)
                onUpdateState { it.copy(infoMessage = null) }
            }
        }

        // 監聽引導設定倒數 (獨立計時器，不依賴搖桿事件)
        LaunchedEffect(state.setupWizardStep, state.wizardWaitingForNeutral) {
            if (state.setupWizardStep > 0 && state.wizardWaitingForNeutral) {
                var remaining = 2
                while (remaining > 0) {
                    onUpdateState { it.copy(wizardCountdown = remaining) }
                    delay(1000)
                    remaining--
                }
                // 倒數結束，進入下一步
                onUpdateState { current ->
                    val nextStep = if (current.setupWizardStep < 4) current.setupWizardStep + 1 else 0
                    current.copy(
                        setupWizardStep = nextStep,
                        wizardWaitingForNeutral = false,
                        wizardCountdown = 0
                    )
                }
            }
        }


        // 飛行狀態 (貼齊最下方，並隨導航欄位移)
        if (isStatusVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xAA111111), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { isStatusVisible = false },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val spec = com.horizon.caadronesimulator.model.DroneRegistry.getSpec(state.droneType)
                    val relativeAltitude = (state.altitude - spec.groundOffset).coerceAtLeast(0f)
                    val distance = sqrt((state.posX).pow(2) + (state.posZ - (-6.0f)).pow(2))

                    StatusItem("高度", String.format(Locale.US, "%.1f m", relativeAltitude), if(relativeAltitude >= 29.9f) Color.Red else Color.Cyan)
                    StatusVerticalDivider()
                    StatusItem("速度", String.format(Locale.US, "%.1f m/s", state.speed), Color.White)
                    StatusVerticalDivider()
                    StatusItem("距離", String.format(Locale.US, "%.1f m", distance), Color.White)
                    StatusVerticalDivider()
                    StatusItem("環境", "L${state.windLevel} ${state.windDirection}", if(state.windLevel > 3) Color.Red else Color.White)
                    StatusVerticalDivider()
                    StatusItem("馬達", if(state.isMotorLocked) "鎖定" else "運轉", if(state.isMotorLocked) Color.Red else lime)

                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
                }
            }
        }

        // 視覺化雷達 / 姿態球 HUD
        val radarAlignment = if (state.showVirtualJoysticks) Alignment.TopStart else Alignment.BottomStart
        val radarPaddingModifier = if (state.showVirtualJoysticks) {
            Modifier.padding(top = 16.dp, start = 20.dp)
        } else {
            // 回到左下角，給予基礎間距
            Modifier.padding(bottom = 16.dp, start = 16.dp)
        }

        Box(modifier = radarPaddingModifier.align(radarAlignment)) {
            when (radarMode) {
                0 -> {
                    // 雷達模式 (矩形) - 縮小尺寸以適應小裝置
                    Box(
                        modifier = Modifier
                            .size(150.dp, 100.dp)
                            .background(Color(0xAA111111), RoundedCornerShape(12.dp))
                            .border(1.5.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
                            .clickable { radarMode = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(140.dp, 90.dp)) {
                            val center = Offset(size.width / 2, size.height * 0.83f)
                            drawRect(color = Color.White.copy(alpha = 0.05f), size = size)
                            val scale = 2.0.dp.toPx()

                            rotate(180f, center) {
                                val fieldCenter = Offset(center.x, center.y + 6f * scale)
                                val redColor = Color.Red.copy(alpha = 0.4f)
                                val circleXOffset = 6f * scale

                                drawRect(
                                    color = Color.Red.copy(alpha = 0.3f),
                                    topLeft = Offset(fieldCenter.x - 35f * scale, fieldCenter.y - 13f * scale),
                                    size = Size(70f * scale, 43f * scale),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                                listOf(4f, 8f).forEach { radiusMeter ->
                                    val r = radiusMeter * scale
                                    val leftCenter = Offset(fieldCenter.x - circleXOffset, fieldCenter.y)
                                    val rightCenter = Offset(fieldCenter.x + circleXOffset, fieldCenter.y)

                                    if (radiusMeter == 8f) {
                                        val angle = acos(6f / 8f).toDouble().let { Math.toDegrees(it) }.toFloat()
                                        val sweep = angle * 2f
                                        drawArc(color = redColor, startAngle = angle, sweepAngle = 360f - sweep, useCenter = false,
                                            topLeft = Offset(leftCenter.x - r, leftCenter.y - r), size = Size(r*2, r*2), style = Stroke(1.dp.toPx()))
                                        drawArc(color = redColor, startAngle = 180f + angle, sweepAngle = 360f - sweep, useCenter = false,
                                            topLeft = Offset(rightCenter.x - r, rightCenter.y - r), size = Size(r*2, r*2), style = Stroke(1.dp.toPx()))
                                    } else {
                                        drawCircle(color = redColor, radius = r, center = leftCenter, style = Stroke(1.dp.toPx()))
                                        drawCircle(color = redColor, radius = r, center = rightCenter, style = Stroke(1.dp.toPx()))
                                    }
                                }

                                val yellowColor = Color.Yellow.copy(alpha = 0.5f)
                                listOf(8f, 16f).forEach { side ->
                                    val s = side * scale
                                    drawRect(color = yellowColor, topLeft = Offset(fieldCenter.x - s / 2, fieldCenter.y - s / 2), size = Size(s, s), style = Stroke(1.dp.toPx()))
                                }

                                drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 1.0f * scale, center = center, style = Stroke(1.dp.toPx()))

                                val relX = state.posX - 0f
                                val relZ = state.posZ - (-6.0f)
                                val dronePos = Offset(center.x + relX * scale, center.y + relZ * scale)
                                rotate(degrees = -state.yaw, pivot = dronePos) {
                                    val arrowPath = Path().apply {
                                        moveTo(dronePos.x, dronePos.y + 6.dp.toPx())
                                        lineTo(dronePos.x - 4.dp.toPx(), dronePos.y - 4.dp.toPx())
                                        lineTo(dronePos.x + 4.dp.toPx(), dronePos.y - 4.dp.toPx())
                                        close()
                                    }
                                    drawPath(path = arrowPath, color = Color.White)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // FPV 模擬視角 / OSD 模式 (矩形) - 縮小尺寸
                    Box(
                        modifier = Modifier
                            .size(150.dp, 100.dp)
                            .clip(RoundedCornerShape(12.dp)) // 強制裁剪內容
                            .background(Color(0x55111111))
                            .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
                            .clickable {
                                onUpdatePipRect(null)
                                radarMode = 2
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // 建立一個內縮的 Box 用於獲取渲染座標，避免 3D 畫面尖角溢出圓角邊框
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp) // 內縮 3dp 確保 3D 畫面縮在圓角內
                                .onGloballyPositioned { coords ->
                                    if (radarMode == 1) {
                                        val pos = coords.positionInWindow()
                                        val size = coords.size
                                        onUpdatePipRect(android.graphics.Rect(
                                            pos.x.toInt(), pos.y.toInt(),
                                            (pos.x + size.width).toInt(), (pos.y + size.height).toInt()
                                        ))
                                    }
                                }
                        )

                        val spec = com.horizon.caadronesimulator.model.DroneRegistry.getSpec(state.droneType)
                        val relAlt = (state.altitude - spec.groundOffset).coerceAtLeast(0f)
                        val dist = sqrt((state.posX).pow(2) + (state.posZ - (-6.0f)).pow(2))

                        // OSD 文字內邊距加大，避免觸碰圓角
                        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                Text("V: ${String.format("%.1f", state.speed)}", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text("P: ${state.pitch.toInt()}°", color = Color.Green, fontSize = 7.sp)
                            }
                            Column(modifier = Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
                                Text("H: ${String.format("%.1f", relAlt)}", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text("T: ${state.cameraTilt.toInt()}°", color = Color.Cyan, fontSize = 7.sp)
                            }
                            Text("D: ${String.format("%.1f", dist)}m", modifier = Modifier.align(Alignment.BottomCenter), color = Color.Green, fontSize = 8.sp)
                            Text("${state.yaw.toInt()}°", modifier = Modifier.align(Alignment.TopCenter), color = Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                2 -> {
                    // 姿態球模式 (圓形) - 縮小尺寸
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color(0xAA111111), CircleShape)
                            .border(1.5.dp, Color(0xFF00BFFF), CircleShape)
                            .clickable { radarMode = 3 },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val radius = size.minDimension / 2
                            rotate(-state.yaw, center) {
                                for (i in 0 until 360 step 30) {
                                    val angleDeg = i.toFloat() - 90f
                                    val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                                    val lineLen = if (i % 90 == 0) 6.dp.toPx() else 3.dp.toPx()
                                    val start = Offset(center.x + cos(angleRad) * (radius - 2.dp.toPx()), center.y + sin(angleRad) * (radius - 2.dp.toPx()))
                                    val end = Offset(center.x + cos(angleRad) * (radius - 2.dp.toPx() - lineLen), center.y + sin(angleRad) * (radius - 2.dp.toPx() - lineLen))
                                    drawLine(Color.White.copy(alpha = 0.5f), start, end, strokeWidth = 1.2.dp.toPx())
                                    if (i % 90 == 0) {
                                        val labelColor = when(i) { 0 -> Color.Red; else -> Color.White }
                                        val markerStart = Offset(center.x + cos(angleRad) * (radius - 2.dp.toPx()), center.y + sin(angleRad) * (radius - 2.dp.toPx()))
                                        val markerEnd = Offset(center.x + cos(angleRad) * (radius - 10.dp.toPx()), center.y + sin(angleRad) * (radius - 10.dp.toPx()))
                                        drawLine(labelColor, markerStart, markerEnd, strokeWidth = 1.5.dp.toPx())
                                    }
                                }
                            }
                            val innerRadius = radius - 10.dp.toPx()
                            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, innerRadius)) }) {
                                val pitchOffset = -(state.pitch / 45f) * innerRadius
                                rotate(-state.roll, center) {
                                    drawRect(Color(0xFF5D4037), topLeft = Offset(-size.width, center.y + pitchOffset), size = Size(size.width * 3, size.height * 2))
                                    drawRect(Color(0xFF0288D1), topLeft = Offset(-size.width, -size.height * 2 + center.y + pitchOffset), size = Size(size.width * 3, size.height * 2))
                                    drawLine(Color.White, Offset(-size.width, center.y + pitchOffset), Offset(size.width * 2, center.y + pitchOffset), 1.5.dp.toPx())
                                }
                            }
                            val crossSize = 10.dp.toPx()
                            drawLine(Color.Yellow, Offset(center.x - crossSize, center.y), Offset(center.x - 3.dp.toPx(), center.y), 1.5.dp.toPx())
                            drawLine(Color.Yellow, Offset(center.x + 3.dp.toPx(), center.y), Offset(center.x + crossSize, center.y), 1.5.dp.toPx())
                            drawLine(Color.Yellow, Offset(center.x, center.y - 3.dp.toPx()), Offset(center.x, center.y - crossSize), 1.5.dp.toPx())
                        }
                    }
                }
                3 -> {
                    // 最小化模式
                    IconButton(
                        onClick = { radarMode = 0 },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xAA111111), CircleShape)
                            .border(1.dp, Color(0xFFFF9800), CircleShape)
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }


        // 相機傾斜控制 (FPV)
        if (state.cameraMode == "FPV 視角") {
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 1.dp).width(10.dp).fillMaxHeight(0.5f).background(Color(0x22000000), RoundedCornerShape(5.dp)).pointerInput(Unit) {
                detectDragGestures { change, dragAmount -> change.consume(); val newTilt = (latestState.cameraTilt - dragAmount.y * 0.4f).coerceIn(-90f, 30f); onUpdateState { it.copy(cameraTilt = newTilt) } }
            }, contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxHeight(0.85f).width(0.5.dp).background(Color(0x33FFFFFF)))
                Column(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text("T", color = Color.White.copy(alpha = 0.3f), fontSize = 7.sp)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val fraction = (state.cameraTilt - (-90f)) / (30f - (-90f))
                        Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(color = Color.Cyan, radius = 2.5.dp.toPx(), center = Offset(size.width / 2, size.height * (1f - fraction))) }
                    }
                    Text("${state.cameraTilt.toInt()}°", color = Color.Cyan.copy(alpha = 0.8f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 起槳 / 停槳按鈕 (僅在著地時顯示)
        if (isGrounded) {
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)) {
                Button(onClick = {
                    if (state.isMotorLocked) {
                        onUpdateState { it.copy(isMotorLocked = false) }
                    } else {
                        onUpdateState { it.copy(isMotorLocked = true) }
                    }
                },
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isMotorLocked) Color(0xAA4CAF50) else Color(0xAAF44336)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(100.dp).height(45.dp).border(1.dp, Color.White, RoundedCornerShape(8.dp))) {
                    Text(if (state.isMotorLocked) "起槳" else "停槳", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 右側抽屜式工具列 (移動至右上角，並隨系統導航欄與狀態欄位移)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding() // 適應狀態欄與導航欄
                .displayCutoutPadding() // 適應瀏海屏
                .padding(top = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 展開內容置於按鈕左方 (在 Row 中先定義內容，實現「向左展開」的視覺順序)
            AnimatedVisibility(
                visible = trayExpanded,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 分組 1: 環境與介面設定
                    ControlBtn(Icons.Default.BarChart, isSelected = isStatusVisible) {
                        isStatusVisible = !isStatusVisible
                    }
                    ControlBtn(if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, isSelected = !state.isMuted) {
                        onUpdateState { it.copy(isMuted = !it.isMuted) }
                    }
                    ControlBtn(Icons.Default.VideogameAsset, isSelected = state.showVirtualJoysticks) {
                        onUpdateState { it.copy(showVirtualJoysticks = !it.showVirtualJoysticks) }
                    }
                    
                    // 分組 2: 視角與進階設定
                    Box {
                        ControlBtn(Icons.Default.Person) {
                            viewExpanded = true
                        }
                        DropdownMenu(expanded = viewExpanded, onDismissRequest = { viewExpanded = false }, properties = PopupProperties(focusable = false)) {
                            DropdownMenuItem(text = { Text("站位視角 (追蹤)") }, onClick = {
                                onUpdateState { it.copy(cameraMode = "站位視角 (追蹤)") }
                                viewExpanded = false
                            })
                            DropdownMenuItem(text = { Text("站位視角 (固定)") }, onClick = {
                                onUpdateState { it.copy(cameraMode = "站位視角 (固定)") }
                                viewExpanded = false
                            })
                            DropdownMenuItem(text = { Text("跟隨視角") }, onClick = {
                                onUpdateState { it.copy(cameraMode = "跟隨視角") }
                                viewExpanded = false
                            })
                            DropdownMenuItem(text = { Text("FPV 視角") }, onClick = {
                                onUpdateState { it.copy(cameraMode = "FPV 視角") }
                                viewExpanded = false
                            })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(if (state.showShadow) "關閉陰影" else "開啟陰影") }, onClick = {
                                onUpdateState { it.copy(showShadow = !it.showShadow) }
                                viewExpanded = false
                            })
                            DropdownMenuItem(text = { Text(if (state.showObstacles) "隱藏障礙物" else "顯示障礙物") }, onClick = {
                                onUpdateState { it.copy(showObstacles = !it.showObstacles) }
                                viewExpanded = false
                            })
                        }
                    }
                    ControlBtn(Icons.Default.Settings) {
                        onUpdateState { it.copy(showSettings = true) }
                    }

                    // 分組 3: 破壞性動作 (紅色警示)
                    ControlBtn(Icons.Default.Refresh, tint = Color.Red.copy(alpha = 0.8f)) {
                        onReset()
                        onUpdateState { it.copy(isMotorLocked = true, isCollision = false) }
                        trayExpanded = false
                    }
                }
            }

            // 切換按鈕置於右方
            ControlBtn(
                icon = if (trayExpanded) Icons.Default.Close else Icons.Default.Menu,
                isSelected = trayExpanded
            ) {
                trayExpanded = !trayExpanded
            }
        }

        // 虛擬搖桿 (隨導航欄與系統欄位移)
        if (state.showVirtualJoysticks) {
            val dz = state.joystickDeadzone
            fun applyDeadzone(v: Float): Float = when { v > dz -> (v - dz) / (1f - dz); v < -dz -> (v + dz) / (1f - dz); else -> 0f }
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding()) {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(40.dp)) {
                    VirtualJoystick(stickX = state.stickLX, stickY = state.stickLY) { lx, ly ->
                        val dx = applyDeadzone(lx); val dy = applyDeadzone(ly)
                        onUpdateState { current ->
                            // 根據模式對應到物理位置，考慮反相
                            current.copy(
                                rawLX = if(current.mappingLX.inverted) -dx else dx,
                                rawLY = if(current.mappingLY.inverted) -dy else dy
                            )
                        }
                    } 
                }
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(40.dp)) {
                    VirtualJoystick(stickX = state.stickRX, stickY = state.stickRY) { rx, ry ->
                        val dx = applyDeadzone(rx); val dy = applyDeadzone(ry)
                        onUpdateState { current ->
                            current.copy(
                                rawRX = if(current.mappingRX.inverted) -dx else dx,
                                rawRY = if(current.mappingRY.inverted) -dy else dy
                            )
                        }
                    } 
                }
            }
        }

        if (state.showSettings) {
            UnifiedSettingsScreen(
                state = state,
                onUpdateState = onUpdateState,
                onClose = { onUpdateState { it.copy(showSettings = false) } },
                onTargetPositioned = { name, rect ->
                    tutorialTargets = tutorialTargets + (name to rect)
                }
            )
        }

        if (state.isCollision) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xBB000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💥 發生碰撞！", color = Color.Red, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp)); Text("無人機已損壞或超出邊界", color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(40.dp)); Button(onClick = { onReset() }, colors = ButtonDefaults.buttonColors(containerColor = orange), shape = RoundedCornerShape(12.dp), modifier = Modifier.width(200.dp).height(60.dp)) { Text("重新開始", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (state.showTutorial) {
            WelcomeTutorial(onDismiss = { onUpdateState { it.copy(showTutorial = false) } })
        }

        if (state.showJoystickTutorial) {
            JoystickSettingsTutorial(
                onDismiss = { onUpdateState { it.copy(showJoystickTutorial = false) } },
                targets = tutorialTargets
            )
        }
        if (state.showClimateTutorial) {
            ClimateSettingsTutorial(
                onDismiss = { onUpdateState { it.copy(showClimateTutorial = false) } },
                targets = tutorialTargets
            )
        }
    }
}

@Composable
fun ClimateSettingsTutorial(onDismiss: () -> Unit, targets: Map<String, Rect>) {
    var step by remember { mutableIntStateOf(0) }
    val tutorialSteps = listOf(
        TutorialStep("環境與氣候導覽", "在此頁面您可以設定模擬環境的各項參數，挑戰不同難度的飛行任務。", Alignment.Center),
        TutorialStep("風力等級選擇", "設定當前的平均風速。等級越高，無人機偏移量越大，需更精確地修正。", Alignment.Center),
        TutorialStep("風向控制系統", "選擇風的來源方向。選擇「隨機」將挑戰無規律的變向強風。", Alignment.Center),
        TutorialStep("激烈度與亂流", "調整風速的起伏程度。高激烈度會產生瞬間強陣風，模擬惡劣氣候環境。", Alignment.Center),
        TutorialStep("時間與光影", "切換不同時段的環境光。注意早晨與下午的陰影拉長方向會有所不同。", Alignment.Center),
        TutorialStep("陰影深淺調節", "調整無人機在地面上的投影濃度，幫助您在不同地形中更好地判斷高度與位置。", Alignment.Center)
    )

    val current = tutorialSteps[step]
    val infiniteTransition = rememberInfiniteTransition(label = "climateTutorial")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    val targetKey = when(step) {
        1 -> "wind_level"; 2 -> "wind_dir"; 3 -> "wind_var"; 4 -> "time"; 5 -> "shadow"
        else -> null
    }
    val targetRect = if (targetKey != null) targets[targetKey] else null

    val density = LocalDensity.current
    val screenHeight = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toDp() }
    val screenWidth = with(density) { LocalContext.current.resources.displayMetrics.widthPixels.toDp() }
    val isSmallScreen = screenHeight < 420.dp

    val (dynamicAlignment, dynamicPadding) = if (targetRect != null) {
        val targetCenterY = with(density) { targetRect.center.y.toDp() }

        if (targetCenterY > screenHeight * 0.5f) {
            val topPadding = if (isSmallScreen) 10.dp else 40.dp
            Alignment.TopCenter to Modifier.padding(top = topPadding)
        } else {
            val bottomPadding = if (isSmallScreen) 10.dp else 40.dp
            Alignment.BottomCenter to Modifier.padding(bottom = bottomPadding)
        }
    } else {
        Alignment.Center to Modifier.padding(32.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000))
            .clickable { if (step < tutorialSteps.size - 1) step++ else onDismiss() }
    ) {
        if (targetRect != null) {
            DynamicTutorialHighlight(targetRect, current.title, pulseAlpha)
        }

        Surface(
            modifier = Modifier
                .align(dynamicAlignment)
                .then(dynamicPadding)
                .widthIn(max = if (isSmallScreen) (screenWidth * 0.75f) else 400.dp),
            color = Color(0xFF1B2535),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, Color.Cyan.copy(alpha = 0.8f))
        ) {
            Column(modifier = Modifier.padding(if (isSmallScreen) 14.dp else 20.dp)) {
                Text(
                    text = current.title,
                    color = Color.Cyan,
                    fontSize = if (isSmallScreen) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(if (isSmallScreen) 6.dp else 8.dp))
                Text(
                    text = current.description,
                    color = Color.White,
                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                    lineHeight = if (isSmallScreen) 18.sp else 21.sp
                )
                Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${step + 1}/${tutorialSteps.size}",
                        color = Color.Gray,
                        fontSize = if (isSmallScreen) 10.sp else 11.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "點擊畫面繼續",
                            color = Color.Cyan.copy(alpha = 0.5f),
                            fontSize = if (isSmallScreen) 10.sp else 11.sp
                        )
                        Spacer(Modifier.width(16.dp))
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("跳過", color = Color.White.copy(alpha = 0.4f), fontSize = if (isSmallScreen) 11.sp else 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JoystickSettingsTutorial(onDismiss: () -> Unit, targets: Map<String, Rect>) {
    var step by remember { mutableIntStateOf(0) }
    val tutorialSteps = listOf(
        TutorialStep("搖桿設定導覽", "在此頁面您可以完整設定外接遙控器，確保飛行操控精確無誤。", Alignment.Center),
        TutorialStep("一鍵引導設定", "點擊「引導設定」由系統帶領您完成所有軸向與正反向偵測，是最推薦新手的設定方式。", Alignment.Center),
        TutorialStep("搖桿校準工具", "若發現搖桿無法回正或行程不足，請使用校準工具來定義物理中位點與邊界。", Alignment.Center),
        TutorialStep("單軸自動綁定", "點擊各功能的「Auto Bind」並撥動搖桿，可快速將功能對應到指定的實體軸向。", Alignment.Center),
        TutorialStep("反向開關", "若發現飛機動作與手感相反（如推桿變下降），切換此開關即可立即修正。", Alignment.Center),
        TutorialStep("操控模式切換", "點擊箭頭可在 Mode 1 (日本手)、Mode 2 (美國手) 與 Mode 3 (中國手) 之間快速切換。", Alignment.Center)
    )

    val current = tutorialSteps[step]
    val infiniteTransition = rememberInfiniteTransition(label = "tutorial")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    val targetKey = when(step) {
        1 -> "wizard"; 2 -> "calib"; 3 -> "auto_bind"; 4 -> "invert"; 5 -> "mode"
        else -> null
    }
    val targetRect = if (targetKey != null) targets[targetKey] else null

    val density = LocalDensity.current
    val screenHeight = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toDp() }
    val screenWidth = with(density) { LocalContext.current.resources.displayMetrics.widthPixels.toDp() }
    val isSmallScreen = screenHeight < 420.dp

    val (dynamicAlignment, dynamicPadding) = if (targetRect != null) {
        val targetCenterY = with(density) { targetRect.center.y.toDp() }
        if (targetCenterY > screenHeight * 0.5f) {
            val topPadding = if (isSmallScreen) 10.dp else 40.dp
            Alignment.TopCenter to Modifier.padding(top = topPadding)
        } else {
            val bottomPadding = if (isSmallScreen) 10.dp else 40.dp
            Alignment.BottomCenter to Modifier.padding(bottom = bottomPadding)
        }
    } else {
        Alignment.Center to Modifier.padding(32.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000))
            .clickable { if (step < tutorialSteps.size - 1) step++ else onDismiss() }
    ) {
        if (targetRect != null) {
            DynamicTutorialHighlight(targetRect, current.title, pulseAlpha)
        }

        Surface(
            modifier = Modifier
                .align(dynamicAlignment)
                .then(dynamicPadding)
                .widthIn(max = if (isSmallScreen) (screenWidth * 0.75f) else 400.dp),
            color = Color(0xFF1B2535),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, Color.Cyan.copy(alpha = 0.8f))
        ) {
            Column(modifier = Modifier.padding(if (isSmallScreen) 14.dp else 20.dp)) {
                Text(
                    text = current.title,
                    color = Color.Cyan,
                    fontSize = if (isSmallScreen) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(if (isSmallScreen) 6.dp else 8.dp))
                Text(
                    text = current.description,
                    color = Color.White,
                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                    lineHeight = if (isSmallScreen) 18.sp else 21.sp
                )
                Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${step + 1}/${tutorialSteps.size}",
                        color = Color.Gray,
                        fontSize = if (isSmallScreen) 10.sp else 11.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "點擊畫面繼續",
                            color = Color.Cyan.copy(alpha = 0.5f),
                            fontSize = if (isSmallScreen) 10.sp else 11.sp
                        )
                        Spacer(Modifier.width(16.dp))
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("跳過", color = Color.White.copy(alpha = 0.4f), fontSize = if (isSmallScreen) 11.sp else 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeTutorial(onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val tutorialSteps = listOf(
        TutorialStep("歡迎使用 Niko Drone Simulator", "這是一個專業的飛行訓練系統，旨在幫助您熟悉無人機考照流程與物理特性。", Alignment.Center),
        TutorialStep("功能工具列面板", "點擊右上角按鈕開啟工具列。包含：\n• 數據開關：顯示/隱藏底部資訊\n• 音效切換：開啟/靜音馬達與環境音\n• 虛擬搖桿：切換螢幕觸控按鍵\n• 視角與環境：切換追蹤/跟隨/FPV視角\n• 整合設定：進行搖桿映射與機型選擇\n• 重置飛行：(紅色) 立即回到起飛位置", Alignment.Center, Modifier.padding(top = 100.dp)),
        TutorialStep("視覺化雷達 HUD", "左下角顯示無人機在場地中的相對位置與航向。紅色區域為出界範圍，請保持在限制內飛行。", Alignment.Center, Modifier.padding(bottom = 120.dp)),
        TutorialStep("飛行狀態監控", "底部欄位提供高度、速度與距離資訊。注意高度限值為 30m，超過將會收到警示訊息。", Alignment.Center, Modifier.padding(bottom = 100.dp)),
        TutorialStep("馬達解鎖與啟動", "著地時點擊置頂的「起槳」按鈕，或使用實體搖桿「內八 CSC」指令來解鎖馬達開始飛行。", Alignment.Center, Modifier.padding(top = 130.dp)),
        TutorialStep("準備就緒！", "現在，請嘗試解鎖馬達並進行第一次起飛練習。祝您練習順利！", Alignment.Center)
    )

    val current = tutorialSteps[step]
    val infiniteTransition = rememberInfiniteTransition(label = "tutorial")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable {
                if (step < tutorialSteps.size - 1) step++ else onDismiss()
            }
    ) {
        // 動態指示箭頭與高亮圈
        when(step) {
            1 -> TutorialHighlight(Alignment.TopEnd, Modifier.statusBarsPadding().displayCutoutPadding().padding(top = 16.dp, end = 16.dp), "功能選單", pulseAlpha)
            2 -> TutorialHighlight(Alignment.BottomStart, Modifier.navigationBarsPadding().padding(bottom = 16.dp, start = 16.dp), "視覺雷達", pulseAlpha)
            3 -> TutorialHighlight(Alignment.BottomCenter, Modifier.navigationBarsPadding(), "飛行數據", pulseAlpha)
            4 -> TutorialHighlight(Alignment.TopCenter, Modifier.padding(top = 70.dp), "解鎖馬達", pulseAlpha)
        }

        // 高亮提示框
        Surface(
            modifier = Modifier
                .align(current.alignment)
                .padding(32.dp)
                .then(current.modifier)
                .widthIn(max = 350.dp),
            color = Color(0xFF1B2535),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, Color.Cyan)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = current.title,
                    color = Color.Cyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = current.description,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "點擊畫面繼續 (${step + 1}/${tutorialSteps.size})",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    TextButton(onClick = onDismiss) {
                        Text("跳過導覽", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusVerticalDivider() {
    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x22FFFFFF)))
}

@Composable
fun TutorialHighlight(alignment: Alignment, modifier: Modifier, label: String, pulseAlpha: Float) {
    val isBottom = alignment == Alignment.BottomStart || alignment == Alignment.BottomCenter || alignment == Alignment.BottomEnd

    val (frameWidth, frameHeight) = when(label) {
        "視覺雷達" -> 150.dp to 100.dp
        "解鎖馬達" -> 105.dp to 50.dp
        "飛行數據" -> 380.dp to 50.dp
        "功能選單" -> 380.dp to 50.dp
        else -> 160.dp to 60.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(alignment)
                .then(modifier)
                .size(width = frameWidth, height = frameHeight)
                .border(3.dp, Color.Cyan.copy(alpha = pulseAlpha), RoundedCornerShape(12.dp))
                .background(Color.Cyan.copy(alpha = 0.1f))
        )

        Column(
            modifier = Modifier
                .align(alignment)
                .then(modifier)
                .offset(y = if (isBottom) -(65.dp) else (frameHeight + 5.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.width(frameWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isBottom) {
                    Text(
                        text = label,
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(30.dp).offset(y = (-5).dp)
                    )
                    Text(
                        text = label,
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicTutorialHighlight(rect: Rect?, label: String, pulseAlpha: Float) {
    if (rect == null) return
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toDp() }

    with(density) {
        val left = rect.left.toDp()
        val top = rect.top.toDp()
        val width = rect.width.toDp()
        val height = rect.height.toDp()
        val isBottom = top > (screenHeight * 0.5f)

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset(x = left, y = top)
                    .size(width = width, height = height)
                    .border(3.dp, Color.Cyan.copy(alpha = pulseAlpha), RoundedCornerShape(12.dp))
                    .background(Color.Cyan.copy(alpha = 0.1f))
            )

            Column(
                modifier = Modifier
                    .offset(
                        x = left + (width / 2) - 80.dp,
                        y = if (isBottom) (top - 65.dp) else (top + height + 5.dp)
                    )
                    .width(160.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isBottom) {
                    Text(
                        text = label,
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(30.dp).offset(y = (-5).dp)
                    )
                    Text(
                        text = label,
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UnifiedSettingsScreen(
    state: DroneState,
    onUpdateState: ((DroneState) -> DroneState) -> Unit,
    onClose: () -> Unit,
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE111111))
            .clickable(enabled = false) {}
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .background(Color(0x22FFFFFF))
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TabIcon(Icons.Default.VideogameAsset, "搖桿", state.settingsTab == 0) {
                    onUpdateState { it.copy(settingsTab = 0) }
                }
                TabIcon(Icons.Default.Cloud, "環境", state.settingsTab == 1) {
                    onUpdateState { it.copy(settingsTab = 1) }
                }
                TabIcon(Icons.Default.AirplanemodeActive, "機型", state.settingsTab == 2) {
                    onUpdateState { it.copy(settingsTab = 2) }
                }
                TabIcon(Icons.Default.Settings, "一般", state.settingsTab == 3) {
                    onUpdateState { it.copy(settingsTab = 3) }
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    val title = when(state.settingsTab) {
                        0 -> "🎮 搖桿模式與映射設定"
                        1 -> "🌍 氣候與環境模擬設定"
                        2 -> "🚁 無人機機型選擇"
                        3 -> "⚙️ 一般系統設定"
                        else -> ""
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.offset(x = 10.dp, y = -10.dp)
                    ) {
                        if (state.settingsTab == 2) {
                            Text(
                                "啟用機型物理特性",
                                color = if (state.applyPhysicalSpecs) Color.Cyan else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = state.applyPhysicalSpecs,
                                onCheckedChange = { enabled -> onUpdateState { it.copy(applyPhysicalSpecs = enabled) } },
                                modifier = Modifier.scale(0.7f),
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Cyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x22FFFFFF)))
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (state.settingsTab < 2) {
                            IconButton(onClick = {
                                onUpdateState {
                                    if (it.settingsTab == 0) it.copy(showJoystickTutorial = true)
                                    else it.copy(showClimateTutorial = true)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "操作導覽",
                                    tint = Color.Cyan.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "關閉設定",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when(state.settingsTab) {
                        0 -> JoystickMappingScreen(
                            mappingLY = state.mappingLY, mappingLX = state.mappingLX, mappingRY = state.mappingRY, mappingRX = state.mappingRX,
                            isAutoBinding = state.isAutoBinding, useRawMapping = state.useRawMapping, halfThrottle = state.halfThrottle, joystickDeadzone = state.joystickDeadzone, activeAxis = state.activeAxisLabel, joystickMode = state.joystickMode, stickLX = state.stickLX, stickLY = state.stickLY, stickRX = state.stickRX, stickRY = state.stickRY, isCalibrating = state.isCalibrating, calibrationStep = state.calibrationStep, setupWizardStep = state.setupWizardStep,
                            onStartCalibration = { onUpdateState { it.copy(isCalibrating = true, calibrationStep = 1) } },
                            onNextCalibrationStep = { onUpdateState { it.copy(calibrationStep = it.calibrationStep + 1) } },
                            onFinishCalibration = { onUpdateState { it.copy(isCalibrating = false, calibrationStep = 0) } },
                            onStartWizard = { onUpdateState { it.copy(setupWizardStep = 1, wizardWaitingForNeutral = false) } },
                            onCancelWizard = { onUpdateState { it.copy(setupWizardStep = 0, isAutoBinding = null, wizardWaitingForNeutral = false) } },
                            isWizardWaiting = state.wizardWaitingForNeutral,
                            wizardCountdown = state.wizardCountdown,
                            onToggleRawMapping = { onUpdateState { s -> s.copy(useRawMapping = it) } },
                            onToggleHalfThrottle = { onUpdateState { s -> s.copy(halfThrottle = it) } },
                            onUpdateDeadzone = { deadzone -> onUpdateState { s -> s.copy(joystickDeadzone = deadzone) } },
                            onStartBinding = { key -> onUpdateState { s -> s.copy(isAutoBinding = key.ifEmpty { null }) } },
                            onToggleInvert = { key -> onUpdateState { s -> when(key) {
                                "ly" -> s.copy(mappingLY = s.mappingLY.copy(inverted = !s.mappingLY.inverted))
                                "lx" -> s.copy(mappingLX = s.mappingLX.copy(inverted = !s.mappingLX.inverted))
                                "ry" -> s.copy(mappingRY = s.mappingRY.copy(inverted = !s.mappingRY.inverted))
                                "rx" -> s.copy(mappingRX = s.mappingRX.copy(inverted = !s.mappingRX.inverted))
                                else -> s 
                            } } },
                            onManualBind = { key, axisId -> onUpdateState { s ->
                                val mapping = com.horizon.caadronesimulator.model.ChannelMapping(axis = axisId, inverted = false, label = "Axis $axisId")
                                when(key) {
                                    "ly" -> s.copy(mappingLY = mapping); "lx" -> s.copy(mappingLX = mapping)
                                    "ry" -> s.copy(mappingRY = mapping); "rx" -> s.copy(mappingRX = mapping)
                                    else -> s
                                }
                            } },
                            onModeChange = { mode -> onUpdateState { it.copy(joystickMode = mode) } },
                            onTargetPositioned = onTargetPositioned
                        )
                        1 -> ClimateSettingsScreen(
                            windLevel = state.windLevel, windDirection = state.windDirection, windVariation = state.windVariation, windDirVariation = state.windDirVariation, timeOfDay = state.timeOfDay, shadowIntensity = state.shadowIntensity,
                            onUpdateWindLevel = { level -> onUpdateState { it.copy(windLevel = level) } },
                            onUpdateWindDirection = { dir -> onUpdateState { it.copy(windDirection = dir) } },
                            onUpdateWindVariation = { vari -> onUpdateState { it.copy(windVariation = vari) } },
                            onUpdateWindDirVariation = { vari -> onUpdateState { it.copy(windDirVariation = vari) } },
                            onUpdateTimeOfDay = { time -> onUpdateState { it.copy(timeOfDay = time) } },
                            onUpdateShadowIntensity = { intensity -> onUpdateState { it.copy(shadowIntensity = intensity) } },
                            onTargetPositioned = onTargetPositioned
                        )
                        2 -> DroneSelectionScreen(
                            currentType = state.droneType,
                            onTypeSelected = { type -> onUpdateState { it.copy(droneType = type) } }
                        )
                        3 -> Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("顯示系統狀態欄", color = Color.White, modifier = Modifier.weight(1f))
                                Switch(checked = state.showStatusBar, onCheckedChange = { onUpdateState { s -> s.copy(showStatusBar = it) } })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("開啟設定時暫停模擬", color = Color.White, modifier = Modifier.weight(1f))
                                Switch(checked = state.pauseInSettings, onCheckedChange = { onUpdateState { s -> s.copy(pauseInSettings = it) } })
                            }
                            HorizontalDivider(color = Color(0x22FFFFFF))
                            Button(
                                onClick = {
                                    onUpdateState { current ->
                                        DroneState().copy(showSettings = true, settingsTab = 3, infoMessage = "已恢復預設設定")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x44FF5252)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("恢復出廠預設值", color = Color.White)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            
                            val metrics = LocalContext.current.resources.displayMetrics
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "裝置解析度",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${metrics.widthPixels} x ${metrics.heightPixels} px",
                                        color = Color.Cyan.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Developed by Enoch Wang",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                    Text(
                                        text = "Release: April 2026",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0x44FFFFFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = if (isSelected) Color.Cyan else Color.White, modifier = Modifier.size(30.dp))
    }
}

@Composable
fun StatusItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(label, color = Color.Gray, fontSize = 8.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ControlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean = false,
    tint: Color = if (isSelected) Color.Cyan else Color.White,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(Color(0xAA111111), CircleShape)
            .border(width = 1.dp, color = if (isSelected) Color.Cyan.copy(alpha = 0.6f) else Color(0x22FFFFFF), shape = CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
