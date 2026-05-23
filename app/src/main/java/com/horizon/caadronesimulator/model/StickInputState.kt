package com.horizon.caadronesimulator.model

import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.horizon.caadronesimulator.logic.InputProcessor

/**
 * [v1.7.8] 搖桿數據狀態 - 指令中樞層 (歸一化緩衝池版)
 * 職責：作為唯一的邏輯運算點，執行 Inversion, Deadzone, Expo 與 Rate。
 * 修正：廢除碎片化變量，建立「全域通道緩衝池」，確保所有消費者（UI/物理）數據 100% 同步。
 */
class StickInputState {
    // --- 全域通道緩衝池 (唯一的數據真源) ---
    // Index 0-47: 系統 HID 軸位 (映射自 MotionEvent)
    // Index 101-124: 專業 Serial 通道 (映射自 UsbSerial / Internal)
    private val channelBuffer = FloatArray(125) { 0f }

    // --- 視覺同步層 (節流狀態) ---
    // 僅用於 Compose UI 重繪，防止渲染風暴
    var visualBuffer by mutableStateOf(List(125) { 0f })
    private var lastUiUpdateTime = 0L
    private val UI_UPDATE_INTERVAL_MS = 16L // ~60Hz 穩定門戶

    // --- 觸控數據 ---
    var touchLY by mutableFloatStateOf(0f); var touchLX by mutableFloatStateOf(0f)
    var touchRY by mutableFloatStateOf(0f); var touchRX by mutableFloatStateOf(0f)
    var isTouchingLeft by mutableStateOf(false); var isTouchingRight by mutableStateOf(false)

    // --- 狀態監控 ---
    private var _pps by mutableIntStateOf(0)
    var packetsPerSecond: Int get() = _pps; set(v) { val now = System.currentTimeMillis(); if (Math.abs(v - _pps) > 5 || now - _lastPpsUpdateTime > 500) { _pps = v; _lastPpsUpdateTime = now } }
    private var _lastPpsUpdateTime = 0L
    var isSignalActive by mutableStateOf(false); var serialByteCount by mutableLongStateOf(0L)

    /** [v1.7.8] 統一寫入接口：更新指定的通道數值 */
    fun setChannel(index: Int, value: Float) {
        if (index < 0 || index >= 125) return
        channelBuffer[index] = value
        
        // 觸發視覺節流同步
        val now = System.currentTimeMillis()
        if (now - lastUiUpdateTime >= UI_UPDATE_INTERVAL_MS) {
            visualBuffer = channelBuffer.toList()
            lastUiUpdateTime = now
        }
    }

    /** [v1.7.8] 強制清空所有數據 (切換模式專用) */
    fun resetAll() {
        channelBuffer.fill(0f)
        visualBuffer = channelBuffer.toList()
        touchLY = 0f; touchLX = 0f; touchRY = 0f; touchRX = 0f
        isTouchingLeft = false; isTouchingRight = false
        lastUiUpdateTime = 0L
    }

    /** [v1.7.8] 兼容性接口：映射至標準 HID 軸位 (僅供 Pro 版內部路徑或舊版邏輯使用) */
    fun updateRaw(ly: Float, lx: Float, ry: Float, rx: Float) {
        setChannel(1, ly)  // AXIS_Y
        setChannel(0, lx)  // AXIS_X
        setChannel(14, ry) // AXIS_RZ
        setChannel(11, rx) // AXIS_Z
    }

    // --- [v1.7.8] 歸一化解析器：支援物理/視覺分流 ---

    private fun resolveValue(state: DroneState, mapping: ChannelMapping, isLeft: Boolean, func: String, isVisual: Boolean, applyCurves: Boolean = true): Float {
        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight
        if (isTouching) {
            val touchVal = when(func) { "T" -> if(isLeft) touchLY else touchRY; "Y" -> if(isLeft) touchLX else touchRX; "P" -> if(isLeft) touchLY else touchRY; else -> if(isLeft) touchLX else touchRX }
            return if (applyCurves) InputProcessor.processVirtual(touchVal, state.getExpo(func), state.getRate(func, touchVal)) else touchVal
        }

        // 讀取池子
        val buffer = if (isVisual) visualBuffer else channelBuffer.toList() // 物理引擎調用時 isVisual 為 false
        
        // 安全哨兵：如果當前不是專業模式 (即 HID 優先為 true)，禁止讀取 101+ 索引（防範 Android 9 崩潰）
        val axis = mapping.axis
        if (axis >= 101 && state.isHidPriorityEnabled) return 0f
        
        val rawValue = if (axis in 0..124) buffer[axis] else 0f
        
        // [v1.7.8] 歸一化極性：Android HID 垂直軸向上為負(-)，在此處轉換為「向上為正(+)」
        val standardizedRaw = if (axis < 100 && (func == "T" || func == "P")) -rawValue else rawValue
        
        val processed = if (mapping.inverted) -standardizedRaw else standardizedRaw
        
        return if (applyCurves) {
            InputProcessor.process(processed, state.joystickDeadzone, state.getExpo(func), state.getRate(func, processed), mapping)
        } else {
            // [v1.7.8] 視覺軌道：僅執行死區過濾，不套用 Rate/Expo，防止出框
            if (Math.abs(processed) < state.joystickDeadzone) 0f else processed
        }
    }

    // --- 物理指令集 (供 PhysicsEngine 調用，維持 Rate/Expo/DNA 曲線) ---
    fun stickThrottle(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("T", state)], getSide("T", state), "T", false, true)
    fun stickYaw(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("Y", state)], getSide("Y", state), "Y", false, true)
    fun stickPitch(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("P", state)], getSide("P", state), "P", false, true)
    fun stickRoll(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("R", state)], getSide("R", state), "R", false, true)

    // --- 視覺同步集 (供 HUD/UI 調用，強制 1:1 歸一化，防止出框) ---
    fun visualLX(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[1], true, getFunc(1, state), true, false)
    fun visualLY(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[0], true, getFunc(0, state), true, false)
    fun visualRX(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[3], false, getFunc(3, state), true, false)
    fun visualRY(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[2], false, getFunc(2, state), true, false)

    // [v1.7.8] 消費者 API (UI/HUD 調用) - 重新映射至視覺軌道
    fun stickLX(state: DroneState) = visualLX(state)
    fun stickLY(state: DroneState) = visualLY(state)
    fun stickRX(state: DroneState) = visualRX(state)
    fun stickRY(state: DroneState) = visualRY(state)

    // --- 輔助函數：將 Mode 1-4 邏輯收納至配置層 ---
    private fun getIdx(func: String, s: DroneState): Int = when(func) {
        "T" -> if (s.joystickMode == 1 || s.joystickMode == 4) 2 else 0
        "Y" -> if (s.joystickMode == 3 || s.joystickMode == 4) 3 else 1
        "P" -> if (s.joystickMode == 1 || s.joystickMode == 4) 0 else 2
        "R" -> if (s.joystickMode == 3 || s.joystickMode == 4) 1 else 3
        else -> 0
    }
    private fun getSide(func: String, s: DroneState): Boolean = (getIdx(func, s) < 2) // 0,1 為左
    private fun getFunc(idx: Int, s: DroneState): String = when(idx) {
        0 -> if(s.joystickMode == 1 || s.joystickMode == 4) "P" else "T"
        1 -> if(s.joystickMode == 3 || s.joystickMode == 4) "R" else "Y"
        2 -> if(s.joystickMode == 1 || s.joystickMode == 4) "T" else "P"
        3 -> if(s.joystickMode == 3 || s.joystickMode == 4) "Y" else "R"
        else -> "T"
    }
}
