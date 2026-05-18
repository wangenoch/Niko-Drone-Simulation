package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.model.DroneRegistry
import java.util.Locale
import kotlin.math.*

/**
 * [v1.5.9] 飛行抬頭顯示器 - Git 憲法還原版
 * 修正：完全恢復 Git 原始的數據條組件與觸碰回調路徑，確保控制絕對同步。
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
    onUpdatePipRect: (androidx.compose.ui.geometry.Rect?) -> Unit,
    onUpdateZoomPipRect: (androidx.compose.ui.geometry.Rect?) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 儀表層 (MFD)
        InstrumentsLayer(
            state = state,
            onUpdatePipRect = { r -> onUpdatePipRect(r?.let { androidx.compose.ui.geometry.Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }) },
            onUpdateState = onUpdateState
        )

        // 2. 虛擬搖桿 (100% 恢復回調)
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

        // 3. 高頻數據欄 (100% 恢復組件)
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            OriginalStatusHUD(
                state = state,
                isVisible = isStatusVisible,
                onToggleVisible = { onToggleStatus() }
            )
        }

        // 4. 頂部狀態顯示
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            Column(modifier = Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                // 邊界警告提示 (原始設定)
                if (state.isNearBoundary) {
                    Spacer(Modifier.height(80.dp))
                    Text(
                        "警告：接近空域邊界",
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
fun ThrottledStatusInfoBar(
    altitude: Float, posX: Float, posZ: Float, speed: Float,
    windLevel: Int, windDirection: String,
    isMotorLocked: Boolean, droneType: String,
    isVisible: Boolean, onToggle: () -> Unit
) {
    var displayAlt by remember { mutableFloatStateOf(0f) }
    var displaySpeed by remember { mutableFloatStateOf(0f) }
    var displayDist by remember { mutableFloatStateOf(0f) }
    val spec = remember(droneType) { DroneRegistry.getSpec(droneType) }

    LaunchedEffect(altitude, posX, posZ, speed) {
        displayAlt = (altitude - spec.groundOffset).coerceAtLeast(0f)
        displaySpeed = speed
        displayDist = sqrt(posX.pow(2) + (posZ + 6f).pow(2))
    }

    if (isVisible) {
        Row(
            modifier = Modifier
                .background(Color(0xAA111111), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusItem("高度", String.format(Locale.US, "%.1f m", displayAlt), if(displayAlt >= 29.8f) Color.Red else Color.Cyan)
            StatusVerticalDivider()
            StatusItem("速度", String.format(Locale.US, "%.1f m/s", displaySpeed), Color.White)
            StatusVerticalDivider()
            StatusItem("距離", String.format(Locale.US, "%.1f m", displayDist), Color.White)
            StatusVerticalDivider()
            StatusItem("環境", "L$windLevel $windDirection", if(windLevel > 3) Color.Red else Color.White)
            StatusVerticalDivider()
            StatusItem("馬達", if(isMotorLocked) "鎖定" else "解鎖", if(isMotorLocked) Color.Red else Color.Green)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(16.dp))
        }
    } else {
        Box(modifier = Modifier.size(44.dp, 20.dp).background(Color(0xAA111111), RoundedCornerShape(10.dp)).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(10.dp)).clickable { onToggle() }, contentAlignment = Alignment.Center) {
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
