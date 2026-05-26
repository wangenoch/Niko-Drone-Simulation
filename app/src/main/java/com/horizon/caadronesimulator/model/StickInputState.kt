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
 * [v1.7.9] 搖桿數據狀態 - 指令中樞層 (歸一化緩衝池專業版)
 * 職責：作為唯一的邏輯運算點，執行 Inversion, Deadzone, Expo 與 Rate。
 * 遵循：INPUT_SYSTEM_ARCHITECTURE.md 規範。
 */
class StickInputState {
    // --- 全域通道緩衝池 (唯一的數據真源) ---
    private val channelBuffer = FloatArray(125) { 0f }
    private val lastRawBuffer = FloatArray(125) { 0f } // [v1.7.9] 變動偵測快照

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

    /** [v1.7.9] 統一寫入接口：更新指定的通道數值 (100% 原始數據) */
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

    /** [v1.7.9] 強制清空所有數據 (切換模式專用) */
    fun resetAll() {
        channelBuffer.fill(0f)
        visualBuffer = channelBuffer.toList()
        touchLY = 0f; touchLX = 0f; touchRY = 0f; touchRX = 0f
        isTouchingLeft = false; isTouchingRight = false
        lastUiUpdateTime = 0L
    }

    /** [v1.7.9] 兼容性接口：映射至標準 HID 軸位 (僅供 Pro 版內部路徑或舊版邏輯使用) */
    fun updateRaw(ly: Float, lx: Float, ry: Float, rx: Float) {
        setChannel(1, ly)  // AXIS_Y
        setChannel(0, lx)  // AXIS_X
        setChannel(14, ry) // AXIS_RZ
        setChannel(11, rx) // AXIS_Z
    }

    // --- [v1.7.9] 核心演算中樞 (Logic Processing) ---

    private fun resolveValue(state: DroneState, mapping: ChannelMapping, isLeft: Boolean, func: String, isVisual: Boolean, ignoreRate: Boolean = false): Float {
        val axis = mapping.axis
        val rawValue = if (axis in 0..124) channelBuffer[axis] else 0f
        
        // 1. [v1.7.9 智慧仲裁] 實體優先與意圖偵測
        // 只要實體搖桿有顯著變動 (>1%)，則鎖定實體路徑；否則若在觸摸中，則切換至虛擬路徑
        val isMoving = if (axis in 0..124) {
            val delta = Math.abs(rawValue - lastRawBuffer[axis])
            if (delta > 0.01f) {
                lastRawBuffer[axis] = rawValue // 更新快照
                true
            } else false
        } else false

        val isTouching = if (isLeft) isTouchingLeft else isTouchingRight
        
        // 決策：若實體正在動，聽實體的；若實體靜止且在觸控，聽觸控的
        if (!isMoving && isTouching) {
            val touchVal = when(func) {
                "T" -> if(isLeft) touchLY else touchRY
                "Y" -> if(isLeft) touchLX else touchRX
                "P" -> if(isLeft) touchLY else touchRY 
                "R" -> if(isLeft) touchLX else touchRX 
                else -> 0f
            }
            val finalTouch = touchVal
            // [v1.7.9.8 TRUTH ANCHOR]
            // 核心極性定義（已驗證）：
            // Roll (R): 右推為正 (+1.0)，物理引擎已對位此正值為視覺向右。
            // Pitch (P): 前推為正 (+1.0)
            // Yaw (Y): 右推/順時針為正 (+1.0)
            // Throttle (T): 上推為正 (+1.0)
            
            // [v1.7.11] 原始行程路徑：無視 Rate 與 Expo
            if (ignoreRate) return finalTouch

            val finalCmd = InputProcessor.processVirtual(finalTouch, state.getExpo(func), state.getRate(func, finalTouch))
            return if (isVisual) finalCmd / state.getRate(func, finalTouch).coerceAtLeast(0.01f) else finalCmd
        }

        // 2. 從緩衝池取貨 (實體路徑)
        val buffer = if (isVisual) visualBuffer else channelBuffer.toList()

        // 安全哨兵：HID 優先時禁止讀取 Serial 索引 (101+)
        if (axis >= 101 && state.isHidPriorityEnabled) return 0f
        val currentRaw = if (axis in 0..124) buffer[axis] else 0f

        // 3. 物理校準與歸一化 (Normalization)
        val normalized = if (mapping.max != mapping.min) {
            (currentRaw - mapping.center) / (if (currentRaw > mapping.center) (mapping.max - mapping.center) else (mapping.center - mapping.min)).coerceAtLeast(0.01f)
        } else {
            currentRaw - mapping.center
        }

        // 3. 唯一極性反轉 (Inversion)
        // [v1.7.9] 移除底層硬編碼取反，僅依據 MappingDB 執行
        val processed = if (mapping.inverted) -normalized else normalized
        
        // [v1.7.11] 原始行程路徑：跳過手感曲線運算，直接回傳反轉並歸一化後的原始物理位置
        if (ignoreRate) return processed

        // 4. 手感曲線演算 (Expo & Rates)
        val finalCmd = InputProcessor.process(processed, state.joystickDeadzone, state.getExpo(func), state.getRate(func, processed), mapping)

        // 5. 視覺對位修正 (Visual Alignment)
        // [v1.7.9] 解決出框問題：FinalCmd / Rate，確保打滿必觸邊且不出框
        return if (isVisual) {
            finalCmd / state.getRate(func, processed).coerceAtLeast(0.01f)
        } else {
            finalCmd
        }
    }

    // --- 物理指令集 (供 PhysicsEngine 調用) ---
    fun stickThrottle(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("T", state)], getSide("T", state), "T", false)
    fun stickYaw(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("Y", state)], getSide("Y", state), "Y", false)
    fun stickPitch(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("P", state)], getSide("P", state), "P", false)
    fun stickRoll(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("R", state)], getSide("R", state), "R", false)

    /** [v1.7.11] 原始行程指令集 (無視 Rate 縮放，僅保留 Inversion 與歸一化，專供解鎖判定) */
    fun rawThrottle(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("T", state)], getSide("T", state), "T", false, ignoreRate = true)
    fun rawYaw(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("Y", state)], getSide("Y", state), "Y", false, ignoreRate = true)
    fun rawPitch(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("P", state)], getSide("P", state), "P", false, ignoreRate = true)
    fun rawRoll(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[getIdx("R", state)], getSide("R", state), "R", false, ignoreRate = true)

    // --- 視覺同步集 (供 HUD/UI 調用) ---
    fun stickLX(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[1], true, getFunc(1, state), true)
    fun stickLY(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[0], true, getFunc(0, state), true)
    fun stickRX(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[3], false, getFunc(3, state), true)
    fun stickRY(state: DroneState) = resolveValue(state, state.getMappingSnapshot()[2], false, getFunc(2, state), true)

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
