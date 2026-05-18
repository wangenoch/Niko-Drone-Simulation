package com.horizon.caadronesimulator.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.ChannelMapping
import com.horizon.caadronesimulator.model.ConnectionStatus
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.ui.hud.MiniStickVisual
import com.horizon.caadronesimulator.ui.overlays.*
import java.util.Locale

@Composable
fun JoystickMappingScreen(
    mappingLY: ChannelMapping, mappingLX: ChannelMapping, mappingRY: ChannelMapping, mappingRX: ChannelMapping,
    isAutoBinding: String?, halfThrottle: Boolean, joystickDeadzone: Float, activeAxis: String, joystickMode: Int,
    stickLX: Float, stickLY: Float, stickRX: Float, stickRY: Float,
    activeHidName: String,
    useGlobalRates: Boolean, globalRate: Float, globalExpo: Float, 
    rateLY: Float, expoLY: Float, rateLX: Float, expoLX: Float, rateRY: Float, expoRY: Float, rateRX: Float, expoRX: Float,
    showIndividualRates: Boolean,
    onStartCalibration: () -> Unit, onStartWizard: () -> Unit,
    onToggleHalfThrottle: (Boolean) -> Unit, onUpdateDeadzone: (Float) -> Unit, onStartBinding: (String) -> Unit,
    onToggleInvert: (String) -> Unit, onManualBind: (String, Int) -> Unit, onModeChange: (Int) -> Unit,
    onToggleGlobalRates: (Boolean) -> Unit, onUpdateGlobalRate: (Float) -> Unit, onUpdateGlobalExpo: (Float) -> Unit,
    onUpdateIndividualRate: (String, Float) -> Unit, onUpdateIndividualExpo: (String, Float) -> Unit,
    onToggleShowIndividual: (Boolean) -> Unit, onResetRates: () -> Unit,
    inputMode: Int, rawChannels: List<Float>, 
    onToggleMappingUnlock: (Boolean) -> Unit,
    activeSerialPath: String, rawHexData: String, linkType: String, baudRate: Int,
    connectionStatus: ConnectionStatus, packetsPerSecond: Int, detectedProtocol: String, 
    isSerialConflict: Boolean, conflictPid: String, rawBytesCount: Int, bufferUsage: String,
    isSignalActive: Boolean, lockedProtocol: String, onUpdateLockedProtocol: (String) -> Unit,
    isLogcatEnabled: Boolean, logcatContent: String,
    onToggleLogcat: (Boolean) -> Unit, onClearLogcat: () -> Unit,
    isHardwareController: Boolean,
    onOpenAuxMapping: () -> Unit = {},
    state: DroneState,
    jitter: String = "0.0 ms",
    stability: String = "100%",
    isMappingUnlocked: Boolean = false,
    isInteractionLocked: Boolean = false,
    shouldShowExpertUI: Boolean = false,
    serialByteCount: Int = 0,
    localSettingsMessage: String? = null,
    onUpdateInputMode: (Int) -> Unit = {},
    onScanUsb: () -> Unit = {},
    onOpenNetworkSettings: () -> Unit = {},
    onUpdateBaudRate: (Int) -> Unit = {},
    onExportLog: () -> Unit = {},
    onToggleNetworkConnection: (Boolean) -> Unit = {},
    onUpdateLockedPath: (String) -> Unit = {}, // [v1.5.2] 新增路徑鎖定參數
    availablePorts: List<String> = emptyList(), // [v1.5.2] 新增可用路徑列表
    showHardwareMonitor: Boolean = false,
    onToggleHardwareMonitor: (Boolean) -> Unit = {},
    diagnosticLog: String = "",
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        
        // 1. Header (專家模式保護：僅在解鎖後顯示)
        if (!state.isExpertModeLocked) {
            Surface(color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("遙控器連線與模式設定", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp)) {
                                Row(modifier = Modifier.padding(2.dp)) {
                                    listOf("外接", "內置").forEachIndexed { idx, name ->
                                        val isSel = inputMode == idx
                                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if(isSel) Color(0xFF333333) else Color.Transparent).border(if(isSel) 1.dp else 0.dp, if(isSel) Color.Gray else Color.Transparent, RoundedCornerShape(4.dp)).clickable { onUpdateInputMode(idx) }.padding(horizontal = 12.dp, vertical = 2.dp)) {
                                            Text(name, color = if(isSel) Color.White else Color.Gray, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(text = if (isSignalActive) "連線正常" else "等待信號...", color = if (isSignalActive) Color.Green else Color.Red, fontSize = 11.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // [v1.6.1] 一鍵鎖定：教官調整完畢後可快速隱藏技術欄位
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { state.isExpertModeLocked = true }) {
                            Text("快速鎖定", color = Color.Yellow.copy(0.6f), fontSize = 10.sp)
                            Switch(
                                checked = false, // 未鎖定狀態下，開關顯示為關閉，點擊後變為 true 並隱藏
                                onCheckedChange = { state.isExpertModeLocked = true }, 
                                modifier = Modifier.scale(0.55f), 
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow)
                            )
                        }
                        
                        IconButton(
                            onClick = { onToggleHardwareMonitor(!showHardwareMonitor) },
                            modifier = Modifier.size(36.dp).background(if (showHardwareMonitor) Color.Cyan.copy(alpha = 0.2f) else Color(0xFF222222), CircleShape).border(1.dp, if (showHardwareMonitor) Color.Cyan else Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.BugReport, null, tint = if (showHardwareMonitor) Color.Cyan else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 2. 診斷面板 (專家模式保護：僅在解鎖且開啟監測時顯示)
        AnimatedVisibility(visible = !state.isExpertModeLocked && showHardwareMonitor, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            HardwareDiagnosticPanel(
                serialByteCount = serialByteCount.toLong(), linkType = linkType, activeSerialPath = activeSerialPath, detectedProtocol = detectedProtocol, lockedProtocol = lockedProtocol, conflictPid = conflictPid, isSerialConflict = isSerialConflict, packetsPerSecond = packetsPerSecond, bufferUsage = bufferUsage, rawBytesCount = rawBytesCount, isSignalActive = isSignalActive, baudRate = baudRate, diagnosticLog = diagnosticLog, rawHexData = rawHexData, rawChannels = rawChannels, isLogcatEnabled = isLogcatEnabled, logcatContent = logcatContent, isMappingUnlocked = isMappingUnlocked, networkHost = state.networkHost, networkPort = state.networkPort, networkProtocol = state.networkProtocol, isNetworkConnected = state.isNetworkConnected, connectionStatus = connectionStatus, jitter = jitter, stability = stability,
                onUpdateLockedProtocol = onUpdateLockedProtocol, onUpdateBaudRate = onUpdateBaudRate, 
                onUpdateLockedPath = onUpdateLockedPath, // 傳遞路徑鎖定回調
                onOpenNetworkSettings = onOpenNetworkSettings, // [v1.5.2] 傳遞網路設定回調
                onExportLog = onExportLog, onToggleLogcat = onToggleLogcat, onClearLogcat = onClearLogcat, onScanUsb = onScanUsb, onUpdateNetworkHost = { state.networkHost = it }, onUpdateNetworkPort = { state.networkPort = it }, onUpdateNetworkProtocol = { state.networkProtocol = it }, onToggleNetworkConnection = { onToggleNetworkConnection(it) }, onToggleMappingUnlock = onToggleMappingUnlock, newHardwareDetected = state.newHardwareDetected,
                availablePorts = availablePorts, // 傳遞可用路徑列表
                onSelectHardwareMode = { mode -> if (mode == -1) { state.newHardwareDetected = null } else { onUpdateInputMode(mode); state.newHardwareDetected = null } }
            )
        }


        // 3. 下方映射與靈敏度區塊 (對稱對齊版)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), 
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 左面板：核心功能按鈕與映射列表
            Surface(modifier = Modifier.weight(1.1f), color = Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = onStartWizard, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("引導 Wizard", fontSize = 10.sp) }
                        Button(onClick = onStartCalibration, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("重新校準", fontSize = 10.sp) }
                        Button(onClick = onOpenAuxMapping, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2)), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("延伸設置", fontSize = 10.sp) }
                    }
                    Spacer(Modifier.height(10.dp))
                    val isMappingLockedByProtocol = state.hardwareProfile?.driver?.isMappingProtected == true && !state.isMappingUnlocked && !com.horizon.caadronesimulator.logic.HardwareRegistry.debugForceUnlockAll
                    
                    listOf("油門", "航向", "俯仰", "橫滾").forEachIndexed { i, label ->
                        val m = when(i) { 0 -> mappingLY; 1 -> mappingLX; 2 -> mappingRY; else -> mappingRX }
                        val k = when(i) { 0 -> "ly"; 1 -> "lx"; 2 -> "ry"; else -> "rx" }
                        CompactMappingRow(label, m, k, isAutoBinding, onStartBinding, onToggleInvert, onManualBind, inputMode, isMappingLockedByProtocol)
                        if (i < 3) Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onModeChange(if(joystickMode > 1) joystickMode - 1 else 4) }, modifier = Modifier.size(24.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.Cyan, modifier = Modifier.size(16.dp)) }
                        Text("Mode $joystickMode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                        IconButton(onClick = { onModeChange(if(joystickMode < 4) joystickMode + 1 else 1) }, modifier = Modifier.size(24.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Cyan, modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            // 右面板：靈敏度/曲線調整 (加入自適應間距以對齊底部)
            Surface(modifier = Modifier.weight(1.0f).fillMaxHeight(), color = Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("靈敏度/曲線", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onResetRates, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(22.dp)) { Text("重置", color = Color.Cyan, fontSize = 10.sp) }
                        Switch(checked = useGlobalRates, onCheckedChange = onToggleGlobalRates, modifier = Modifier.scale(0.5f))
                    }
                    
                    // [v1.5.2 優化] 移除 SpaceBetween，改用與左側一致的固定間距，達成自然且飽滿的視覺對齊
                    Spacer(Modifier.height(10.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (useGlobalRates) {
                            ProfessionalSlider("Rate", globalRate, 0.1f..2.0f, onUpdateGlobalRate)
                            ProfessionalSlider("Expo", globalExpo, 0.0f..1.0f, onUpdateGlobalExpo)
                        } else {
                            Button(onClick = { onToggleShowIndividual(true) }, modifier = Modifier.fillMaxWidth().height(38.dp).padding(vertical = 2.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB39DDB))) { 
                                Text("進階設定 ➔", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold) 
                            }
                        }
                        ProfessionalSlider("死區 DZ: " + "%.2f".format(Locale.US, joystickDeadzone), joystickDeadzone, 0.0f..0.3f, onUpdateDeadzone)
                    }

                    Spacer(Modifier.weight(1f)) // 這裡是關鍵：將剩餘空間推開，使 Checkbox 貼底

                    // 底部 Checkbox 區，在視覺水平線上與左側對齊
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) { 
                        Checkbox(checked = halfThrottle, onCheckedChange = onToggleHalfThrottle, colors = CheckboxDefaults.colors(checkedColor = Color.Cyan, uncheckedColor = Color.Gray), modifier = Modifier.scale(0.8f))
                        Text("半油門模式", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMappingRow(label: String, mapping: ChannelMapping, key: String, isBinding: String?, onStartBinding: (String) -> Unit, onToggleInvert: (String) -> Unit, onManualBind: (String, Int) -> Unit, inputMode: Int, isLocked: Boolean = false) {
    var showPicker by remember { mutableStateOf(false) }
    val isCurrentBinding = isBinding == key
    val channelLabelText = if (mapping.axis == -1) "--" else if (inputMode == 1) "CH ${mapping.axis - 100}" else "AX ${mapping.axis}"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(40.dp))
        Surface(
            color = if (isLocked) Color.Gray.copy(alpha = 0.1f) else if (isCurrentBinding) Color.Cyan.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.4f), 
            shape = RoundedCornerShape(4.dp), 
            border = BorderStroke(1.dp, if (isLocked) Color.White.copy(0.05f) else if (isCurrentBinding) Color.Cyan else Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.weight(1f).height(22.dp).clickable(enabled = !isLocked) { onStartBinding(key) }
        ) {
            Box(contentAlignment = Alignment.Center) { 
                Text(
                    text = if (isLocked) "協議已鎖定" else if (isCurrentBinding) "偵測中" else "點擊映射", 
                    color = if (isLocked) Color.Gray else if (isCurrentBinding) Color.Cyan else Color(0xFF00B0FF), 
                    fontSize = 11.sp
                ) 
            }
        }
        Spacer(Modifier.width(6.dp))
        Box {
            Surface(
                color = if (isLocked) Color.Gray.copy(alpha = 0.1f) else Color(0xFF263238), 
                shape = RoundedCornerShape(4.dp), 
                border = BorderStroke(1.dp, if (isLocked) Color.White.copy(0.05f) else Color.Cyan.copy(0.3f)),
                modifier = Modifier.width(50.dp).height(22.dp).clickable(enabled = !isLocked) { showPicker = true }
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(channelLabelText, color = if (isLocked) Color.Gray else Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (!isLocked) Icon(Icons.Default.ArrowDropDown, null, tint = Color.Cyan, modifier = Modifier.size(12.dp))
                }
            }
            if (!isLocked) {
                DropdownMenu(
                    expanded = showPicker, 
                    onDismissRequest = { showPicker = false }, 
                    modifier = Modifier.background(Color(0xFF1B2535)).heightIn(max = 240.dp),
                    properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(text = { Text("未設置", fontSize = 12.sp, color = Color.Gray) }, onClick = { onManualBind(key, -1); showPicker = false })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    if (inputMode == 1) { (1..24).forEach { ch -> DropdownMenuItem(text = { Text("CH $ch", fontSize = 12.sp) }, onClick = { onManualBind(key, 100 + ch); showPicker = false }) } } 
                    else { (0..47).forEach { ax -> DropdownMenuItem(text = { Text("Axis $ax", fontSize = 12.sp) }, onClick = { onManualBind(key, ax); showPicker = false }) } }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Switch(checked = mapping.inverted, onCheckedChange = { if (!isLocked) onToggleInvert(key) }, modifier = Modifier.scale(0.45f), enabled = !isLocked)
    }
}

@Composable
fun ProfessionalSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onUpdateValue: (Float) -> Unit) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Slider(value = value, onValueChange = onUpdateValue, valueRange = range, modifier = Modifier.height(24.dp), colors = SliderDefaults.colors(thumbColor = Color(0xFFB39DDB), activeTrackColor = Color(0xFF7E57C2), inactiveTrackColor = Color(0x22FFFFFF)))
    }
}
