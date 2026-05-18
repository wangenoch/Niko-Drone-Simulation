package com.horizon.caadronesimulator.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.horizon.caadronesimulator.logic.InputProcessor

/**
 * [v1.6.1] 搖桿數據狀態 - 指令中樞層
 * 職責：作為唯一的邏輯運算點，執行 Inversion, Deadzone, Expo 與 Rate。
 * 修正：徹底分離「觸控」與「實體」路徑，觸控不再受 Invert 設定影響，並跳過物理校準。
 */
class StickInputState {
    // --- 物理/協議數據 ---
    var rawLY by mutableFloatStateOf(0f)
    var rawLX by mutableFloatStateOf(0f)
    var rawRY by mutableFloatStateOf(0f)
    var rawRX by mutableFloatStateOf(0f)

    // --- 觸控數據 ---
    var touchLY by mutableFloatStateOf(0f)
    var touchLX by mutableFloatStateOf(0f)
    var touchRY by mutableFloatStateOf(0f)
    var touchRX by mutableFloatStateOf(0f)
    
    var isTouchingLeft by mutableStateOf(false)
    var isTouchingRight by mutableStateOf(false)

    // 通道數據
    var rawChannels by mutableStateOf(List(24) { 0f })

    private var _pps by mutableIntStateOf(0)
    var packetsPerSecond: Int 
        get() = _pps
        set(value) {
            val now = System.currentTimeMillis()
            if (Math.abs(value - _pps) > 5 || now - _lastPpsUpdateTime > 500) {
                _pps = value
                _lastPpsUpdateTime = now
            }
        }
    private var _lastPpsUpdateTime = 0L

    var isSignalActive by mutableStateOf(false)
    var serialByteCount by mutableLongStateOf(0L)

    fun updateRaw(ly: Float, lx: Float, ry: Float, rx: Float) {
        rawLY = ly; rawLX = lx; rawRY = ry; rawRX = rx
    }

    private fun getRawByAxis(axisId: Int, fallback: Float): Float {
        return when (axisId) {
            in 0..28 -> fallback 
            in 101..124 -> rawChannels.getOrNull(axisId - 101) ?: 0f
            else -> 0f
        }
    }

    /** [v1.6.1] 智慧型數據源選擇器 (優先權：觸碰 > 物理映射 > 預設通道) */
    private fun resolveRawValue(
        mapping: ChannelMapping, 
        isTouching: Boolean, 
        touchValue: Float, 
        isInternal: Boolean,
        defaultInternalAxis: Int,
        defaultExternalAxis: Float 
    ): Float {
        if (isTouching) return touchValue
        return when {
            mapping.axis != -1 -> getRawByAxis(mapping.axis, if (mapping.axis in 0..28) defaultExternalAxis else 0f)
            isInternal -> rawChannels.getOrNull(defaultInternalAxis - 101) ?: 0f
            else -> defaultExternalAxis
        }
    }

    // --- [v1.6.1] 邏輯運算核心：唯一 Inversion 與 Expo/Rate 實施點 ---
    
    fun stickThrottle(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 1 || mode == 4) 103 else 104
        val isLeft = (targetAxis == 104)
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight
        val map = if (isLeft) state.mappingLY else state.mappingRY

