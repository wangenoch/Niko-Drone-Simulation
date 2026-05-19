package com.horizon.caadronesimulator.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.logic.DroneViewModel
import com.horizon.caadronesimulator.logic.InternalCommManager
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.ui.hud.SideNavInstruments
import com.horizon.caadronesimulator.ui.hud.StickInteractionLogic

/**
 * [v1.7.6] 專業版功能調度器 (Pro Feature Dispatcher)
 * 職責：僅整合「專業版硬體」專屬的 LaunchedEffect 與日誌同步。
 */
@Composable
fun ProFeatureDispatcher(
    droneState: DroneState,
    internalComm: InternalCommManager
) {
    // 1. Pro 專屬：通訊與日誌連動
    LaunchedEffect(droneState.lockedProtocol) { 
        internalComm.setLockedProtocol(droneState.lockedProtocol) 
    }
    
    LaunchedEffect(droneState.isLogcatEnabled) { 
        internalComm.toggleLogcat(droneState.isLogcatEnabled) 
    }
}
