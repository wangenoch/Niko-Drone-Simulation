package com.horizon.caadronesimulator.ui.settings

import androidx.compose.runtime.Composable
import com.horizon.caadronesimulator.model.DroneState

/**
 * [v1.7.6] 合規版 (Store) 硬體連線狀態 Bar 佔位符
 * 職責：在上架版中隱藏內置模式切換，僅保留自動感知邏輯。
 */
@Composable
fun HardwareConnectionHeader(
    state: DroneState,
    inputMode: Int,
    isSignalActive: Boolean,
    showHardwareMonitor: Boolean,
    onUpdateInputMode: (Int) -> Unit,
    onToggleHardwareMonitor: (Boolean) -> Unit
) {
    // Store 版：不顯示切換 Bar，所有邏輯由 ProHardwareBridge 在背景自動處理。
}
