package com.horizon.caadronesimulator.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.horizon.caadronesimulator.logic.InputProcessor

/**
 * [v1.8.14] 搖桿數據狀態 - Git 憲法級 1:1 還原版
 * 修正：移除所有手動極性干預，100% 復刻 b02b6fd 原始信號路徑。
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

    /** [v1.2.81 補強] 智慧型數據源選擇器 (1:1 復刻 Git) */
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

    // --- [1:1 復刻 Git 映射與極性判定] ---

    fun stickThrottle(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 1 || mode == 4) 103 else 104
        val isLeft = (targetAxis == 104)
        val map = if (isLeft) state.mappingLY else state.mappingRY
        val raw = resolveRawValue(map, if(isLeft) isTouchingLeft else isTouchingRight, if(isLeft) touchLY else touchRY, (state.inputMode == 1), targetAxis, if(isLeft) rawLY else rawRY)
        // [v1.8.18] 使用語義化功能 Key "T"，確保對接到非對稱基因 (上昇/下降)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("T"), state.getRate("T", raw), map)
        return if (map.inverted) -v else v
    }

    fun stickYaw(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 3 || mode == 4) 101 else 102
        val isLeft = (targetAxis == 102)
        val map = if (isLeft) state.mappingLX else state.mappingRX
        val raw = resolveRawValue(map, if(isLeft) isTouchingLeft else isTouchingRight, if(isLeft) touchLX else touchRX, (state.inputMode == 1), targetAxis, if(isLeft) rawLX else rawRX)
        // [v1.8.18] 使用語義化功能 Key "Y"
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("Y"), state.getRate("Y", raw), map)
        return if (map.inverted) -v else v
    }

    fun stickPitch(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 1 || mode == 4) 104 else 103
        val isLeft = (targetAxis == 104)
        val map = if (isLeft) state.mappingLY else state.mappingRY
        val raw = resolveRawValue(map, if(isLeft) isTouchingLeft else isTouchingRight, if(isLeft) touchLY else touchRY, (state.inputMode == 1), targetAxis, if(isLeft) rawLY else rawRY)
        // [v1.8.18] 使用語義化功能 Key "P"
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("P"), state.getRate("P", raw), map)
        return if (map.inverted) -v else v
    }

    fun stickRoll(state: DroneState): Float {
        val mode = state.joystickMode
        val targetAxis = if (mode == 3 || mode == 4) 102 else 101
        val isLeft = (targetAxis == 102)
        val map = if (isLeft) state.mappingLX else state.mappingRX
        val raw = resolveRawValue(map, if(isLeft) isTouchingLeft else isTouchingRight, if(isLeft) touchLX else touchRX, (state.inputMode == 1), targetAxis, if(isLeft) rawLX else rawRX)
        // [v1.8.18] 使用語義化功能 Key "R"
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("R"), state.getRate("R", raw), map)
        return if (map.inverted) -v else v
    }

    // --- 視覺同步接口 ---
    fun stickLX(state: DroneState): Float {
        val map = state.mappingLX
        val raw = resolveRawValue(map, isTouchingLeft, touchLX, state.inputMode == 1, 102, rawLX)
        // 視覺指針同樣語義化，確保 UI 與物理感官同步
        val func = when(state.joystickMode) { 1 -> "Y"; 2 -> "Y"; 3 -> "R"; 4 -> "R"; else -> "Y" }
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(func), state.getRate(func, raw), map)
        return if (map.inverted) -v else v
    }
    fun stickLY(state: DroneState): Float {
        val map = state.mappingLY
        val raw = resolveRawValue(map, isTouchingLeft, touchLY, state.inputMode == 1, 104, rawLY)
        val func = when(state.joystickMode) { 1 -> "P"; 2 -> "T"; 3 -> "T"; 4 -> "P"; else -> "T" }
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(func), state.getRate(func, raw), map)
        return if (map.inverted) -v else v
    }
    fun stickRX(state: DroneState): Float {
        val map = state.mappingRX
        val raw = resolveRawValue(map, isTouchingRight, touchRX, state.inputMode == 1, 101, rawRX)
        val func = when(state.joystickMode) { 1 -> "R"; 2 -> "R"; 3 -> "Y"; 4 -> "Y"; else -> "R" }
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(func), state.getRate(func, raw), map)
        return if (map.inverted) -v else v
    }
    fun stickRY(state: DroneState): Float {
        val map = state.mappingRY
        val raw = resolveRawValue(map, isTouchingRight, touchRY, state.inputMode == 1, 103, rawRY)
        val func = when(state.joystickMode) { 1 -> "T"; 2 -> "P"; 3 -> "P"; 4 -> "T"; else -> "P" }
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(func), state.getRate(func, raw), map)
        return if (map.inverted) -v else v
    }
}
