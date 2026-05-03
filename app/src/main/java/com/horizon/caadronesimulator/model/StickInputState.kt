package com.horizon.caadronesimulator.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.horizon.caadronesimulator.logic.InputProcessor

/**
 * [v1.2.68] 獨立的搖桿數據狀態 (效能與校準優化版)
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
     * 支援實體/觸控優先權判定，並提供映射為 -1 時的自動備援。
     */
    private fun resolveRawValue(
        mapping: ChannelMapping, 
        isTouching: Boolean, 
        touchValue: Float, 
        isInternal: Boolean,
        defaultInternalAxis: Int,
        defaultExternalAxis: Float // 這裡傳入對應的 rawLX/LY 等
    ): Float {
        // 1. 若正在觸控，觸控優先
        if (isTouching) return touchValue
        
        // 2. 判定映射路徑
        return when {
            // 已明確映射
            mapping.axis != -1 -> getRawByAxis(mapping.axis, if (mapping.axis in 0..28) defaultExternalAxis else 0f)
            
            // 未映射 (-1) 時的自動備援
            isInternal -> rawChannels.getOrNull(defaultInternalAxis - 101) ?: 0f
            else -> defaultExternalAxis
        }
    }

    fun stickThrottle(state: DroneState): Float {
        val mode = state.joystickMode
        // Mode 1,4: 右垂直(S3=103); Mode 2,3: 左垂直(S4=104)
        val targetAxis = if (mode == 1 || mode == 4) 103 else 104
        val isLeft = (targetAxis == 104)
        
        val map = if (isLeft) state.mappingLY else state.mappingRY
        val raw = resolveRawValue(
            mapping = map,
            isTouching = if(isLeft) isTouchingLeft else isTouchingRight,
            touchValue = if(isLeft) touchLY else touchRY,
            isInternal = (state.inputMode == 1),
            defaultInternalAxis = targetAxis,
            defaultExternalAxis = if(isLeft) rawLY else rawRY
        )
        
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(if(isLeft) "ly" else "ry"), state.getRate(if(isLeft) "ly" else "ry"), map)
        return if (map.inverted) -v else v
    }

    fun stickYaw(state: DroneState): Float {
        val mode = state.joystickMode
        // Mode 1,2: 左水平(S2=102); Mode 3,4: 右水平(S1=101)
        val targetAxis = if (mode == 3 || mode == 4) 101 else 102
        val isLeft = (targetAxis == 102)
        
        val map = if (isLeft) state.mappingLX else state.mappingRX
        val raw = resolveRawValue(
            mapping = map,
            isTouching = if(isLeft) isTouchingLeft else isTouchingRight,
            touchValue = if(isLeft) touchLX else touchRX,
            isInternal = (state.inputMode == 1),
            defaultInternalAxis = targetAxis,
            defaultExternalAxis = if(isLeft) rawLX else rawRX
        )
                 
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(if(isLeft) "lx" else "rx"), state.getRate(if(isLeft) "lx" else "rx"), map)
        return if (map.inverted) -v else v
    }

    fun stickPitch(state: DroneState): Float {
        val mode = state.joystickMode
        // Mode 1,4: 左垂直(S4=104); Mode 2,3: 右垂直(S3=103)
        val targetAxis = if (mode == 1 || mode == 4) 104 else 103
        val isLeft = (targetAxis == 104)
        
        val map = if (isLeft) state.mappingLY else state.mappingRY
        val raw = resolveRawValue(
            mapping = map,
            isTouching = if(isLeft) isTouchingLeft else isTouchingRight,
            touchValue = if(isLeft) touchLY else touchRY,
            isInternal = (state.inputMode == 1),
            defaultInternalAxis = targetAxis,
            defaultExternalAxis = if(isLeft) rawLY else rawRY
        )
                 
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(if(isLeft) "ly" else "ry"), state.getRate(if(isLeft) "ly" else "ry"), map)
        return if (map.inverted) -v else v
    }

    fun stickRoll(state: DroneState): Float {
        val mode = state.joystickMode
        // Mode 1,2: 右水平(S1=101); Mode 3,4: 左水平(S2=102)
        val targetAxis = if (mode == 3 || mode == 4) 102 else 101
        val isLeft = (targetAxis == 102)
        
        val map = if (isLeft) state.mappingLX else state.mappingRX
        val raw = resolveRawValue(
            mapping = map,
            isTouching = if(isLeft) isTouchingLeft else isTouchingRight,
            touchValue = if(isLeft) touchLX else touchRX,
            isInternal = (state.inputMode == 1),
            defaultInternalAxis = targetAxis,
            defaultExternalAxis = if(isLeft) rawLX else rawRX
        )
                 
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo(if(isLeft) "lx" else "rx"), state.getRate(if(isLeft) "lx" else "rx"), map)
        return if (map.inverted) -v else v
    }



    // --- 視覺同步 (含 "視、控絕對對齊" 邏輯) ---

    fun stickLX(state: DroneState, ignoreSettings: Boolean = false): Float {
        val map = state.mappingLX
        val raw = resolveRawValue(map, isTouchingLeft, touchLX, state.inputMode == 1, 102, rawLX)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("lx"), state.getRate("lx"), map, ignoreSettings)
        return if (map.inverted) -v else v
    }
    fun stickLY(state: DroneState, ignoreSettings: Boolean = false): Float {
        val map = state.mappingLY
        val raw = resolveRawValue(map, isTouchingLeft, touchLY, state.inputMode == 1, 104, rawLY)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("ly"), state.getRate("ly"), map, ignoreSettings)
        return if (map.inverted) -v else v
    }
    fun stickRX(state: DroneState, ignoreSettings: Boolean = false): Float {
        val map = state.mappingRX
        val raw = resolveRawValue(map, isTouchingRight, touchRX, state.inputMode == 1, 101, rawRX)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("rx"), state.getRate("rx"), map, ignoreSettings)
        return if (map.inverted) -v else v
    }
    fun stickRY(state: DroneState, ignoreSettings: Boolean = false): Float {
        val map = state.mappingRY
        val raw = resolveRawValue(map, isTouchingRight, touchRY, state.inputMode == 1, 103, rawRY)
        val v = com.horizon.caadronesimulator.logic.InputProcessor.process(raw, state.joystickDeadzone, state.getExpo("ry"), state.getRate("ry"), map, ignoreSettings)
        return if (map.inverted) -v else v
    }

}
