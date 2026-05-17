package com.horizon.caadronesimulator.logic

import android.view.MotionEvent
import com.horizon.caadronesimulator.model.ChannelMapping
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.model.DroneCategory
import com.horizon.caadronesimulator.model.DroneRegistry
import kotlin.math.abs

/**
 * [v1.5.2] 獨立安全控制引擎 (Safety & Control Manager)
 * 職責：從 MainActivity 抽離解鎖、熄火、零油門保護、飛行模式切換與視覺參數調度。
 */
object SafetyManager {

    /**
     * 處理內置串口模式的輔助控制
     */
    fun processSerialAux(
        channels: List<Float>,
        state: DroneState,
        stickInput: StickInputState,
        logSink: (String) -> Unit
    ) {
        // [v1.5.3] 虛擬模式自動解鎖權限
        if (state.inputMode == -1 || state.inputMode == 2) {
            state.isArmSafetyPassed = true
            state.isHoldSafetyPassed = true
            return
        }

        val now = System.currentTimeMillis()
        if (now - state.lastInteractionTime < 1000) return

        fun getVal(m: ChannelMapping): Float? {
            // [防線 24] 來源身份驗證：Serial 模式僅接受 101+ 通道
            if (m.axis < 101) return null
            val raw = channels.getOrNull(m.axis - 101) ?: return null
            val v = (raw - m.center) / (if(raw >= m.center) (m.max - m.center).coerceAtLeast(0.01f) else (m.center - m.min).coerceAtLeast(0.01f))
            return if (m.inverted) -v.coerceIn(-1f, 1f) else v.coerceIn(-1f, 1f)
        }

        handleSafetyAndArming(::getVal, state, stickInput, logSink)
    }

    /**
     * 處理外接手把模式的輔助控制
     */
    fun processHidAux(
        event: MotionEvent,
        state: DroneState,
        stickInput: StickInputState,
        logSink: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        if (now - state.lastInteractionTime < 1000) return

        fun getVal(m: ChannelMapping): Float? {
            // [防線 24] 來源身份驗證：HID 模式僅接受 < 100 通道
            if (m.axis == -1 || m.axis >= 100) return null
            val v = event.getAxisValue(m.axis)
            return if (m.inverted) -v.coerceIn(-1f, 1f) else v.coerceIn(-1f, 1f)
        }

        handleSafetyAndArming(::getVal, state, stickInput, logSink)
    }

    private fun handleSafetyAndArming(
        getVal: (ChannelMapping) -> Float?,
        state: DroneState,
        stickInput: StickInputState,
        logSink: (String) -> Unit
    ) {
        val droneSpec = DroneRegistry.getSpec(state.droneType)
        
        // 1. 解鎖安全檢查與控制
        val armVal = getVal(state.mappingArm)
        if (armVal != null) {
            val isArmedRequested = armVal > 0.6f 
            val isDisarmedRequested = armVal < -0.6f

            if (!state.isArmSafetyPassed) {
                if (isDisarmedRequested) {
                    state.isArmSafetyPassed = true
                    if (state.systemMessage?.contains("請先將解鎖開關撥至 OFF") == true) state.systemMessage = "✅ 系統檢查通過"
                } else if (isArmedRequested) {
                    state.systemMessage = "⚠️ 安全警告：請先將解鎖開關撥至 OFF"
                    return 
                }
            } else {
                // [防線 21] 零油門起飛保護
                if (isArmedRequested && state.isMotorLocked) {
                    val currentThrottle = stickInput.stickThrottle(state)
                    if (currentThrottle > 0.05f) {
                        state.systemMessage = "⚠️ 安全警報：油門未歸零 (%.0f%%)".format(currentThrottle * 100)
                        return
                    }
                }
                
                if (isArmedRequested && state.isMotorLocked) {
                    state.isMotorLocked = false
                } else if (isDisarmedRequested && !state.isMotorLocked) {
                    state.isMotorLocked = true
                }
            }
        } else {
            state.isArmSafetyPassed = true
        }

        // 2. 熄火安全檢查與控制 (使用技能標籤)
        if (droneSpec.isHoldSupported) {
            val holdVal = getVal(state.mappingHold)
            if (holdVal != null) {
                val isHoldRequested = holdVal < -0.6f 
                val isNormalRequested = holdVal > 0.6f

                if (!state.isHoldSafetyPassed) {
                    if (isHoldRequested) {
                        state.isHoldSafetyPassed = true
                        if (state.systemMessage?.contains("請先將熄火開關撥至 HOLD") == true) state.systemMessage = "✅ 熄火系統就緒"
                    } else if (isNormalRequested) {
                        state.systemMessage = "⚠️ 安全警告：請先將熄火開關撥至 HOLD"
                        return
                    }
                } else {
                    if (isHoldRequested != state.isThrottleHoldActive) {
                        state.isThrottleHoldActive = isHoldRequested
                    }
                }
            } else {
                state.isHoldSafetyPassed = true
                state.isThrottleHoldActive = false 
            }
        } else {
            state.isHoldSafetyPassed = true
            state.isThrottleHoldActive = false
        }

        // 3. 視覺參數與智慧觀察員
        getVal(state.mappingObsHeight)?.let { v ->
            val targetH = 1.6f + ((v + 1f) / 2f) * (25f - 1.6f)
            if (abs(targetH - state.observerHeight) > 0.2f) {
                state.observerHeight = targetH
                if (state.cameraMode == "觀察員視角 (實驗性)") {
                    state.lastManualTouchTime = System.currentTimeMillis()
                }
            }
        }
        getVal(state.mappingObsTilt)?.let { v ->
            val targetT = v * 50f + 25f 
            if (abs(targetT - state.observerTilt) > 2.0f) {
                state.observerTilt = targetT.coerceIn(-30f, 85f)
                if (state.cameraMode == "觀察員視角 (實驗性)") {
                    state.lastManualTouchTime = System.currentTimeMillis()
                }
            }
        }
        getVal(state.mappingFpvTilt)?.let { v ->
            val targetTilt = -( (v + 1f) / 2f * 90f )
            if (abs(targetTilt - state.cameraTilt) > 1.0f) {
                state.cameraTilt = targetTilt.coerceIn(-90f, 0f)
            }
        }

        // 4. 飛行模式切換 (三段開關)
        getVal(state.mappingFlightMode)?.let { v ->
            val newMode = when {
                v < -0.3f -> "手動模式 (ACRO)"
                v > 0.3f -> "定點模式 (POS)"
                else -> "姿態模式 (ATTI)"
            }
            if (!state.diagnosticLog.contains(newMode)) {
                logSink("飛行模式切換 ➔ $newMode")
            }
        }
    }
}
