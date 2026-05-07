package com.horizon.caadronesimulator.ui.components

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import java.util.Locale
import kotlin.math.abs

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
    isMappingUnlocked: Boolean = false,
    onUpdateLockedProtocol: (String) -> Unit,
    onUpdateBaudRate: (Int) -> Unit,
    onExportLog: () -> Unit,
    onToggleLogcat: (Boolean) -> Unit,
    onClearLogcat: () -> Unit,
    onToggleMappingUnlock: (Boolean) -> Unit = {}
) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("硬體診斷中心", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("RX: $serialByteCount bytes", color = Color.Gray, fontSize = 9.sp)
            }

            // 專業狀態表格
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    DiagnosticSmallLabel("連結類型", linkType)
                    DiagnosticSmallLabel("硬體路徑", activeSerialPath)
                    
                    // 全通訊協議管理中樞 (v1.3.5)
                    ProtocolManagementWidget(
                        detectedProtocol = detectedProtocol,
                        lockedProtocol = lockedProtocol,
                        onUpdateLockedProtocol = onUpdateLockedProtocol
                    )

                    DiagnosticSmallLabel("進程衝突 (PID)", conflictPid)
                    if (isSerialConflict) {
                        Text("⚠️ 偵測到進程衝突 (串口被佔用)", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.weight(1.2f)) {
                    DiagnosticSmallLabel("封包速率 (PPS)", "$packetsPerSecond 封包/秒")
                    DiagnosticSmallLabel("緩衝區狀態", bufferUsage)
                    DiagnosticSmallLabel("數據密度 (500ms)", "$rawBytesCount bytes")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val signalColor = if (isSignalActive) Color.Green else Color.Red
                        Text("訊號狀態: ", color = Color.Gray, fontSize = 9.sp)
                        Text(if (isSignalActive) "OK" else "NO SIGNAL", color = signalColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isBaudLocked = lockedProtocol == "AX12(UMBUS)"
                        Text("傳輸波特率: ", color = Color.Gray, fontSize = 9.sp)
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                modifier = Modifier.clickable(enabled = !isBaudLocked) { expanded = true },
                                color = if (isBaudLocked) Color.Gray.copy(alpha = 0.1f) else Color.Cyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, if (isBaudLocked) Color.Gray.copy(0.3f) else Color.Cyan.copy(0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "$baudRate bps", 
                                        color = if (isBaudLocked) Color.Gray else Color.White, 
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!isBaudLocked) {
                                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.Cyan, modifier = Modifier.size(14.dp))
                                    } else {
                                        Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(10.dp).padding(start = 2.dp))
                                    }
                                }
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color(0xFF1B2535)).border(1.dp, Color.Cyan.copy(0.3f), RoundedCornerShape(4.dp))
                            ) {
                                val baudRates = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)
                                baudRates.forEach { rate ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                "$rate bps", 
                                                color = if (rate == baudRate) Color.Cyan else Color.White, 
                                                fontSize = 12.sp,
                                                fontWeight = if (rate == baudRate) FontWeight.Bold else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            onUpdateBaudRate(rate)
                                            expanded = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 日誌與 Hex
            Surface(color = Color(0x33000000), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("系統日誌: $diagnosticLog", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onExportLog, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(20.dp)) {
                            Icon(Icons.Default.Save, null, tint = Color.Cyan, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("匯出日誌", color = Color.Cyan, fontSize = 9.sp)
                        }
                    }

                    HorizontalDivider(color = Color(0x11FFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Info, null, tint = Color.Cyan.copy(0.7f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Logcat 即時監測", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.weight(1f))
                        if (isLogcatEnabled) {
                            TextButton(onClick = onClearLogcat, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(20.dp)) {
                                Text("清除", color = Color.Gray, fontSize = 9.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Switch(checked = isLogcatEnabled, onCheckedChange = onToggleLogcat, modifier = Modifier.scale(0.5f).height(20.dp))
                    }

                    if (isLogcatEnabled) {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.Black.copy(0.3f), RoundedCornerShape(4.dp)).padding(4.dp)) {
                            val scrollState = rememberScrollState()
                            LaunchedEffect(logcatContent) { scrollState.animateScrollTo(scrollState.maxValue) }
                            Text(
                                logcatContent.ifEmpty { "等待日誌輸入..." },
                                color = Color(0xFFBBBBBB),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.verticalScroll(scrollState)
                            )
                        }
                    } else {
                        Text("原始數據即時預覽 (16 位元組):", color = Color.Gray, fontSize = 8.sp)
                        Text(rawHexData, color = Color.Yellow.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            // [v1.2.68] UMBUS 強制解鎖開關
            if (detectedProtocol.contains("UMBUS")) {
                Surface(
                    color = Color.Red.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, Color.Red.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LockOpen, null, tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("解鎖 UMBUS 手動映射映射 (專家模式)", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isMappingUnlocked, 
                            onCheckedChange = onToggleMappingUnlock, 
                            modifier = Modifier.scale(0.5f).height(20.dp),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Red, checkedTrackColor = Color.Red.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            // 24 個通道長條圖 (4 搖桿 + 20 輔助)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                rawChannels.take(24).forEachIndexed { index, value ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .fillMaxWidth()
                                .background(
                                    if (index < 4) Color.Cyan.copy(alpha = 0.1f) else Color(0x22FFFFFF), 
                                    RoundedCornerShape(2.dp)
                                ), 
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val h = (((value + 1f) / 2f).coerceIn(0f, 1f) * 40).dp
                            val barColor = when {
                                index < 4 -> Color.Cyan // 搖桿主軸
                                abs(value) > 0.1f -> Color(0xFFC6FF00) // 有動作的輔助通道 (螢光綠)
                                else -> Color.Gray
                            }
                            Box(modifier = Modifier.height(h).fillMaxWidth().background(barColor, RoundedCornerShape(2.dp)))
                        }
                        Text(
                            text = "${index + 1}", 
                            fontSize = 5.sp, 
                            color = if (index < 4) Color.Cyan else Color.Gray,
                            fontWeight = if (index < 4) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticSmallLabel(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", color = Color.Gray, fontSize = 9.sp)
        Text(value, color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
