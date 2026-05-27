package com.horizon.nikonikodronesimulator.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.horizon.nikonikodronesimulator.logic.DroneViewModel
import com.horizon.nikonikodronesimulator.logic.storage.ConfigurationStore
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.StickInputState
import com.horizon.nikonikodronesimulator.ui.hud.SideNavInstruments
import com.horizon.nikonikodronesimulator.ui.hud.StickInteractionLogic

/**
 * [v1.7.6] 標準飛行邏輯派發器 (Standard Flight Logic Dispatcher)
 * 職責：整合所有版本皆適用的通用組件 (側邊儀表、解鎖邏輯、引導精靈)。
 */
@Composable
fun StandardFlightLogic(
    droneState: DroneState,
    stickInputState: StickInputState,
    viewModel: DroneViewModel,
    configStore: ConfigurationStore,
    modifier: Modifier = Modifier
) {
    // 1. 通用：硬體設定引導精靈監控
    LaunchedEffect(droneState.setupWizardStep, droneState.wizardWaitingForNeutral) {
        if (droneState.setupWizardStep > 0 && droneState.wizardWaitingForNeutral) {
            viewModel.startWizardCountdown(droneState, configStore)
        }
    }

    // 2. 通用視覺：側邊導航儀表 (zIndex=12 -> 改由傳入的 modifier 決定)
    SideNavInstruments(
        state = droneState,
        onUpdateState = { action -> droneState.action() },
        modifier = modifier
    )

    // 3. 通用邏輯：搖桿解鎖判定 (CSC)
    StickInteractionLogic(
        state = droneState, 
        stickState = stickInputState,
        onUpdateState = { action -> droneState.action() }
    )
}
