package com.horizon.caadronesimulator.mission

import androidx.compose.runtime.Composable
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneSpecs

/**
 * 任務管理器 (Mission Manager)
 */
object MissionManager {
    private val evaluators = mapOf(
        "FREE" to FreeFlightEvaluator(),
        "SPOT" to SpotTimerEvaluator()
    )

    fun update(state: DroneState, dt: Float, spec: DroneSpecs) {
        val activeKey = if (state.isSpotTimerEnabled) "SPOT" else "FREE"
        evaluators[activeKey]?.update(state, dt, spec)
    }

    @Composable
    fun RenderOverlay(state: DroneState, onUpdateState: (DroneState.() -> Unit) -> Unit) {
        val activeKey = if (state.isSpotTimerEnabled) "SPOT" else "FREE"
        evaluators[activeKey]?.OverlayUI(state, onUpdateState)
    }
}
