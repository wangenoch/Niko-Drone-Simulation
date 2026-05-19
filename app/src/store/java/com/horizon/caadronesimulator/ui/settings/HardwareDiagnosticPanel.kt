package com.horizon.caadronesimulator.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.horizon.caadronesimulator.model.ConnectionStatus
import android.hardware.usb.UsbDevice

/**
 * [v1.7.6] 合規版 (Store) 硬體診斷面板佔位符
 * 職責：1:1 對齊 Pro 版參數簽署，確保 Main 代碼在 Store 版編譯時不報錯。
 */
@Composable
fun HardwareDiagnosticPanel(
    serialByteCount: Long,
    linkType: String,
    activeSerialPath: String,
    detectedProtocol: String,
    lockedProtocol: String,
    conflictPid: String,
    isSerialConflict: Boolean,
    packetsPerSecond: Int,
    bufferUsage: String,
    rawBytesCount: Int,
    isSignalActive: Boolean,
    baudRate: Int,
    diagnosticLog: String,
    rawHexData: String,
    rawChannels: List<Float>,
    isLogcatEnabled: Boolean,
    logcatContent: String,
    isMappingUnlocked: Boolean,
    networkHost: String,
    networkPort: Int,
    networkProtocol: String,
    isNetworkConnected: Boolean,
    connectionStatus: ConnectionStatus,
    onUpdateLockedProtocol: (String) -> Unit,
    onUpdateBaudRate: (Int) -> Unit,
    onOpenNetworkSettings: () -> Unit = {},
    onUpdateLockedPath: (String) -> Unit = {},
    onExportLog: () -> Unit,
    onToggleLogcat: (Boolean) -> Unit,
    onClearLogcat: () -> Unit,
    onScanUsb: () -> Unit,
    onUpdateNetworkHost: (String) -> Unit,
    onUpdateNetworkPort: (Int) -> Unit,
    onUpdateNetworkProtocol: (String) -> Unit,
    onToggleNetworkConnection: (Boolean) -> Unit,
    onToggleMappingUnlock: (Boolean) -> Unit,
    newHardwareDetected: UsbDevice? = null,
    availablePorts: List<String> = emptyList(),
    onSelectHardwareMode: (Int) -> Unit = {},
    jitter: String = "0.0 ms",
    stability: String = "100%",
    isExpertLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Store 版：隱藏所有診斷內容，不參與 UI 渲染
}
