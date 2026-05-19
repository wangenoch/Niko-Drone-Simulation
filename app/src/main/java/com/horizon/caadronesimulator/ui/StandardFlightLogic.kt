package com.horizon.caadronesimulator.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.logic.DroneViewModel
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.ui.hud.SideNavInstruments
import com.horizon.caadronesimulator.ui.hud.StickInteractionLogic

/**
 * [v1.7.6] 標準飛行邏輯派發器 (Standard Flight Logic Dispatcher)
 * 職責：整合所有版本皆適用的通用組件 (側邊儀表、解鎖邏輯、引導精靈)。
 */
@Composable
fun StandardFlightLogic(
    droneState: DroneState,
    stickInputState: StickInputState,
    viewModel: DroneViewModel,
    configStore: ConfigurationStore
) {
    // 1. 通用：硬體設定引導精靈監控
    LaunchedEffect(droneState.setupWizardStep, droneState.wizardWaitingForNeutral) {
        if (droneState.setupWizardStep > 0 && droneState.wizardWaitingForNeutral) {
            viewModel.startWizardCountdown(droneState, configStore)
        }
    }

    // 2. 通用視覺：側邊導航儀表 (zIndex=12)
    SideNavInstruments(
        state = droneState,
        onUpdateState = { action -> droneState.action() },
        modifier = Modifier.zIndex(12f)
    )

    // 3. 通用邏輯：搖桿解鎖判定 (CSC)
    StickInteractionLogic(
        state = droneState, 
        stickState = stickInputState,
        onUpdateState = { action -> droneState.action() }
    )
}
