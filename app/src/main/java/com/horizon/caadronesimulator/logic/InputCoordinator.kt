package com.horizon.caadronesimulator.logic

import android.view.InputDevice
import android.view.MotionEvent
import com.horizon.caadronesimulator.model.ChannelMapping
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * [v1.7.6] 專業輸入協調中心 (Input Coordinator)
 * 職責：整合物理手把、虛擬搖桿與串口數據。
 */
object InputCoordinator {

    fun handleJoystickEvent(
        event: MotionEvent,
        state: DroneState,
        stickInput: StickInputState,
        commManager: InternalCommManager
    ): Boolean {
        // [v1.7.8] 互斥防護：若「非 HID 優先」(即使用專業模式) 且已連線，完全封鎖 HID 事件以防止數據風暴崩潰
        if (!state.isHidPriorityEnabled && state.connectionStatus == com.horizon.caadronesimulator.model.ConnectionStatus.ACTIVE) return false

        if (state.isAutoBinding == null && ((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) == 0 || state.inputMode == 1)) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false

        if (state.isAutoBinding != null) {
            handleAutoBinding(event, state)
            return true
        }

        if (state.setupWizardStep > 0 && !state.wizardWaitingForNeutral) {
            handleSetupWizard(event, state)
            return true
        }

        processMainJoystickInput(event, state, stickInput)
        
        if (!state.showSettings && !state.isCollision && !state.isCalibrating && state.setupWizardStep <= 0) {
            SafetyManager.processHidAux(event, state, stickInput) { commManager.injectLog(it) }
        }

        if (state.isCalibrating) {
            handleCalibration(event, state)
        }

        if (!state.controllerConnected) state.controllerConnected = true
        return true
    }

    fun processSerialInput(
        channels: List<Float>,
        state: DroneState,
        stickInput: StickInputState,
        commManager: InternalCommManager
    ) {
        // [v1.7.8] 歸一化架構：將 Serial 數據直接壓入緩衝池 (Index 101+)
        channels.forEachIndexed { i, v ->
            stickInput.setChannel(i + 101, v)
        }
        
        if (state.isCalibrating) {
            state.apply {
                mappingLY = updateCalib(mappingLY, mappingLY.axis - 101, channels, calibrationStep)
                mappingLX = updateCalib(mappingLX, mappingLX.axis - 101, channels, calibrationStep)
                mappingRY = updateCalib(mappingRY, mappingRY.axis - 101, channels, calibrationStep)
                mappingRX = updateCalib(mappingRX, mappingRX.axis - 101, channels, calibrationStep)
                
                mappingHold = updateCalib(mappingHold, mappingHold.axis - 101, channels, calibrationStep)
                mappingArm = updateCalib(mappingArm, mappingArm.axis - 101, channels, calibrationStep)
                mappingObsHeight = updateCalib(mappingObsHeight, mappingObsHeight.axis - 101, channels, calibrationStep)
                mappingObsTilt = updateCalib(mappingObsTilt, mappingObsTilt.axis - 101, channels, calibrationStep)
                mappingFpvTilt = updateCalib(mappingFpvTilt, mappingFpvTilt.axis - 101, channels, calibrationStep)
            }
        }

        var maxV = 0f; var maxIdx = -1
        channels.forEachIndexed { i, v -> val av = abs(v); if (av > maxV) { maxV = av; maxIdx = i } }
        if (maxV > 0.15f) state.activeAxisLabel = "CH ${maxIdx + 101}" else if (maxV < 0.05f) state.activeAxisLabel = "NONE"

        if (!state.showSettings && !state.isCollision && !state.isCalibrating && state.setupWizardStep <= 0) {
            SafetyManager.processSerialAux(channels, state, stickInput) { commManager.injectLog(it) }
        }

        if (state.inputMode == 1) {
            if (state.isAutoBinding != null) {
                handleSerialAutoBinding(channels, state)
            } else if (state.setupWizardStep > 0 && !state.wizardWaitingForNeutral) {
                // [v1.7.8] 補齊：Serial 模式下的一鍵設置響導支持
                handleSerialSetupWizard(channels, state)
            }
        }
    }

