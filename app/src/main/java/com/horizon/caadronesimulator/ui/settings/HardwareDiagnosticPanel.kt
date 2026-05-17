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
import java.util.Locale
import android.hardware.usb.UsbDevice

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
        color = Color(0xFF0D1117),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. 頂部狀態與控制列
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val statusColor = when(connectionStatus) {
                    ConnectionStatus.ACTIVE -> Color(0xFF00E676)
                    ConnectionStatus.SEARCHING -> Color(0xFFFF9100)
                    else -> Color.Gray
                }
                Box(modifier = Modifier.size(7.dp).background(statusColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when(connectionStatus) {
                        ConnectionStatus.ACTIVE -> "連線中 (穩定)"
                        ConnectionStatus.SEARCHING -> "正在搜尋硬體..."
                        ConnectionStatus.LINKED -> "鏈結建立中..."
                        else -> "通訊閒置"
                    },
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.weight(1f))
                
                // [主權標籤]
                Surface(
                    color = when(ds.commDecisionState) {
                        com.horizon.caadronesimulator.model.CommDecisionState.LOCKED -> Color.Blue.copy(alpha = 0.3f)
                        com.horizon.caadronesimulator.model.CommDecisionState.SCANNING -> Color.Gray.copy(alpha = 0.3f)
                        com.horizon.caadronesimulator.model.CommDecisionState.ENGAGED -> Color.Yellow.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = when(ds.commDecisionState) {
                            com.horizon.caadronesimulator.model.CommDecisionState.LOCKED -> "🔒 手動鎖定"
                            com.horizon.caadronesimulator.model.CommDecisionState.SCANNING -> "🔎 自動掃描"
                            com.horizon.caadronesimulator.model.CommDecisionState.ENGAGED -> "🚀 發現硬體"
                            com.horizon.caadronesimulator.model.CommDecisionState.AWAITING_PERMISSION -> "🔑 等待授權"
                            else -> if(ds.isAutoConnectEnabled) "待機" else "🚫 靜默模式"
                        },
                        color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                val btnColor = when(connectionStatus) {
                    ConnectionStatus.ACTIVE -> Color(0xFF2E7D32)
                    ConnectionStatus.SEARCHING -> Color(0xFFF57C00)
                    else -> Color(0xFF1976D2) 
                }
                
                Button(
                    onClick = onScanUsb,
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(26.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    val icon = if (connectionStatus == ConnectionStatus.IDLE) Icons.Default.Usb else Icons.Default.Close
                    Icon(icon, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = if (connectionStatus == ConnectionStatus.IDLE) "掃描連線" else "中斷連線", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("RX: $serialByteCount", color = Color.Gray, fontSize = 8.sp)
            }

            Spacer(Modifier.height(10.dp))

            // 2. 核心診斷表格 (佈局重組 3.0)
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左側：配置與主權
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiagnosticRow("連結類型", linkType)
                    
                    // 硬體路徑選單
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("硬體路徑: ", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        var pathExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                modifier = Modifier.clickable { pathExpanded = true },
                                color = if (ds.lockedSerialPath.isEmpty()) Color.Gray.copy(0.1f) else Color.Cyan.copy(0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, if (ds.lockedSerialPath.isEmpty()) Color.Gray.copy(0.3f) else Color.Cyan.copy(0.4f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text(if (ds.lockedSerialPath.isEmpty()) "自動偵測" else ds.lockedSerialPath.substringAfterLast("/"), color = Color.White, fontSize = 8.sp)
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.Cyan, modifier = Modifier.size(10.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = pathExpanded, 
                                onDismissRequest = { pathExpanded = false },
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                DropdownMenuItem(text = { Text("自動掃描 (推薦)", fontSize = 11.sp) }, onClick = { onUpdateLockedPath(""); pathExpanded = false })
                                availablePorts.forEach { p -> DropdownMenuItem(text = { Text(p, fontSize = 10.sp) }, onClick = { onUpdateLockedPath(p); pathExpanded = false }) }
                            }
                        }
                    }

                    // 協議管理
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("協議管理: ", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        Text(detectedProtocol.ifEmpty { "待機" }, color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        var protocolMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { protocolMenuExpanded = true }, modifier = Modifier.size(16.dp)) {
                                Icon(if (lockedProtocol.isEmpty()) Icons.Default.Search else Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(10.dp))
                            }
                            DropdownMenu(
                                expanded = protocolMenuExpanded, 
                                onDismissRequest = { protocolMenuExpanded = false },
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                DropdownMenuItem(text = { Text("自動識別協議", fontSize = 11.sp) }, onClick = { onUpdateLockedProtocol(""); protocolMenuExpanded = false })
                                HorizontalDivider(color = Color.White.copy(0.1f))
                                listOf("CRSF", "S.Bus", "MAVLink", "AX12(UMBUS)(實驗性)", "AX-Enhanced(實驗性)", "SIYI MK15(實驗性)").forEach { p ->
                                    DropdownMenuItem(text = { Text(p, fontSize = 10.sp) }, onClick = { onUpdateLockedProtocol(p); protocolMenuExpanded = false })
                                }
                            }
                        }
                    }

                    // 波特率選擇
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("傳輸速率: ", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        var baudExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(modifier = Modifier.clickable { baudExpanded = true }, color = Color.Cyan.copy(0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(0.5.dp, Color.Cyan.copy(0.3f))) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("$baudRate bps", color = Color.White, fontSize = 8.sp); Icon(Icons.Default.ArrowDropDown, null, tint = Color.Cyan, modifier = Modifier.size(10.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = baudExpanded, 
                                onDismissRequest = { baudExpanded = false },
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                listOf(9600, 57600, 115200, 460800, 921600).forEach { rate ->
                                    val isRec = rate == ds.hardwareProfile?.driver?.recommendedBaudRate
                                    DropdownMenuItem(text = { Text(if(isRec) "$rate (推薦)" else "$rate bps", fontSize = 10.sp, color = if(isRec) Color.Cyan else Color.White) }, onClick = { onUpdateBaudRate(rate); baudExpanded = false })
                                }
                            }
                        }
                    }

                    // [重組] 進程衝突
                    DiagnosticRow("進程衝突", conflictPid)
                    
                    // [重組] 網路設定入口 (整合至左側)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("網路連線: ", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        Text(if(isNetworkConnected) "$networkHost:$networkPort" else "未連線", color = if(isNetworkConnected) Color.Green else Color.White, fontSize = 8.sp)
                        IconButton(onClick = onOpenNetworkSettings, modifier = Modifier.size(18.dp).padding(start = 2.dp)) {
                            Icon(Icons.Default.Settings, null, tint = Color.Cyan.copy(0.5f), modifier = Modifier.size(10.dp))
                        }
                    }
                    DiagnosticRow("網路協議", if(isNetworkConnected) networkProtocol else "--")
                }

                // 右側：物理性能指標
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiagnosticRow("封包速率", "$packetsPerSecond PPS")
                    DiagnosticRow("訊號延遲", jitter)
                    DiagnosticRow("通訊穩定", stability)
                    DiagnosticRow("緩衝狀態", bufferUsage)
                    DiagnosticRow("數據密度", "$rawBytesCount bytes")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("訊號狀態: ", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(60.dp))
                        Text(if (isSignalActive) "OK" else "NO SIGNAL", color = if(isSignalActive) Color.Green else Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3. 數據流全視角監控區 (雙排 48 通道進化版)
            Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(if(ds.inputMode == 0) "手把軸向監控 (HID)" else "通道數據流 (15 FPS)", color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f)); Text("48-CH DUAL ROW", color = Color.Gray.copy(0.5f), fontSize = 7.sp)
                }
                Spacer(Modifier.height(6.dp))
                
                // 上排 (1-24)
                Row(modifier = Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                    repeat(24) { i ->
                        val v = rawChannels.getOrNull(i) ?: 0f
                        val h = ((v + 1f) / 2f).coerceIn(0f, 1f)
                        // [v1.5.2] 每 4 通道增加右側邊距，增強辨識度
                        val endPad = if ((i + 1) % 4 == 0 && i < 23) 3.dp else 0.dp
                        Box(modifier = Modifier.weight(1f).padding(end = endPad).fillMaxHeight(h.coerceAtLeast(0.05f)).background(if(i<4) Color.Cyan else Color.Cyan.copy(0.3f), RoundedCornerShape(topStart=1.dp, topEnd=1.dp)))
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
                        Box(modifier = Modifier.weight(1f).padding(end = endPad).fillMaxHeight(h.coerceAtLeast(0.05f)).background(Color.Gray.copy(0.3f), RoundedCornerShape(topStart=1.dp, topEnd=1.dp)))
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 自適應 Hex/Axis 預覽
                Text(text = rawHexData.ifEmpty { "Waiting for data stream..." }, color = Color(0xFF00E676), fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(4.dp))
            }

            Spacer(Modifier.height(14.dp))

            // 4. 日誌與匯出系統 (視覺強化版)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("系統日誌", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                
                // [v1.5.3] 補回遺失的即時監測開關
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onToggleLogcat(!isLogcatEnabled) }) {
                    Switch(
                        checked = isLogcatEnabled,
                        onCheckedChange = onToggleLogcat,
                        modifier = Modifier.scale(0.5f).height(20.dp),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan, checkedTrackColor = Color.Cyan.copy(0.4f))
                    )
                    Text("即時監測", color = if(isLogcatEnabled) Color.Cyan else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearLogcat, modifier = Modifier.height(22.dp)) { Text("清除", color = Color.Gray.copy(0.7f), fontSize = 8.sp) }
                Spacer(Modifier.width(6.dp))
                
                // [視覺強化] 提升匯出按鈕清晰度
                Button(
                    onClick = onExportLog, 
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan.copy(0.15f)), 
                    shape = RoundedCornerShape(4.dp), 
                    modifier = Modifier.height(24.dp), 
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), 
                    border = BorderStroke(1.dp, Color.Cyan.copy(0.4f))
                ) {
                    Icon(Icons.Default.Download, null, tint = Color.Cyan, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("匯出報告", color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(color = Color.Black.copy(0.5f), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().height(80.dp)) {
                Box(modifier = Modifier.padding(6.dp)) {
                    val scrollState = rememberScrollState(); LaunchedEffect(diagnosticLog) { scrollState.animateScrollTo(scrollState.maxValue) }
                    Text(text = diagnosticLog, color = Color.LightGray, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.verticalScroll(scrollState).fillMaxWidth())
                }
            }

            // 5. 專家保護提示 (放寬判定條件：只要鎖定 AX12 類協議即顯示)
            val driver = ds.hardwareProfile?.driver
            val isAx12Mode = lockedProtocol.contains("AX12") || detectedProtocol.contains("UMBUS") || detectedProtocol.contains("AX")
            if ((driver?.isMappingProtected == true || isAx12Mode) && !isMappingUnlocked && !com.horizon.caadronesimulator.logic.HardwareRegistry.debugForceUnlockAll) {
                Surface(
                    color = Color.Red.copy(0.08f), 
                    shape = RoundedCornerShape(4.dp), 
                    border = BorderStroke(1.dp, Color.Red.copy(0.3f)), 
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = Color.Red.copy(0.9f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if(isAx12Mode) "專業協議映射已鎖定，確保控制精確性。" else driver?.protectionWarning ?: "", 
                            color = Color.White.copy(0.9f), 
                            fontSize = 9.sp, 
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onToggleMappingUnlock(true) }, 
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.2f)),
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            border = BorderStroke(1.dp, Color.Red.copy(0.5f))
                        ) {
                            Text("強制解鎖", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // [New] 新硬體偵測提示
    newHardwareDetected?.let { dev ->
        Surface(color = Color.Cyan.copy(0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp), border = BorderStroke(1.dp, Color.Cyan.copy(0.3f))) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("偵測到新 USB 硬體", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${dev.productName ?: "未知裝置"} (VID:${dev.vendorId})", color = Color.White.copy(0.7f), fontSize = 8.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSelectHardwareMode(-1) }) { Text("忽略", color = Color.Gray, fontSize = 10.sp) }
                    
                    // [v1.5.3] 鎖定模式防呆：僅開放「外接模式」切換，隱藏「內置系統」選項以防止錯誤配置。
                    // 如需恢復全功能切換，請解除 isExpertModeLocked 或在此移除條件判定。
                    if (!isExpertLocked) {
                        Button(onClick = { onSelectHardwareMode(1) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.2f)), shape = RoundedCornerShape(4.dp), modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), border = BorderStroke(0.5.dp, Color.White.copy(0.5f))) {
                            Text("設為內置", color = Color.White, fontSize = 9.sp)
                        }
                    }

                    Button(onClick = { onSelectHardwareMode(0) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan), shape = RoundedCornerShape(4.dp), modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("設為外接", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(60.dp))
        Text(value, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}
