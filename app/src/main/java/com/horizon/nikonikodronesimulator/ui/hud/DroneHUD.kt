package com.horizon.nikonikodronesimulator.ui.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.nikonikodronesimulator.model.AppConfig
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.StickInputState
import com.horizon.nikonikodronesimulator.model.DroneRegistry
import com.horizon.nikonikodronesimulator.ui.theme.NikoTheme
import com.horizon.nikonikodronesimulator.R
import kotlin.math.*

/**
 * [v1.5.9] 飛行抬頭顯示器 - Git 憲法還原版
 */
@Composable
fun DroneHUD(
    state: DroneState,
    stickState: StickInputState,
    isStatusVisible: Boolean,
    tutorialTargets: Map<String, androidx.compose.ui.geometry.Rect>,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onToggleStatus: () -> Unit,
    onUpdatePipRect: (android.graphics.Rect?) -> Unit,
    onUpdateZoomPipRect: (android.graphics.Rect?) -> Unit,
    onUpdateTutorialTargets: (String, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> }
) {
    // [v1.7.15] 效能優化：將重複的距離運算與狀態判定提升至頂層
    val distToOpsCenter = sqrt(state.posX.pow(2) + (state.posZ - 6f).pow(2))
    val isInZoomZone = state.enableZoomAssistant && 
                       !state.isTetherModeEnabled &&
                       distToOpsCenter > AppConfig.VisualDefaults.ZOOM_ASSISTANT_DISTANCE &&
                       state.cameraMode != AppConfig.CAM_MODE_FPV && 
                       state.cameraMode != AppConfig.CAM_MODE_FOLLOW && 
                       state.cameraMode != AppConfig.CAM_MODE_STATION_SMART && 
                       !state.showSettings

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 儀表層 (MFD)
        InstrumentsLayer(
            state = state,
            onUpdatePipRect = onUpdatePipRect,
            onUpdateState = onUpdateState,
            onUpdateTutorialTargets = onUpdateTutorialTargets
        )

        // 2. 虛擬搖桿
        if (state.showVirtualJoysticks) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 10.dp)) {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 60.dp, bottom = 40.dp)) {
                    VirtualJoystick(
                        stickX = stickState.stickLX(state),
                        stickY = stickState.stickLY(state),
                        onDragStateChange = { isTouching -> 
                            stickState.isTouchingLeft = isTouching 
                            onUpdateState { lastInteractionTime = System.currentTimeMillis() }
                        },
                        onValueChange = { x, y -> 
                            stickState.touchLX = x; stickState.touchLY = y 
                            onUpdateState { lastInteractionTime = System.currentTimeMillis() }
                        }
                    )
                }
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 60.dp, bottom = 40.dp)) {
                    VirtualJoystick(
                        stickX = stickState.stickRX(state),
                        stickY = stickState.stickRY(state),
                        onDragStateChange = { isTouching -> 
                            stickState.isTouchingRight = isTouching 
                            onUpdateState { lastInteractionTime = System.currentTimeMillis() }
                        },
                        onValueChange = { x, y -> 
                            stickState.touchRX = x; stickState.touchRY = y 
                            onUpdateState { lastInteractionTime = System.currentTimeMillis() }
                        }
                    )
                }
            }
        }

        // 3. 高頻數據欄
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            OriginalStatusHUD(
                state = state,
                isVisible = isStatusVisible,
                onToggleVisible = { onToggleStatus() }
            )
        }

        // 4. 頂部狀態顯示與姿態輔助視窗 (Zoom Assistant)
        Box(modifier = Modifier.fillMaxSize()) {
            // [v1.7.15] 幾何避讓 3.0：效仿物理碰撞機制 (AABB Intersection)
            // 邏輯：定義 UI 與飛機的 2D 實體矩陣，計算兩者是否產生面積重疊
            val dronePos = state.droneScreenPos
            val isDroneObscured = if (dronePos != null) {
                // A. 定義 UI 視窗感應盒 (螢幕中上衝突區)
                val uiBox = androidx.compose.ui.geometry.Rect(left = 250f, top = 0f, right = 750f, bottom = 520f)
                // B. 定義飛機視覺足跡 (含高度與寬度緩衝，以 T4 為體積基準)
                val droneBox = androidx.compose.ui.geometry.Rect(
                    left = dronePos.x - 180f,
                    top = dronePos.y - 150f,
                    right = dronePos.x + 180f,
                    bottom = dronePos.y + 60f
                )
                // C. 碰撞判定：只要面積重疊，判決成立
                uiBox.overlaps(droneBox)
            } else false

            // 2. 綜合判定：相機俯仰、手動覆蓋、或 2D 實體碰撞
            val shouldRelocate = state.cameraMode != AppConfig.CAM_MODE_STATION_TRACK && 
                                (state.observerTilt < -5f || isDroneObscured)
            
            val isZoomRelocated = state.autoPiPRelocate && shouldRelocate
            val zoomAlign = if (isZoomRelocated) Alignment.TopEnd else Alignment.TopCenter
            
            // 3. 漸進式上浮邏輯
            val dynamicTopPadding = if (!isZoomRelocated && dronePos != null) {
                (4 + (dronePos.y / 1000f) * 12).coerceIn(4f, 16f).dp
            } else 16.dp

            if (isInZoomZone) {
                PrecisionZoomView(
                    state = state,
                    modifier = Modifier
                        .padding(top = dynamicTopPadding, end = if(isZoomRelocated) 65.dp else 0.dp)
                        .align(zoomAlign)
                        .zIndex(5f),
                    onUpdateRect = { r -> 
                        onUpdateZoomPipRect(r?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }) 
                    }
                )
            } else {
                onUpdateZoomPipRect(null)
            }

            Column(modifier = Modifier.align(Alignment.TopCenter).zIndex(10f), horizontalAlignment = Alignment.CenterHorizontally) {
                val zoomPad = if (isInZoomZone) 110.dp else 10.dp
                
                // [v1.7.25] 繫留警告顯示 (優先權高於邊界警告)
                val tetherMsg = when(state.systemMessage) {
                    "TETHER_ALT_LIMIT" -> stringResource(R.string.sys_msg_tether_alt)
                    "TETHER_DIST_LIMIT" -> stringResource(R.string.sys_msg_tether_dist)
                    "TETHER_GROUND_LIMIT" -> stringResource(R.string.sys_msg_tether_ground)
                    else -> null
                }
                
                if (tetherMsg != null) {
                    Spacer(Modifier.height(zoomPad))
                    Surface(
                        color = Color.Red.copy(alpha = 0.8f),
                        shape = NikoTheme.shapes.medium,
                        border = BorderStroke(2.dp, Color.White)
                    ) {
                        Text(
                            tetherMsg,
                            color = Color.White,
                            style = NikoTheme.typography.h2,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                } else if (state.isNearBoundary) {
                    Spacer(Modifier.height(zoomPad))
                    Surface(
                        color = NikoTheme.colors.panel.copy(alpha = 0.85f),
                        shape = NikoTheme.shapes.medium,
                        border = BorderStroke(2.dp, NikoTheme.colors.warning)
                    ) {
                        Text(
                            stringResource(R.string.hud_boundary_warning),
                            color = NikoTheme.colors.warning,
                            style = NikoTheme.typography.h2,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }
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
