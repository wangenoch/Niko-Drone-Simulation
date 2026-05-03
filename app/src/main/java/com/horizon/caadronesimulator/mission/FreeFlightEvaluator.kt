package com.horizon.caadronesimulator.mission

import androidx.compose.runtime.Composable
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneSpecs

/**
 * 自由練習模式評測器 (Free Flight Evaluator)
 */
class FreeFlightEvaluator : MissionEvaluator {
    override val modeName: String = "自由練習"

    override fun update(state: DroneState, dt: Float, spec: DroneSpecs) {
        // 自由練習模式不修改狀態邏輯
    }

    @Composable
    override fun OverlayUI(state: DroneState, onUpdateState: (DroneState.() -> Unit) -> Unit) {
        // 自由練習模式沒有額外的 UI 疊加
    }
}