        return if (isTouching) {
            val touchVal = if (isLeft) touchLY else touchRY
            com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchVal, state.getExpo("T"), state.getRate("T", touchVal))
        } else {
            val raw = resolveRawValue(map, false, 0f, (state.inputMode == 1), targetAxis, if(isLeft) rawLY else rawRY)
            val processed = if (map.inverted) -raw else raw
            com.horizon.caadronesimulator.logic.InputProcessor.process(processed, state.joystickDeadzone, state.getExpo("T"), state.getRate("T", processed), map)
        }
    }

    fun stickYaw(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 3 || mode == 4) 101 else 102
        val isLeft = (targetAxis == 102)
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight
        val map = if (isLeft) state.mappingLX else state.mappingRX

        return if (isTouching) {
            val touchVal = if (isLeft) touchLX else touchRX
            com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchVal, state.getExpo("Y"), state.getRate("Y", touchVal))
        } else {
            val raw = resolveRawValue(map, false, 0f, (state.inputMode == 1), targetAxis, if(isLeft) rawLX else rawRX)
            val processed = if (map.inverted) -raw else raw
            com.horizon.caadronesimulator.logic.InputProcessor.process(processed, state.joystickDeadzone, state.getExpo("Y"), state.getRate("Y", processed), map)
        }
    }

    fun stickPitch(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 1 || mode == 4) 104 else 103
        val isLeft = (targetAxis == 104)
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight
        val map = if (isLeft) state.mappingLY else state.mappingRY

        return if (isTouching) {
            val touchVal = if (isLeft) touchLY else touchRY
            com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchVal, state.getExpo("P"), state.getRate("P", touchVal))
        } else {
            val raw = resolveRawValue(map, false, 0f, (state.inputMode == 1), targetAxis, if(isLeft) rawLY else rawRY)
            val processed = if (map.inverted) -raw else raw
            com.horizon.caadronesimulator.logic.InputProcessor.process(processed, state.joystickDeadzone, state.getExpo("P"), state.getRate("P", processed), map)
        }
    }

    fun stickRoll(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 3 || mode == 4) 102 else 101
        val isLeft = (targetAxis == 102)
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight
        val map = if (isLeft) state.mappingLX else state.mappingRX

        return if (isTouching) {
            val touchVal = if (isLeft) touchLX else touchRX
            com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchVal, state.getExpo("R"), state.getRate("R", touchVal))
        } else {
            val raw = resolveRawValue(map, false, 0f, (state.inputMode == 1), targetAxis, if(isLeft) rawLX else rawRX)
            val processed = if (map.inverted) -raw else raw
            com.horizon.caadronesimulator.logic.InputProcessor.process(processed, state.joystickDeadzone, state.getExpo("R"), state.getRate("R", processed), map)
        }
    }

    // --- [v1.6.1] 視覺同步接口： HUD 虛擬搖桿專用 (反映最終指令) ---
    
    fun stickLX(state: DroneState): Float {
        val map = state.mappingLX
        val func = when(state.joystickMode) { 1 -> "Y"; 2 -> "Y"; 3 -> "R"; 4 -> "R"; else -> "Y" }
        return if (isTouchingLeft) {
            com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchLX, state.getExpo(func), state.getRate(func, touchLX))
        } else {
            val raw = resolveRawValue(map, false, 0f, state.inputMode == 1, 102, rawLX)
            val processed = if (map.inverted) -raw else raw
            com.horizon.caadronesimulator.logic.InputProcessor.process(processed, state.joystickDeadzone, state.getExpo(func), state.getRate(func, processed), map)
        }
    }
    fun stickLY(state: DroneState): Float {
        val map = state.mappingLY
        val func = when(state.joystickMode) { 1 -> "P"; 2 -> "T"; 3 -> "T"; 4 -> "P"; else -> "T" }
        return if (isTouchingLeft) {
            com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchLY, state.getExpo(func), state.getRate(func, touchLY))
        } else {
            val raw = resolveRawValue(map, false, 0f, state.inputMode == 1, 104, rawLY)
            val processed = if (map.inverted) -raw else raw
            com.horizon.caadronesimulator.logic.InputProcessor.process(processed, state.joystickDeadzone, state.getExpo(func), state.getRate(func, processed), map)
        }
    }
    fun stickRX(state: DroneState): Float {
        val map = state.mappingRX
        val func = when(state.joystickMode) { 1 -> "R"; 2 -> "R"; 3 -> "Y"; 4 -> "Y"; else -> "R" }
        return if (isTouchingRight) {
            com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchRX, state.getExpo(func), state.getRate(func, touchRX))
        } else {
            val raw = resolveRawValue(map, false, 0f, state.inputMode == 1, 101, rawRX)
            val processed = if (map.inverted) -raw else raw
            com.horizon.caadronesimulator.logic.InputProcessor.process(processed, state.joystickDeadzone, state.getExpo(func), state.getRate(func, processed), map)
        }
    }
    fun stickRY(state: DroneState): Float {
        val map = state.mappingRY
        val func = when(state.joystickMode) { 1 -> "T"; 2 -> "P"; 3 -> "P"; 4 -> "T"; else -> "P" }
        return if (isTouchingRight) {
            com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchRY, state.getExpo(func), state.getRate(func, touchRY))
        } else {
            val raw = resolveRawValue(map, false, 0f, state.inputMode == 1, 103, rawRY)
            val processed = if (map.inverted) -raw else raw
            com.horizon.caadronesimulator.logic.InputProcessor.process(processed, state.joystickDeadzone, state.getExpo(func), state.getRate(func, processed), map)
        }
    }
}
