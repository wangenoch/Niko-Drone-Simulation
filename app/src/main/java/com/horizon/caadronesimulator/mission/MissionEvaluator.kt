package com.horizon.caadronesimulator.mission

import androidx.compose.runtime.Composable
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneSpecs

/**
 * 任務評測器接口 (Mission Evaluator Interface)
 * [v1.2.68] 局部更新優化版
 */
interface MissionEvaluator {
    val modeName: String

    /**
     * 核心邏輯更新：直接修改傳入的 state 對象
     */
    fun update(state: DroneState, dt: Float, spec: DroneSpecs)

    @Composable
    fun OverlayUI(state: DroneState, onUpdateState: (DroneState.() -> Unit) -> Unit)
}
