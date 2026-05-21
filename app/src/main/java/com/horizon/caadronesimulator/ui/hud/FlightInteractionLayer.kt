package com.horizon.caadronesimulator.ui.hud

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.*
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
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.ui.common.NikoConfirmDialog
import kotlin.math.pow

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.5.3] 飛行互動控制層 (UI Corrected Version)
 */
@Composable
fun FlightInteractionLayer(
    state: DroneState,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // --- 1. 著地安全控制 ---
        val spec = remember(state.droneType) { DroneRegistry.getSpec(state.droneType) }
        // [v1.5.9] 提高地面判定寬容度至 0.5m，確保物理彈跳時下打油門仍能停槳
        val isNearGround by remember(state.altitude) { derivedStateOf { state.altitude <= (spec.groundOffset + 0.5f) } }
        
        if (isNearGround) {
            // [v1.7.6] 校準：Zoom Assistant 觸發維持作業中心 Z=6 基準
            val distToOpsCenter = kotlin.math.sqrt(state.posX * state.posX + (state.posZ - 6f) * (state.posZ - 6f))
            val isInZoomZone = state.enableZoomAssistant && distToOpsCenter > 10.0f && state.cameraMode != AppConfig.CAM_MODE_FPV && state.cameraMode != AppConfig.CAM_MODE_FOLLOW && !state.isMenuExpanded
            val isZoomRelocated = state.autoPiPRelocate && (state.observerTilt < -5f || state.altitude > 10f)
            val isZoomInCenter = isInZoomZone && !isZoomRelocated
            val buttonTopPadding = if (isZoomInCenter) 175.dp else 85.dp
            
            Box(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = buttonTopPadding)) {
                Surface(
                    onClick = { onUpdateState { this.isMotorLocked = !this.isMotorLocked; this.lastInteractionTime = System.currentTimeMillis() } },
                    color = if (state.isMotorLocked) Color(0xAA4CAF50) else Color(0xAAF44336),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(0.5f)),
                    modifier = Modifier.size(width = 100.dp, height = 40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = if (state.isMotorLocked) stringResource(R.string.action_arm) else stringResource(R.string.action_disarm),
                            color = Color.White, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // --- 2. 右上角功能選單 ---
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp), 
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
                        InteractionBtn(Icons.Default.VideogameAsset, state.showVirtualJoysticks) { onUpdateState { this.showVirtualJoysticks = !this.showVirtualJoysticks } }
                        InteractionBtn(Icons.Default.Timer, state.isSpotTimerEnabled) { 
                            onUpdateState { 
                                this.isSpotTimerEnabled = !this.isSpotTimerEnabled
                                if (this.isSpotTimerEnabled) {
                                    this.spotTimerMessage = "IDLE"
                                    this.spotTimerSeconds = 5.0f
                                }
                            } 
                        }
                        InteractionBtn(if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, !state.isMuted) { onUpdateState { this.isMuted = !this.isMuted } }
                        
                        var viewExpanded by remember { mutableStateOf(false) }
                        var cameraMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            InteractionBtn(Icons.Default.Visibility) { viewExpanded = true }
                            // [v1.5.9] 樣式還原：1:1 復刻 Git 原始深色戰術選單
                            MaterialTheme(
                                colorScheme = MaterialTheme.colorScheme.copy(surface = Color(0xEE111111)),
                                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenu(
                                    expanded = viewExpanded, 
                                    onDismissRequest = { viewExpanded = false }, 
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier.background(Color(0xEE111111)).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("${stringResource(R.string.menu_camera_mode)}: ${state.cameraMode}", color = Color.White, fontSize = 13.sp) }, 
                                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowRight, null, tint = Color.White.copy(0.5f)) }, 
                                        onClick = { cameraMenuExpanded = true }
                                    )
                                    DropdownMenuItem(
                                        text = { 
                                            val z = state.zoomFactor
                                            val label = when { 
                                                z < 0.75f -> "0.5X"
                                                z < 1.25f -> "1.0X"
                                                z < 1.75f -> "1.5X"
                                                z < 2.5f -> "2.0X"
                                                else -> "3.0X" 
                                            }
                                            Text("${stringResource(R.string.menu_zoom_factor)}: $label (${stringResource(R.string.menu_click_to_switch)})", color = Color.Cyan, fontSize = 13.sp)
                                        }, 
                                        onClick = { 
                                            val current = state.zoomFactor
                                            val next = when { 
                                                current < 0.75f -> 1.0f
                                                current < 1.25f -> 1.5f
                                                current < 1.75f -> 2.0f
                                                current < 2.5f -> 3.0f
                                                else -> 0.5f 
                                            }
                                            onUpdateState { zoomFactor = next } 
                                        }
                                    )
                                    HorizontalDivider(color = Color.White.copy(0.1f))
                                    DropdownMenuItem(text = { Text(if (state.showObstacles) stringResource(R.string.menu_hide_obstacles) else stringResource(R.string.menu_show_obstacles), color = Color.White, fontSize = 13.sp) }, onClick = { onUpdateState { this.showObstacles = !this.showObstacles }; viewExpanded = false })
                                    DropdownMenuItem(text = { Text(if (state.showShadow) stringResource(R.string.menu_hide_shadow) else stringResource(R.string.menu_show_shadow), color = Color.White, fontSize = 13.sp) }, onClick = { onUpdateState { this.showShadow = !this.showShadow }; viewExpanded = false })
                                    DropdownMenuItem(text = { Text(if (state.showFlightPath) stringResource(R.string.menu_hide_path) else stringResource(R.string.menu_show_path), color = Color.White, fontSize = 13.sp) }, onClick = { onUpdateState { this.showFlightPath = !this.showFlightPath }; viewExpanded = false })
                                    DropdownMenuItem(text = { Text(if (state.enableZoomAssistant) stringResource(R.string.menu_disable_zoom) else stringResource(R.string.menu_enable_zoom), color = Color.White, fontSize = 13.sp) }, onClick = { onUpdateState { this.enableZoomAssistant = !this.enableZoomAssistant }; viewExpanded = false })
                                }

                                DropdownMenu(
                                    expanded = cameraMenuExpanded, 
                                    onDismissRequest = { cameraMenuExpanded = false }, 
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier.background(Color(0xEE111111)).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                                ) {
                                    listOf(
                                        AppConfig.CAM_MODE_STATION_TRACK to stringResource(R.string.visual_cam_mode_station_track),
                                        AppConfig.CAM_MODE_STATION_SMART to stringResource(R.string.visual_cam_mode_station_smart),
                                        AppConfig.CAM_MODE_STATION_FIXED to stringResource(R.string.visual_cam_mode_station_fixed),
                                        AppConfig.CAM_MODE_FOLLOW to stringResource(R.string.visual_cam_mode_follow),
                                        AppConfig.CAM_MODE_FPV to stringResource(R.string.visual_cam_mode_fpv),
                                        AppConfig.CAM_MODE_OBS to stringResource(R.string.visual_cam_mode_obs)
                                    ).forEach { (id, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = if(state.cameraMode == id) Color.Cyan else Color.White, fontSize = 13.sp) }, 
                                            onClick = { onUpdateState { this.cameraMode = id }; cameraMenuExpanded = false; viewExpanded = false }
                                        ) 
                                    }
                                }
                            }
                        }

                        // [v1.5.3] 設定按鈕回歸 (正確使用 Settings 圖示)
                        InteractionBtn(Icons.Default.Settings) { onUpdateState { this.showSettings = true } }
                    }
                }
                InteractionBtn(if (state.isMenuExpanded) Icons.Default.Close else Icons.Default.Menu, state.isMenuExpanded) { onUpdateState { this.isMenuExpanded = !this.isMenuExpanded } }
            }
        }
    }
}

@Composable
fun InteractionBtn(icon: ImageVector, isSelected: Boolean = false, tint: Color = if (isSelected) Color.Cyan else Color.White, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp).background(Color(0xAA111111), CircleShape).border(1.dp, if (isSelected) Color.Cyan.copy(0.6f) else Color(0x22FFFFFF), CircleShape)) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}
