package com.horizon.caadronesimulator.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.model.DroneState
import kotlin.math.pow

/**
 * [v1.2.68] 飛行互動控制層
 * 負責所有按鈕控制、選單操作與安全保護邏輯。
 * 從 DroneHUD 抽離，使儀表板回歸純粹的顯示職責。
 */
@Composable
fun FlightInteractionLayer(
    state: DroneState,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // --- 1. 著地安全控制 (僅在靠近地面時浮現) ---
        val spec = remember(state.droneType) { DroneRegistry.getSpec(state.droneType) }
        val isNearGround by remember(state.altitude) { derivedStateOf { state.altitude <= (spec.groundOffset + 0.15f) } }
        
        if (isNearGround) {
            // [v1.4.2 修正] 統一使用 statusBarsPadding 並動態調整高度避開計時器
            val horizontalDist = kotlin.math.sqrt(state.posX * state.posX + (state.posZ + 6f) * (state.posZ + 6f))
            val isInZoomZone = state.enableZoomAssistant && horizontalDist > 10.0f && state.cameraMode != "FPV 視角" && state.cameraMode != "跟隨視角" && !state.isMenuExpanded
            val isZoomRelocated = state.autoPiPRelocate && (state.observerTilt < -5f || state.altitude > 10f)
            val isZoomInCenter = isInZoomZone && !isZoomRelocated
            
            val buttonTopPadding = if (isZoomInCenter) 175.dp else 85.dp
            
            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = buttonTopPadding)
            ) {
                Button(
                    onClick = { onUpdateState { this.isMotorLocked = !this.isMotorLocked } }, 
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isMotorLocked) Color(0xAA4CAF50) else Color(0xAAF44336)), 
                    shape = RoundedCornerShape(8.dp), 
                    modifier = Modifier.width(100.dp).height(40.dp).border(1.dp, Color.White.copy(0.5f), RoundedCornerShape(8.dp))
                ) {
                    Text(if (state.isMotorLocked) "起槳" else "停槳", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // --- 2. 右上角功能選單 ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 16.dp, end = 16.dp), 
            verticalAlignment = Alignment.Top, 
            horizontalArrangement = Arrangement.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(
                    visible = state.isMenuExpanded, 
                    enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(), 
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InteractionBtn(Icons.Default.Refresh, tint = Color.Red.copy(0.8f)) { 
                            onReset()
                            onUpdateState { this.isMotorLocked = true; this.isCollision = false; this.isMenuExpanded = false } 
                        }
                        InteractionBtn(Icons.Default.VideogameAsset, state.showVirtualJoysticks) { 
                            onUpdateState { this.showVirtualJoysticks = !this.showVirtualJoysticks } 
                        }
                        InteractionBtn(Icons.Default.Timer, state.isSpotTimerEnabled) { 
                            onUpdateState { this.isSpotTimerEnabled = !this.isSpotTimerEnabled } 
                        }
                        InteractionBtn(if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, !state.isMuted) { 
                            onUpdateState { this.isMuted = !this.isMuted } 
                        }
                        
                        var viewExpanded by remember { mutableStateOf(false) }
                        var cameraMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            InteractionBtn(Icons.Default.Visibility) { viewExpanded = true }
                            DropdownMenu(expanded = viewExpanded, onDismissRequest = { viewExpanded = false }, properties = PopupProperties(focusable = false)) {
                                DropdownMenuItem(text = { Text("視角模式：${state.cameraMode}") }, trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowRight, null) }, onClick = { cameraMenuExpanded = true })
                                
                                // [v1.2.68] 新增視野倍率顯示與點擊循環切換
                                DropdownMenuItem(
                                    text = { 
                                        val z = state.zoomFactor
                                        val label = when {
                                            z < 1.25f -> "1.0X"
                                            z < 1.75f -> "1.5X"
                                            z < 2.5f -> "2.0X"
                                            else -> "3.0X"
                                        }
                                        Text("視野倍率：$label (點擊切換)", color = Color.Cyan) 
                                    },
                                    onClick = {
                                        val current = state.zoomFactor
                                        val next = when {
                                            current < 1.25f -> 1.5f
                                            current < 1.75f -> 2.0f
                                            current < 2.5f -> 3.0f
                                            else -> 1.0f
                                        }
                                        onUpdateState { zoomFactor = next }
                                    }
                                )

                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(if (state.showObstacles) "隱藏障礙物" else "開啟障礙物") }, onClick = { onUpdateState { this.showObstacles = !this.showObstacles }; viewExpanded = false })
                                DropdownMenuItem(text = { Text(if (state.showShadow) "關閉陰影" else "開啟陰影") }, onClick = { onUpdateState { this.showShadow = !this.showShadow }; viewExpanded = false })
                                
                                // [v1.2.68] 新增飛行軌跡開關
                                DropdownMenuItem(
                                    text = { Text(if (state.showFlightPath) "隱藏飛行軌跡" else "顯示飛行軌跡") },
                                    onClick = { 
                                        onUpdateState { this.showFlightPath = !this.showFlightPath }
                                        viewExpanded = false 
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(if (state.enableZoomAssistant) "關閉姿態輔助(ZOOM)" else "開啟姿態輔助(ZOOM)") },
                                    onClick = {
                                        onUpdateState { this.enableZoomAssistant = !this.enableZoomAssistant }
                                        viewExpanded = false
                                    }
                                )
                            }
                            DropdownMenu(expanded = cameraMenuExpanded, onDismissRequest = { cameraMenuExpanded = false }, properties = PopupProperties(focusable = false)) {
                                listOf("站位視角 (追蹤)", "站位視角 (固定)", "跟隨視角", "FPV 視角").forEach { m -> 
                                    DropdownMenuItem(text = { Text(m) }, onClick = { onUpdateState { this.cameraMode = m }; cameraMenuExpanded = false; viewExpanded = false }) 
                                }
                            }
                        }
                        InteractionBtn(Icons.Default.Settings) { onUpdateState { this.showSettings = true } }
                    }
                }
                InteractionBtn(if (state.isMenuExpanded) Icons.Default.Close else Icons.Default.Menu, state.isMenuExpanded) { 
                    onUpdateState { this.isMenuExpanded = !this.isMenuExpanded } 
                }
            }
        }
    }
}

@Composable
fun InteractionBtn(
    icon: ImageVector, 
    isSelected: Boolean = false, 
    tint: Color = if (isSelected) Color.Cyan else Color.White, 
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick, 
        modifier = Modifier.size(44.dp).background(Color(0xAA111111), CircleShape).border(1.dp, if (isSelected) Color.Cyan.copy(0.6f) else Color(0x22FFFFFF), CircleShape)
    ) { 
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) 
    }
}
