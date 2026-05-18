package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.model.DroneRegistry
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun JoystickOverlay(
    state: DroneState,
    stickState: StickInputState
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 40.dp, start = 40.dp)) {
            VirtualJoystick(
                stickX = stickState.stickLX(state), 
                stickY = stickState.stickLY(state),
                onDragStateChange = { stickState.isTouchingLeft = it },
                onValueChange = { x, y -> stickState.touchLX = x; stickState.touchLY = y }
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp, end = 40.dp)) {
            VirtualJoystick(
                stickX = stickState.stickRX(state), 
                stickY = stickState.stickRY(state),
                onDragStateChange = { stickState.isTouchingRight = it },
                onValueChange = { x, y -> stickState.touchRX = x; stickState.touchRY = y }
            )
        }
    }
}

/**
 * 獨立的搖桿底層邏輯
 * [v1.5.9] 修正解鎖極性：對齊已翻轉的 Roll 軸，確保內八起槳。
 */
@Composable
fun StickInteractionLogic(
    state: DroneState,
    stickState: StickInputState,
    onUpdateState: (DroneState.() -> Unit) -> Unit
) {
    val latestState by rememberUpdatedState(state)
    val spec = DroneRegistry.getSpec(state.droneType)
    
    val sT = stickState.stickThrottle(state)
    val sY = stickState.stickYaw(state)
    val sP = stickState.stickPitch(state)
    val sR = stickState.stickRoll(state)

    // [重要修正] 解鎖手勢極性校準
    // 因為 Roll 軸極性已翻轉 (sR 變為正值代表內推)，解鎖判定需同步修正
    val isCSC = (sT < -0.7f && sY > 0.7f && sP < -0.7f && sR > 0.7f)
    
    val isGrounded = state.altitude <= spec.groundOffset + 0.15f
    val sticksNeutral = abs(sY) < 0.2f && abs(sP) < 0.2f && abs(sR) < 0.2f

    val isStickMoving = abs(sT) > 0.3f || abs(sY) > 0.3f || abs(sP) > 0.3f || abs(sR) > 0.3f
    LaunchedEffect(isStickMoving) {
        if (isStickMoving && state.isMenuExpanded) {
            onUpdateState { isMenuExpanded = false }
        }
    }
    
    var lastUnlockTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isMotorLocked) {
        if (!state.isMotorLocked) {
            lastUnlockTime = System.currentTimeMillis()
            delay(500)
            onUpdateState { isMenuExpanded = false }
        }
    }

    val isAutoStop = !state.isMotorLocked && isGrounded && sT < -0.95f && sticksNeutral && (System.currentTimeMillis() - lastUnlockTime > 2000)

    LaunchedEffect(isCSC) {
        if (isCSC) {
            delay(1200)
            if (latestState.isMotorLocked) {
                onUpdateState { isMotorLocked = false; systemMessage = "已解鎖" }
            } else if (isGrounded) {
                onUpdateState { isMotorLocked = true; systemMessage = "已上鎖" }
            }
        }
    }

    LaunchedEffect(isAutoStop) {
        if (isAutoStop) {
            delay(1000)
            if (latestState.altitude <= spec.groundOffset + 0.15f) {
                onUpdateState { isMotorLocked = true; systemMessage = "自動停槳" }
            }
        }
    }
}
