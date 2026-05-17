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
 * [v1.5.3] 專業輸入協調中心 (Fixed Inversion Logic)
 */
object InputCoordinator {

    fun handleJoystickEvent(
        event: MotionEvent,
        state: DroneState,
        stickInput: StickInputState,
        usbSerial: UsbSerialManager
    ): Boolean {
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
            SafetyManager.processHidAux(event, state, stickInput) { usbSerial.injectLog(it) }
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
        usbSerial: UsbSerialManager
    ) {
        stickInput.rawChannels = channels
        
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
            SafetyManager.processSerialAux(channels, state, stickInput) { usbSerial.injectLog(it) }
        }

        if (state.inputMode == 1 && state.isAutoBinding != null) {
            handleSerialAutoBinding(channels, state)
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
            // [v1.5.3] 初次綁定自動識別極性
            val m = ChannelMapping(axis = trig, inverted = (if (isY) -mv else mv) < 0, label = "Axis $trig")
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
        var trig = -1; var mv = 0f
        channels.forEachIndexed { i, v -> if (abs(v) > 0.85f) { trig = i; mv = v } }
        if (trig != -1) {
            val key = state.isAutoBinding
            val m = ChannelMapping(axis = trig + 101, inverted = false, label = "Serial CH${trig + 1}")
            val labels = getLabelsForMode(state.joystickMode)
            when(key) { 
                "ly" -> state.mappingLY = m.copy(label = labels[0], inverted = mv < 0)
                "lx" -> state.mappingLX = m.copy(label = labels[1], inverted = mv < 0)
                "ry" -> state.mappingRY = m.copy(label = labels[2], inverted = mv < 0)
                "rx" -> state.mappingRX = m.copy(label = labels[3], inverted = mv < 0)
                "hold" -> state.mappingHold = m.copy(label = "熄火開關")
                "arm" -> state.mappingArm = m.copy(label = "解鎖開關")
                "obsHeight" -> state.mappingObsHeight = m.copy(label = "站位高度")
                "obsTilt" -> state.mappingObsTilt = m.copy(label = "抬頭角度")
                "fpvTilt" -> state.mappingFpvTilt = m.copy(label = "FPV 雲台")
                "flightMode" -> state.mappingFlightMode = m.copy(label = "飛行模式")
            }
            state.isAutoBinding = null
        }
    }

    private fun getLabelsForMode(mode: Int): List<String> = when(mode) {
        1 -> listOf("俯仰 Pitch", "航向 Yaw", "油門 Throttle", "橫滾 Roll")
        3 -> listOf("油門 Throttle", "橫滾 Roll", "俯仰 Pitch", "航向 Yaw")
        4 -> listOf("俯仰 Pitch", "橫滾 Roll", "油門 Throttle", "航向 Yaw")
        else -> listOf("油門 Throttle", "航向 Yaw", "俯仰 Pitch", "橫滾 Roll")
    }

    private fun handleSetupWizard(event: MotionEvent, state: DroneState) {
        var trig = -1; var mv = 0f
        for (i in 0..47) { val v = event.getAxisValue(i); if (abs(v) > 0.70f) { trig = i; mv = v; break } }
        if (trig != -1) {
            val isY = (state.setupWizardStep == 1 || state.setupWizardStep == 3)
            val m = ChannelMapping(axis = trig, inverted = (if (isY) -mv else mv) < 0, label = "Axis $trig")
            when(state.setupWizardStep) {
                1 -> state.mappingLY = m; 2 -> state.mappingLX = m; 3 -> state.mappingRY = m; 4 -> state.mappingRX = m
            }
            state.wizardWaitingForNeutral = true
        }
    }

    private fun processMainJoystickInput(event: MotionEvent, state: DroneState, stickInput: StickInputState) {
        fun gV(m: ChannelMapping, d: Int, y: Boolean = false): Float { 
            val raw = event.getAxisValue(if (m.axis != -1 && m.axis < 100) m.axis else d)
            val processed = if (m.inverted) -raw else raw
            val posKey = when(d) {
                MotionEvent.AXIS_Y -> "ly"; MotionEvent.AXIS_X -> "lx"
                MotionEvent.AXIS_RZ -> "ry"; MotionEvent.AXIS_Z -> "rx"; else -> ""
            }
            
            // [v1.8.17] 語義化功能映射：根據 Mode 1-4 將物理軸向轉換為飛行功能 (T/Y/P/R)
            // 這確保了 (飛機特徵 => 專屬曲線 => 靈敏度) 流程能正確對接到對應的功能基因
            val funcKey = when(state.joystickMode) {
                1 -> when(posKey) { "ly" -> "P"; "lx" -> "Y"; "ry" -> "T"; "rx" -> "R"; else -> "" }
                2 -> when(posKey) { "ly" -> "T"; "lx" -> "Y"; "ry" -> "P"; "rx" -> "R"; else -> "" }
                3 -> when(posKey) { "ly" -> "T"; "lx" -> "R"; "ry" -> "P"; "rx" -> "Y"; else -> "" }
                4 -> when(posKey) { "ly" -> "P"; "lx" -> "R"; "ry" -> "T"; "rx" -> "Y"; else -> "" }
                else -> posKey
            }
            
            val rate = state.getRate(funcKey, processed)
            val expo = state.getExpo(funcKey)
            
            // 應用 Expo 曲線
            val absV = abs(processed)
            val curved = (expo * absV * absV * absV + (1f - expo) * absV) * rate
            val finalVal = (if (processed < 0) -curved else curved).coerceIn(-1f, 1f)
            
            return if (y) -finalVal else finalVal
        }

        stickInput.updateRaw(
            gV(state.mappingLY, MotionEvent.AXIS_Y, true), 
            gV(state.mappingLX, MotionEvent.AXIS_X), 
            gV(state.mappingRY, MotionEvent.AXIS_RZ, true), 
            gV(state.mappingRX, MotionEvent.AXIS_Z)
        )

        stickInput.rawChannels = List(48) { i -> event.getAxisValue(i) }
        var maxV = 0f; var maxIdx = -1
        for (i in 0..47) { val v = abs(event.getAxisValue(i)); if (v > maxV) { maxV = v; maxIdx = i } }
        if (maxV > 0.15f) state.activeAxisLabel = "Axis $maxIdx" else if (maxV < 0.05f) state.activeAxisLabel = "NONE"
    }

    private fun handleCalibration(event: MotionEvent, state: DroneState) {
        fun uC(m: ChannelMapping, d: Int): ChannelMapping { 
            val v = event.getAxisValue(if (m.axis != -1) m.axis else d)
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