    private fun handleSerialSetupWizard(channels: List<Float>, state: DroneState) {
        val now = System.currentTimeMillis()
        if (now - state.lastWizardStepTime < 600) return

        var trig = -1; var mv = 0f
        channels.forEachIndexed { i, v -> if (abs(v) > 0.85f) { trig = i; mv = v } }
        
        if (trig != -1) {
            val isY = (state.setupWizardStep == 1 || state.setupWizardStep == 3)
            val labels = getLabelsForMode(state.joystickMode)
            val label = when(state.setupWizardStep) {
                1 -> labels[0]; 2 -> labels[1]; 3 -> labels[2]; 4 -> labels[3]
                else -> "Serial CH${trig + 1}"
            }
            val isVirtualAxis = trig >= 101
            val m = ChannelMapping(axis = trig + 101, inverted = if (isVirtualAxis) false else (mv < 0), label = label)
            
            when(state.setupWizardStep) {
                1 -> state.mappingLY = m; 2 -> state.mappingLX = m; 3 -> state.mappingRY = m; 4 -> state.mappingRX = m
            }
            state.wizardWaitingForNeutral = true
            state.lastWizardStepTime = now
        }
    }

    private fun updateCalib(m: ChannelMapping, axisIdx: Int, channels: List<Float>, step: Int): ChannelMapping {
        val targetAxis = axisIdx + 101
        if (m.axis != targetAxis) return m
        val v = channels.getOrNull(axisIdx) ?: 0f
        return when(step) { 
            1 -> m.copy(center = v, min = v, max = v)
            2 -> m.copy(min = min(v, m.min), max = max(v, m.max))
            else -> m 
        }
    }

    private fun handleAutoBinding(event: MotionEvent, state: DroneState) {
        var trig = -1; var mv = 0f
        for (i in 0..47) { val v = event.getAxisValue(i); if (abs(v) > 0.70f) { trig = i; mv = v; break } }
        if (trig != -1) {
            val key = state.isAutoBinding
            val isY = (key == "ly" || key == "ry")
            // [v1.7.9] 移除自作聰明的自動反向，初次綁定保持 inverted = false，由使用者決定是否反轉
            // 除非是明確的 Y 軸邏輯修正需求
            val m = ChannelMapping(axis = trig, inverted = false, label = "Axis $trig")
            when(key) { 
                "ly" -> state.mappingLY = m; "lx" -> state.mappingLX = m; "ry" -> state.mappingRY = m; "rx" -> state.mappingRX = m 
                "hold" -> state.mappingHold = m; "arm" -> state.mappingArm = m
                "obsHeight" -> state.mappingObsHeight = m; "obsTilt" -> state.mappingObsTilt = m
                "fpvTilt" -> state.mappingFpvTilt = m; "flightMode" -> state.mappingFlightMode = m
            }
            state.isAutoBinding = null
        }
    }

    private fun handleSerialAutoBinding(channels: List<Float>, state: DroneState) {
        // [v1.7.8] 防抖動
        val now = System.currentTimeMillis()
        if (now - state.lastWizardStepTime < 600) return
        
        var trig = -1; var mv = 0f
        channels.forEachIndexed { i, v -> if (abs(v) > 0.85f) { trig = i; mv = v } }
        if (trig != -1) {
            val key = state.isAutoBinding
            val m = ChannelMapping(axis = trig + 101, inverted = false, label = "Serial CH${trig + 1}")
            val labels = getLabelsForMode(state.joystickMode)
            val isVirtual = (trig + 101) >= 101
            when(key) { 
                "ly" -> state.mappingLY = m.copy(label = labels[0], inverted = if(isVirtual) false else (mv < 0))
                "lx" -> state.mappingLX = m.copy(label = labels[1], inverted = if(isVirtual) false else (mv < 0))
                "ry" -> state.mappingRY = m.copy(label = labels[2], inverted = if(isVirtual) false else (mv < 0))
                "rx" -> state.mappingRX = m.copy(label = labels[3], inverted = if(isVirtual) false else (mv < 0))
                "hold" -> state.mappingHold = m.copy(label = "HOLD_SWITCH")
                "arm" -> state.mappingArm = m.copy(label = "ARM_SWITCH")
                "obsHeight" -> state.mappingObsHeight = m.copy(label = "OBS_HEIGHT")
                "obsTilt" -> state.mappingObsTilt = m.copy(label = "OBS_TILT")
                "fpvTilt" -> state.mappingFpvTilt = m.copy(label = "FPV_TILT")
                "flightMode" -> state.mappingFlightMode = m.copy(label = "FLIGHT_MODE")
            }
            state.isAutoBinding = null
            state.lastWizardStepTime = now
        }
    }

