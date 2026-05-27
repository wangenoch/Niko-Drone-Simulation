package com.horizon.nikonikodronesimulator.mission

import androidx.compose.runtime.Composable
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.DroneSpecs

/**
 * 自由練習模式評測器 (Free Flight Evaluator)
 */
class FreeFlightEvaluator : MissionEvaluator {
    override val modeName: String = "FREE"

    override fun update(state: DroneState, dt: Float, spec: DroneSpecs) {
        // 自由練習模式不修改狀態邏輯
    }

    @Composable
    override fun OverlayUI(state: DroneState, onUpdateState: (DroneState.() -> Unit) -> Unit) {
        // 自由練習模式沒有額外的 UI 疊加
    }
}
