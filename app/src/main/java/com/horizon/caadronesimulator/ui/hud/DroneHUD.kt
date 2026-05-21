package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.model.DroneRegistry
import java.util.Locale
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
    onUpdateZoomPipRect: (android.graphics.Rect?) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 儀表層 (MFD)
        InstrumentsLayer(
            state = state,
            onUpdatePipRect = onUpdatePipRect,
            onUpdateState = onUpdateState
        )

        // 2. 虛擬搖桿
        if (state.showVirtualJoysticks) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 40.dp)) {
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
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // [v1.7.6] 校準：Zoom Assistant 則維持中心 Z=6 的觸發
            val distToOpsCenter = sqrt(state.posX.pow(2) + (state.posZ - 6f).pow(2))
            val isInZoomZone = state.enableZoomAssistant && distToOpsCenter > 10.0f && state.cameraMode != AppConfig.CAM_MODE_FPV && state.cameraMode != AppConfig.CAM_MODE_FOLLOW && !state.showSettings
            
            val currentSpec = DroneRegistry.getSpec(state.droneType)
            val isZoomRelocated = state.autoPiPRelocate && (state.observerTilt < -5f || (state.altitude - currentSpec.groundOffset) > 10f)
            val zoomAlign = if (isZoomRelocated) Alignment.TopEnd else Alignment.TopCenter
            val zoomPad = if (isZoomRelocated) Modifier.padding(end = 65.dp, top = 16.dp) else Modifier.padding(top = 10.dp)

            if (isInZoomZone) {
                PrecisionZoomView(
                    state = state,
                    modifier = zoomPad.align(zoomAlign),
                    onUpdateRect = { r -> 
                        onUpdateZoomPipRect(r?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }) 
                    }
                )
            } else {
                onUpdateZoomPipRect(null)
            }

            Column(modifier = Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.isNearBoundary) {
                    Spacer(Modifier.height(80.dp))
                    Text(
                        stringResource(R.string.hud_boundary_warning),
                        color = Color.Red,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
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
