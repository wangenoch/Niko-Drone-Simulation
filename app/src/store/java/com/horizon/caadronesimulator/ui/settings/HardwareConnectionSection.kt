package com.horizon.caadronesimulator.ui.settings
import androidx.compose.runtime.Composable
import com.horizon.caadronesimulator.model.ConnectionStatus

@Composable
fun HardwareConnectionSection(
    baudRate: Int,
    usbSerialConnected: Boolean,
    controllerConnected: Boolean,
    connectionStatus: com.horizon.caadronesimulator.model.ConnectionStatus,
    isSignalActive: Boolean,
    packetsPerSecond: Int,
    activeSerialPath: String?,
    isSerialConflict: Boolean,
    onUpdateBaudRate: (Int) -> Unit,
    onToggleConnection: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onToggleExpertLocked: (Boolean) -> Unit,
    onTargetPositioned: (String, androidx.compose.ui.geometry.Rect) -> Unit
) {
    // Store 版佔位
}

