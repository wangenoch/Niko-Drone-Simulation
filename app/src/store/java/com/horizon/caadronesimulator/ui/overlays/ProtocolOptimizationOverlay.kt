package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.runtime.Composable
import com.horizon.caadronesimulator.model.DroneState

/** [v1.7.6] Store 版佔位符：上架版不進行 UMBUS 協議優化提示 */
@Composable
fun ProtocolOptimizationOverlay(
    state: DroneState,
    onApply: (String, Int) -> Unit,
    onIgnore: (Boolean) -> Unit
) {
    // No-op for store version
}