    private fun getLabelsForMode(mode: Int): List<String> = when(mode) {
        1 -> listOf("PITCH", "YAW", "THROTTLE", "ROLL")
        3 -> listOf("THROTTLE", "ROLL", "PITCH", "YAW")
        4 -> listOf("PITCH", "ROLL", "THROTTLE", "YAW")
        else -> listOf("THROTTLE", "YAW", "PITCH", "ROLL")
    }

    private fun handleSetupWizard(event: MotionEvent, state: DroneState) {
        // [v1.7.8] 互斥防護：若處於專業模式 (非 HID 優先)，禁止 HID 觸發響導步驟，防止 Axis 14 幽靈信號干擾
        if (!state.isHidPriorityEnabled) return

        // [v1.7.8] 防抖動：防止雙重身分衝突導致連續跳步
        val now = System.currentTimeMillis()
        if (now - state.lastWizardStepTime < 600) return
        
        var trig = -1; var mv = 0f
        for (i in 0..47) { val v = event.getAxisValue(i); if (abs(v) > 0.70f) { trig = i; mv = v; break } }
        if (trig != -1) {
            val isY = (state.setupWizardStep == 1 || state.setupWizardStep == 3)
            // [v1.7.9] 嚮導初次綁定不再強制取反，回歸純淨映射
            val m = ChannelMapping(axis = trig, inverted = false, label = "Axis $trig")
            when(state.setupWizardStep) {
                1 -> state.mappingLY = m; 2 -> state.mappingLX = m; 3 -> state.mappingRY = m; 4 -> state.mappingRX = m
            }
            state.wizardWaitingForNeutral = true
            state.lastWizardStepTime = now
        }
    }

    private fun processMainJoystickInput(event: MotionEvent, state: DroneState, stickInput: StickInputState) {
        // [v1.7.8] 歸一化架構：將 MotionEvent 數據直接壓入全域通道緩衝池
        // 僅處理 Axis 0-47 (標準 HID 範圍)
        for (i in 0..47) {
            stickInput.setChannel(i, event.getAxisValue(i))
        }

        // [v1.7.8] 設置 Axis Label 監控 (僅供診斷顯示)
        var maxV = 0f; var maxIdx = -1
        for (i in 0..47) { val v = abs(event.getAxisValue(i)); if (v > maxV) { maxV = v; maxIdx = i } }
        if (maxV > 0.15f) state.activeAxisLabel = "Axis $maxIdx" else if (maxV < 0.05f) state.activeAxisLabel = "NONE"
    }

    private fun handleCalibration(event: MotionEvent, state: DroneState) {
        fun uC(m: ChannelMapping, d: Int): ChannelMapping { 
            // [v1.7.8] 安全加固：防止 HID 模式下存取 Serial 通道 (101+) 導致越界崩潰
            val targetAxis = if (m.axis != -1 && m.axis < 100) m.axis else d
            val v = event.getAxisValue(targetAxis)
            return when(state.calibrationStep) { 
                1 -> m.copy(center = v, min = v, max = v)
                2 -> m.copy(min = min(v, m.min), max = max(v, m.max))
                else -> m 
            } 
        }
        state.mappingLY = uC(state.mappingLY, MotionEvent.AXIS_Y)
        state.mappingLX = uC(state.mappingLX, MotionEvent.AXIS_X)
        state.mappingRY = uC(state.mappingRY, MotionEvent.AXIS_RZ)
        state.mappingRX = uC(state.mappingRX, MotionEvent.AXIS_Z)
    }
}
