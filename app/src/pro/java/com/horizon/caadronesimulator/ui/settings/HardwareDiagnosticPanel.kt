package com.horizon.caadronesimulator.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.model.ConnectionStatus
import com.horizon.caadronesimulator.ui.theme.NikoTheme
import java.util.Locale
import android.hardware.usb.UsbDevice

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.5.2] 終極整合版硬體診斷面板 (Industrial Logic 3.0)
 * 特性：佈局大重組、全鏈路 15FPS 監控、網路串口統合、日誌復原。
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
    isExpertLocked: Boolean = false // [v1.5.3] 傳遞專家模式鎖定狀態
) {
    val context = LocalContext.current
    val ds = com.horizon.caadronesimulator.model.DroneState.getInstance()

    Surface(
        color = NikoTheme.colors.background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, NikoTheme.colors.divider),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. 頂部狀態與控制列
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val statusColor = when(connectionStatus) {
                    ConnectionStatus.ACTIVE -> NikoTheme.colors.status
                    ConnectionStatus.SEARCHING -> NikoTheme.colors.safety
                    else -> NikoTheme.colors.textSecondary
                }
                Box(modifier = Modifier.size(7.dp).background(statusColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when(connectionStatus) {
                        ConnectionStatus.ACTIVE -> stringResource(R.string.diag_status_active)
                        ConnectionStatus.SEARCHING -> stringResource(R.string.diag_status_searching)
                        ConnectionStatus.LINKED -> stringResource(R.string.diag_status_linked)
                        else -> stringResource(R.string.diag_status_idle)
                    },
                    color = NikoTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.weight(1f))
                
                // [主權標籤]
                Surface(
                    color = when(ds.commDecisionState) {
                        com.horizon.caadronesimulator.model.CommDecisionState.LOCKED -> NikoTheme.colors.primary.copy(alpha = 0.3f)
                        com.horizon.caadronesimulator.model.CommDecisionState.SCANNING -> NikoTheme.colors.textSecondary.copy(alpha = 0.3f)
                        com.horizon.caadronesimulator.model.CommDecisionState.ENGAGED -> NikoTheme.colors.accent.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, NikoTheme.colors.divider),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = when(ds.commDecisionState) {
                            com.horizon.caadronesimulator.model.CommDecisionState.LOCKED -> stringResource(R.string.diag_state_locked)
                            com.horizon.caadronesimulator.model.CommDecisionState.SCANNING -> stringResource(R.string.diag_state_scanning)
                            com.horizon.caadronesimulator.model.CommDecisionState.ENGAGED -> stringResource(R.string.diag_state_engaged)
                            com.horizon.caadronesimulator.model.CommDecisionState.AWAITING_PERMISSION -> stringResource(R.string.diag_state_awaiting_perm)
                            else -> if(ds.isAutoConnectEnabled) stringResource(R.string.diag_state_standby) else stringResource(R.string.diag_state_silent)
                        },
                        color = NikoTheme.colors.textPrimary, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                val btnColor = when(connectionStatus) {
                    ConnectionStatus.ACTIVE -> Color(0xFF2E7D32)
                    ConnectionStatus.SEARCHING -> Color(0xFFF57C00)
                    else -> NikoTheme.colors.primary
                }
                
                Button(
                    onClick = onScanUsb,
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(26.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    val icon = if (connectionStatus == ConnectionStatus.IDLE) Icons.Default.Usb else Icons.Default.Close
                    Icon(icon, null, modifier = Modifier.size(12.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(text = if (connectionStatus == ConnectionStatus.IDLE) stringResource(R.string.action_scan) else stringResource(R.string.action_disconnect), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("RX: $serialByteCount", color = NikoTheme.colors.textSecondary, fontSize = 8.sp)
            }

            Spacer(Modifier.height(10.dp))

            // 2. 核心診斷表格 (佈局重組 3.0)
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左側：配置與主權
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 硬體路徑選單
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${stringResource(R.string.diag_label_path)}: ", color = NikoTheme.colors.textSecondary, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        var pathExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                modifier = Modifier.clickable { pathExpanded = true },
                                color = if (ds.lockedSerialPath.isEmpty()) NikoTheme.colors.textSecondary.copy(0.1f) else NikoTheme.colors.primary.copy(0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, if (ds.lockedSerialPath.isEmpty()) NikoTheme.colors.divider else NikoTheme.colors.primary.copy(0.4f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text(if (ds.lockedSerialPath.isEmpty()) stringResource(R.string.diag_path_auto) else ds.lockedSerialPath.substringAfterLast("/"), color = NikoTheme.colors.textPrimary, fontSize = 8.sp)
                                    Icon(Icons.Default.ArrowDropDown, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(10.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = pathExpanded, 
                                onDismissRequest = { pathExpanded = false },
                                modifier = Modifier.background(NikoTheme.colors.panel).border(1.dp, NikoTheme.colors.divider, RoundedCornerShape(8.dp)),
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.diag_path_auto_rec), fontSize = 11.sp, color = NikoTheme.colors.textPrimary) }, onClick = { onUpdateLockedPath(""); pathExpanded = false })
                                availablePorts.forEach { p -> DropdownMenuItem(text = { Text(p, fontSize = 10.sp, color = NikoTheme.colors.textPrimary) }, onClick = { onUpdateLockedPath(p); pathExpanded = false }) }
                            }
                        }
                    }
                    DiagnosticRow(stringResource(R.string.diag_label_link_type), linkType)

                    // 協議管理
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${stringResource(R.string.diag_label_protocol_mgmt)}: ", color = NikoTheme.colors.textSecondary, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        Text(detectedProtocol.ifEmpty { stringResource(R.string.diag_status_idle) }, color = NikoTheme.colors.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        var protocolMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { protocolMenuExpanded = true }, modifier = Modifier.size(16.dp)) {
                                Icon(if (lockedProtocol.isEmpty()) Icons.Default.Search else Icons.Default.Lock, null, tint = NikoTheme.colors.textSecondary, modifier = Modifier.size(10.dp))
                            }
                            DropdownMenu(
                                expanded = protocolMenuExpanded, 
                                onDismissRequest = { protocolMenuExpanded = false },
                                modifier = Modifier.background(NikoTheme.colors.panel).border(1.dp, NikoTheme.colors.divider, RoundedCornerShape(8.dp)),
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.diag_protocol_auto), fontSize = 11.sp, color = NikoTheme.colors.textPrimary) }, onClick = { onUpdateLockedProtocol(""); protocolMenuExpanded = false })
                                HorizontalDivider(color = NikoTheme.colors.divider)
                                listOf("CRSF", "S.Bus", "MAVLink", "RadioMaster AX12 (UMBUS-V1)", "RadioMaster AX12 (UMBUS-V2)", stringResource(R.string.diag_protocol_si_exp)).forEach { p ->
                                    DropdownMenuItem(text = { Text(p, fontSize = 10.sp, color = NikoTheme.colors.textPrimary) }, onClick = { onUpdateLockedProtocol(p); protocolMenuExpanded = false })
                                }
                            }
                        }
                    }

                    // [重組] 進程衝突
                    DiagnosticRow(stringResource(R.string.diag_label_conflict), conflictPid)

                    // 波特率選擇
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${stringResource(R.string.diag_label_baud)}: ", color = NikoTheme.colors.textSecondary, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        var baudExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(modifier = Modifier.clickable { baudExpanded = true }, color = NikoTheme.colors.primary.copy(0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(0.5.dp, NikoTheme.colors.primary.copy(0.3f))) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("$baudRate bps", color = NikoTheme.colors.textPrimary, fontSize = 8.sp); Icon(Icons.Default.ArrowDropDown, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(10.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = baudExpanded, 
                                onDismissRequest = { baudExpanded = false },
                                modifier = Modifier.background(NikoTheme.colors.panel).border(1.dp, NikoTheme.colors.divider, RoundedCornerShape(8.dp)),
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                listOf(9600, 57600, 115200, 460800, 921600).forEach { rate ->
                                    val isRec = rate == ds.hardwareProfile?.driver?.recommendedBaudRate
                                    DropdownMenuItem(text = { Text(if(isRec) stringResource(R.string.diag_baud_rec, rate) else "$rate bps", fontSize = 10.sp, color = if(isRec) NikoTheme.colors.primary else NikoTheme.colors.textPrimary) }, onClick = { onUpdateBaudRate(rate); baudExpanded = false })
                                }
                            }
                        }
                    }
                    
                    DiagnosticRow(stringResource(R.string.diag_label_net_proto), if(isNetworkConnected) networkProtocol else "--")
                    // [重組] 網路設定入口 (整合至左側)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${stringResource(R.string.diag_label_net_conn)}: ", color = NikoTheme.colors.textSecondary, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        Text(if(isNetworkConnected) "$networkHost:$networkPort" else stringResource(R.string.hud_signal_waiting), color = if(isNetworkConnected) NikoTheme.colors.status else NikoTheme.colors.textPrimary, fontSize = 8.sp)
                        IconButton(onClick = onOpenNetworkSettings, modifier = Modifier.size(18.dp).padding(start = 2.dp)) {
                            Icon(Icons.Default.Settings, null, tint = NikoTheme.colors.primary.copy(0.5f), modifier = Modifier.size(10.dp))
                        }
                    }
                }

                // 右側：物理性能指標
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiagnosticRow(stringResource(R.string.diag_label_pps), "$packetsPerSecond PPS")
                    DiagnosticRow(stringResource(R.string.diag_label_jitter), jitter)
                    DiagnosticRow(stringResource(R.string.diag_label_stability), stability)
                    DiagnosticRow(stringResource(R.string.diag_label_buffer), bufferUsage)
                    DiagnosticRow(stringResource(R.string.diag_label_density), "$rawBytesCount bytes")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${stringResource(R.string.diag_label_signal_status)}: ", color = NikoTheme.colors.textSecondary, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        Text(if (isSignalActive) stringResource(R.string.status_ok) else stringResource(R.string.status_no_signal), color = if(isSignalActive) NikoTheme.colors.status else NikoTheme.colors.warning, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3. 數據流全視角監控區 (雙排 48 通道進化版)
            Column(modifier = Modifier.fillMaxWidth().background(NikoTheme.colors.panel.copy(0.3f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(if(ds.inputMode == 0) stringResource(R.string.diag_monitor_hid) else stringResource(R.string.diag_monitor_serial), color = NikoTheme.colors.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f)); Text(stringResource(R.string.diag_label_dual_row), color = NikoTheme.colors.textSecondary.copy(0.5f), fontSize = 7.sp)
                }
                Spacer(Modifier.height(6.dp))
                
                // 上排 (1-24)
                Row(modifier = Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                    repeat(24) { i ->
                        val v = rawChannels.getOrNull(i) ?: 0f
                        val h = ((v + 1f) / 2f).coerceIn(0f, 1f)
                        val endPad = if ((i + 1) % 4 == 0 && i < 23) 3.dp else 0.dp
                        Box(modifier = Modifier.weight(1f).padding(end = endPad).fillMaxHeight(h.coerceAtLeast(0.05f)).background(if(i<4) NikoTheme.colors.primary else NikoTheme.colors.primary.copy(0.3f), RoundedCornerShape(topStart=1.dp, topEnd=1.dp)))
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 下排 (25-48)
                Row(modifier = Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                    repeat(24) { i ->
                        val idx = i + 24
                        val v = rawChannels.getOrNull(idx) ?: 0f
                        val h = ((v + 1f) / 2f).coerceIn(0f, 1f)
                        val endPad = if ((i + 1) % 4 == 0 && i < 23) 3.dp else 0.dp
                        Box(modifier = Modifier.weight(1f).padding(end = endPad).fillMaxHeight(h.coerceAtLeast(0.05f)).background(NikoTheme.colors.textSecondary.copy(0.3f), RoundedCornerShape(topStart=1.dp, topEnd=1.dp)))
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 自適應 Hex/Axis 預覽
                Text(text = rawHexData.ifEmpty { stringResource(R.string.diag_status_waiting_data) }, color = NikoTheme.colors.status, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().background(NikoTheme.colors.panel.copy(0.5f), RoundedCornerShape(4.dp)).padding(4.dp))
            }

            Spacer(Modifier.height(14.dp))

            // 4. 日誌與匯出系統 (視覺強化版)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diag_system_log), color = NikoTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onToggleLogcat(!isLogcatEnabled) }) {
                    Switch(
                        checked = isLogcatEnabled,
                        onCheckedChange = onToggleLogcat,
                        modifier = Modifier.scale(0.5f).height(20.dp),
                        colors = SwitchDefaults.colors(checkedThumbColor = NikoTheme.colors.primary, checkedTrackColor = NikoTheme.colors.primary.copy(0.4f))
                    )
                    Text(stringResource(R.string.diag_realtime_monitor), color = if(isLogcatEnabled) NikoTheme.colors.primary else NikoTheme.colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearLogcat, modifier = Modifier.height(22.dp)) { Text(stringResource(R.string.action_clear), color = NikoTheme.colors.textSecondary.copy(0.7f), fontSize = 8.sp) }
                Spacer(Modifier.width(6.dp))
                
                Button(
                    onClick = onExportLog, 
                    colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.primary.copy(0.15f)), 
                    shape = RoundedCornerShape(4.dp), 
                    modifier = Modifier.height(24.dp), 
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), 
                    border = BorderStroke(1.dp, NikoTheme.colors.primary.copy(0.4f))
                ) {
                    Icon(Icons.Default.Download, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_export), color = NikoTheme.colors.primary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(color = NikoTheme.colors.panel.copy(alpha = if(NikoTheme.colors.isLight) 0.05f else 0.5f), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().height(80.dp), border = if(NikoTheme.colors.isLight) BorderStroke(1.dp, NikoTheme.colors.divider) else null) {
                Box(modifier = Modifier.padding(6.dp)) {
                    val logText = if (isLogcatEnabled) logcatContent else diagnosticLog
                    val scrollState = rememberScrollState(); LaunchedEffect(logText) { scrollState.animateScrollTo(scrollState.maxValue) }
                    Text(text = logText, color = NikoTheme.colors.textSecondary, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.verticalScroll(scrollState).fillMaxWidth())
                }
            }

            // 5. 專家保護提示
            val driver = ds.hardwareProfile?.driver
            val isAx12Mode = lockedProtocol.contains("AX12") || detectedProtocol.contains("UMBUS") || detectedProtocol.contains("AX")
            if ((driver?.isMappingProtected == true || isAx12Mode) && !isMappingUnlocked && !com.horizon.caadronesimulator.logic.HardwareRegistry.debugForceUnlockAll) {
                Surface(
                    color = NikoTheme.colors.warning.copy(0.08f), 
                    shape = RoundedCornerShape(4.dp), 
                    border = BorderStroke(1.dp, NikoTheme.colors.warning.copy(0.3f)), 
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = NikoTheme.colors.warning.copy(0.9f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if(isAx12Mode) stringResource(R.string.joystick_mapping_locked) else driver?.protectionWarning ?: "", 
                            color = NikoTheme.colors.textPrimary.copy(0.9f), 
                            fontSize = 9.sp, 
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onToggleMappingUnlock(true) }, 
                            colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.warning.copy(0.2f)),
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            border = BorderStroke(1.dp, NikoTheme.colors.warning.copy(0.5f))
                        ) {
                            Text(stringResource(R.string.action_force_unlock), color = NikoTheme.colors.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // [New] 新硬體偵測提示
    newHardwareDetected?.let { dev ->
        Surface(color = NikoTheme.colors.primary.copy(0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp), border = BorderStroke(1.dp, NikoTheme.colors.primary.copy(0.3f))) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.diag_new_hw_detected), color = NikoTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${dev.productName ?: stringResource(R.string.diag_unknown_device)} (VID:${dev.vendorId})", color = NikoTheme.colors.textSecondary, fontSize = 8.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSelectHardwareMode(-1) }) { Text(stringResource(R.string.action_ignore), color = NikoTheme.colors.textSecondary, fontSize = 10.sp) }
                    
                    if (!isExpertLocked) {
                        Button(onClick = { onSelectHardwareMode(1) }, colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.textPrimary.copy(0.2f)), shape = RoundedCornerShape(4.dp), modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), border = BorderStroke(0.5.dp, NikoTheme.colors.divider)) {
                            Text(stringResource(R.string.diag_btn_set_internal), color = NikoTheme.colors.textPrimary, fontSize = 9.sp)
                        }
                    }

                    Button(onClick = { onSelectHardwareMode(0) }, colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.primary), shape = RoundedCornerShape(4.dp), modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(stringResource(R.string.diag_btn_set_external), color = if(NikoTheme.colors.isLight) Color.White else Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", color = NikoTheme.colors.textSecondary, fontSize = 9.sp, modifier = Modifier.width(60.dp))
        Text(value, color = NikoTheme.colors.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}
