package com.horizon.caadronesimulator.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * [v1.2.95] 獨立的搖桿數據狀態 - 支援虛擬搖桿解耦運算
 */
class StickInputState {
    // --- 物理/協議數據 ---
    var rawLY by mutableFloatStateOf(0f)
    var rawLX by mutableFloatStateOf(0f)
    var rawRY by mutableFloatStateOf(0f)
    var rawRX by mutableFloatStateOf(0f)

    // --- 觸控數據 (虛擬搖桿) ---
    var touchLY by mutableFloatStateOf(0f)
    var touchLX by mutableFloatStateOf(0f)
    var touchRY by mutableFloatStateOf(0f)
    var touchRX by mutableFloatStateOf(0f)
    
    var isTouchingLeft by mutableStateOf(false)
    var isTouchingRight by mutableStateOf(false)

    // 通道數據 (24 通道)
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
            in 0..28 -> fallback // 接收來自 MainActivity 的 MotionEvent 值
            in 101..124 -> rawChannels.getOrNull(axisId - 101) ?: 0f
            else -> 0f
        }
    }

    /**
     * [v1.2.81 補強] 智慧型數據源選擇器
     */
    private fun resolveRawValue(
        mapping: ChannelMapping, 
        isTouching: Boolean, 
        touchValue: Float, 
        isInternal: Boolean,
        defaultInternalAxis: Int,
        defaultExternalAxis: Float
    ): Float {
        // 若正在觸控，觸控優先 (外部呼叫處已處理 processVirtual，此處為實體備援)
        if (isTouching) return touchValue
        
        return when {
            mapping.axis != -1 -> getRawByAxis(mapping.axis, if (mapping.axis in 0..28) defaultExternalAxis else 0f)
            isInternal -> rawChannels.getOrNull(defaultInternalAxis - 101) ?: 0f
            else -> defaultExternalAxis
        }
    }

    fun stickThrottle(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 1 || mode == 4) 103 else 104
        val isLeft = (targetAxis == 104)
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight
        
        if (isTouching) {
            return com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(
                linearVal = if (isLeft) touchLY else touchRY,
                expo = state.getExpo(if (isLeft) "ly" else "ry"),
                rate = state.getRate(if (isLeft) "ly" else "ry")
            )
        }

        val map = if (isLeft) state.mappingLY else state.mappingRY
        val raw = resolveRawValue(map, false, 0f, (state.inputMode == 1), targetAxis, if (isLeft) rawLY else rawRY)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(if (isLeft) "ly" else "ry"), state.getRate(if (isLeft) "ly" else "ry"), map)
        return if (map.inverted) -v else v
    }

    fun stickYaw(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 3 || mode == 4) 101 else 102
        val isLeft = (targetAxis == 102)
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight

        if (isTouching) {
            return com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(
                linearVal = if (isLeft) touchLX else touchRX,
                expo = state.getExpo(if (isLeft) "lx" else "rx"),
                rate = state.getRate(if (isLeft) "lx" else "rx")
            )
        }
        
        val map = if (isLeft) state.mappingLX else state.mappingRX
        val raw = resolveRawValue(map, false, 0f, (state.inputMode == 1), targetAxis, if (isLeft) rawLX else rawRX)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(if (isLeft) "lx" else "rx"), state.getRate(if (isLeft) "lx" else "rx"), map)
        return if (map.inverted) -v else v
    }

    fun stickPitch(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 1 || mode == 4) 104 else 103
        val isLeft = (targetAxis == 104)
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight

        if (isTouching) {
            return com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(
                linearVal = if (isLeft) touchLY else touchRY,
                expo = state.getExpo(if (isLeft) "ly" else "ry"),
                rate = state.getRate(if (isLeft) "ly" else "ry")
            )
        }
        
        val map = if (isLeft) state.mappingLY else state.mappingRY
        val raw = resolveRawValue(map, false, 0f, (state.inputMode == 1), targetAxis, if (isLeft) rawLY else rawRY)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(if (isLeft) "ly" else "ry"), state.getRate(if (isLeft) "ly" else "ry"), map)
        return if (map.inverted) -v else v
    }

    fun stickRoll(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 3 || mode == 4) 102 else 101
        val isLeft = (targetAxis == 102)
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight

        if (isTouching) {
            return com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(
                linearVal = if (isLeft) touchLX else touchRX,
                expo = state.getExpo(if (isLeft) "lx" else "rx"),
                rate = state.getRate(if (isLeft) "lx" else "rx")
            )
        }
        
        val map = if (isLeft) state.mappingLX else state.mappingRX
        val raw = resolveRawValue(map, false, 0f, (state.inputMode == 1), targetAxis, if (isLeft) rawLX else rawRX)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(if (isLeft) "lx" else "rx"), state.getRate(if (isLeft) "lx" else "rx"), map)
        return if (map.inverted) -v else v
    }

    // --- 視覺同步 ---
    fun stickLX(state: DroneState, ignoreSettings: Boolean = false): Float {
        val map = state.mappingLX
        val isTouching = isTouchingLeft
        if (isTouching) return com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchLX, state.getExpo("lx"), state.getRate("lx"))
        val raw = resolveRawValue(map, false, 0f, state.inputMode == 1, 102, rawLX)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("lx"), state.getRate("lx"), map, ignoreSettings)
        return if (map.inverted) -v else v
    }
    fun stickLY(state: DroneState, ignoreSettings: Boolean = false): Float {
        val map = state.mappingLY
        val isTouching = isTouchingLeft
        if (isTouching) return com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchLY, state.getExpo("ly"), state.getRate("ly"))
        val raw = resolveRawValue(map, false, 0f, state.inputMode == 1, 104, rawLY)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("ly"), state.getRate("ly"), map, ignoreSettings)
        return if (map.inverted) -v else v
    }
    fun stickRX(state: DroneState, ignoreSettings: Boolean = false): Float {
        val map = state.mappingRX
        val isTouching = isTouchingRight
        if (isTouching) return com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchRX, state.getExpo("rx"), state.getRate("rx"))
        val raw = resolveRawValue(map, false, 0f, state.inputMode == 1, 101, rawRX)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("rx"), state.getRate("rx"), map, ignoreSettings)
        return if (map.inverted) -v else v
    }
    fun stickRY(state: DroneState, ignoreSettings: Boolean = false): Float {
        val map = state.mappingRY
        val isTouching = isTouchingRight
        if (isTouching) return com.horizon.caadronesimulator.logic.InputProcessor.processVirtual(touchRY, state.getExpo("ry"), state.getRate("ry"))
        val raw = resolveRawValue(map, false, 0f, state.inputMode == 1, 103, rawRY)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("ry"), state.getRate("ry"), map, ignoreSettings)
        return if (map.inverted) -v else v
    }
}
