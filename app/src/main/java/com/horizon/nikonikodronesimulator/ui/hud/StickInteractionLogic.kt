package com.horizon.nikonikodronesimulator.ui.hud

import androidx.compose.runtime.*
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.StickInputState
import com.horizon.nikonikodronesimulator.model.DroneRegistry
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * [v1.7.24] 搖桿交互控制中心 - 核心補丁整合版
 * 職責：整合「CSC 解鎖手勢」、「自動解鎖」與「落地自動上鎖」。
 */
@Composable
fun StickInteractionLogic(
    state: DroneState,
    stickState: StickInputState,
    onUpdateState: (DroneState.() -> Unit) -> Unit
) {
    val spec = remember(state.droneType) { DroneRegistry.getSpec(state.droneType) }
    
    // 1. 數據準備：採用無視 Rate 的原始行程 (raw) 進行安全判定
    val rawT = stickState.rawThrottle(state)
    val rawY = stickState.rawYaw(state)
    val rawP = stickState.rawPitch(state)
    val rawR = stickState.rawRoll(state)

    val sT = stickState.stickThrottle(state)
    val sY = stickState.stickYaw(state)
    val sP = stickState.stickPitch(state)
    val sR = stickState.stickRoll(state)

    // 2. CSC 解鎖手勢 (內八)：左下 (T<0, Y>0) + 右下 (P<0, R<0)
    val isCSC = (rawT < -0.7f && rawY > 0.7f && rawP < -0.7f && rawR < -0.7f)
    
    // 3. 自動狀態判定
    val isGrounded = state.altitude <= spec.groundOffset + 0.15f
    val sticksNeutral = abs(sY) < 0.2f && abs(sP) < 0.2f && abs(sR) < 0.2f
    val isStickMoving = abs(sT) > 0.3f || abs(sY) > 0.3f || abs(sP) > 0.3f || abs(sR) > 0.3f

    // 4. 教學與選單聯動：若有操作則收起選單
    LaunchedEffect(isStickMoving) {
        if (isStickMoving && state.isMenuExpanded) {
            onUpdateState { isMenuExpanded = false }
        }
    }
    
    var lastUnlockTime by remember { mutableStateOf(0L) }
    LaunchedEffect(state.isMotorLocked) {
        if (!state.isMotorLocked) {
            lastUnlockTime = System.currentTimeMillis()
            delay(500)
            onUpdateState { isMenuExpanded = false }
        }
    }

    // 5. [v1.7.24] 核心重構：自動上鎖判定 (整合落地與搖桿向下)
    // 門檻：未上鎖、已落地、油門打到底 (-0.95以下)、搖桿中位、且解鎖已過 2 秒 (避免剛解鎖即上鎖)
    val isAutoStopCondition = !state.isMotorLocked && isGrounded && rawT < -0.95f && sticksNeutral && (System.currentTimeMillis() - lastUnlockTime > 2000)

    // 6. CSC 手勢執行
    LaunchedEffect(isCSC) {
        if (isCSC) {
            delay(1200)
            if (state.isMotorLocked) {
                onUpdateState { isMotorLocked = false; systemMessage = "SAFETY_ARMED" }
            } else if (state.altitude <= spec.groundOffset + 0.15f) {
                onUpdateState { isMotorLocked = true; systemMessage = "SAFETY_DISARMED" }
            }
        }
    }

    // 7. 落地自動上鎖執行
    LaunchedEffect(isAutoStopCondition) {
        if (isAutoStopCondition) {
            delay(1000)
            if (state.altitude <= spec.groundOffset + 0.15f) {
                onUpdateState { isMotorLocked = true; systemMessage = "SAFETY_LANDING_DISARM" }
            }
        }
    }
}
