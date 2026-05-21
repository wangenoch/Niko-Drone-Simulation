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
import com.horizon.caadronesimulator.ui.theme.NikoTheme
import java.util.Locale

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

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
        
        // 1. [v1.7.6] 通訊連線 Bar (Flavor 隔離組件)
        HardwareConnectionHeader(
            state = state,
            inputMode = inputMode,
            isSignalActive = isSignalActive,
            showHardwareMonitor = showHardwareMonitor,
            onUpdateInputMode = onUpdateInputMode,
            onToggleHardwareMonitor = onToggleHardwareMonitor
        )

        // 2. 診斷面板 (專家模式保護：僅在解鎖且開啟監測時顯示)
        AnimatedVisibility(visible = !state.isExpertModeLocked && showHardwareMonitor, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                HardwareDiagnosticPanel(
                    serialByteCount = serialByteCount.toLong(), linkType = linkType, activeSerialPath = activeSerialPath, detectedProtocol = detectedProtocol, lockedProtocol = lockedProtocol, conflictPid = conflictPid, isSerialConflict = isSerialConflict, packetsPerSecond = packetsPerSecond, bufferUsage = bufferUsage, rawBytesCount = rawBytesCount, isSignalActive = isSignalActive, baudRate = baudRate, diagnosticLog = diagnosticLog, rawHexData = rawHexData, rawChannels = rawChannels, isLogcatEnabled = isLogcatEnabled, logcatContent = logcatContent, isMappingUnlocked = isMappingUnlocked, networkHost = state.networkHost, networkPort = state.networkPort, networkProtocol = state.networkProtocol, isNetworkConnected = state.isNetworkConnected, connectionStatus = connectionStatus, jitter = jitter, stability = stability,
                    isExpertLocked = state.isExpertModeLocked,
                    onUpdateLockedProtocol = onUpdateLockedProtocol, onUpdateBaudRate = onUpdateBaudRate, 
                    onUpdateLockedPath = onUpdateLockedPath, 
                    onOpenNetworkSettings = onOpenNetworkSettings, 
                    onExportLog = onExportLog, onToggleLogcat = onToggleLogcat, onClearLogcat = onClearLogcat, onScanUsb = onScanUsb, 
                    onUpdateNetworkHost = { state.networkHost = it }, 
                    onUpdateNetworkPort = { state.networkPort = it }, 
                    onUpdateNetworkProtocol = { state.networkProtocol = it }, 
                    onToggleNetworkConnection = { onToggleNetworkConnection(it) }, 
                    onToggleMappingUnlock = onToggleMappingUnlock, newHardwareDetected = state.newHardwareDetected,
                    availablePorts = availablePorts,
                    onSelectHardwareMode = { mode -> if (mode == -1) { state.newHardwareDetected = null } else { onUpdateInputMode(mode); state.newHardwareDetected = null } }
                )
        }


        // 3. 下方映射與靈敏度區塊 (對稱對齊版)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), 
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 左面板：核心功能按鈕與映射列表
            Surface(modifier = Modifier.weight(1.1f), color = NikoTheme.colors.textPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = onStartWizard, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(R.string.joystick_btn_wizard), color = Color.White, fontSize = 10.sp) }
                        Button(onClick = onStartCalibration, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(R.string.joystick_btn_recalibrate), color = Color.White, fontSize = 10.sp) }
                        Button(onClick = onOpenAuxMapping, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2)), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(R.string.joystick_btn_aux), color = Color.White, fontSize = 10.sp) }
                    }
                    Spacer(Modifier.height(10.dp))
                    val isMappingLockedByProtocol = state.hardwareProfile?.driver?.isMappingProtected == true && !state.isMappingUnlocked && !com.horizon.caadronesimulator.logic.HardwareRegistry.debugForceUnlockAll
                    
                    val labels = listOf(
                        stringResource(R.string.joystick_label_throttle_short),
                        stringResource(R.string.joystick_label_rudder),
                        stringResource(R.string.joystick_label_elevator),
                        stringResource(R.string.joystick_label_aileron)
                    )
                    
                    labels.forEachIndexed { i, label ->
                        val m = when(i) { 0 -> mappingLY; 1 -> mappingLX; 2 -> mappingRY; else -> mappingRX }
                        val k = when(i) { 0 -> "ly"; 1 -> "lx"; 2 -> "ry"; else -> "rx" }
                        CompactMappingRow(label, m, k, isAutoBinding, onStartBinding, onToggleInvert, onManualBind, inputMode, isMappingLockedByProtocol)
                        if (i < 3) Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onModeChange(if(joystickMode > 1) joystickMode - 1 else 4) }, modifier = Modifier.size(24.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(16.dp)) }
                        Text(stringResource(R.string.joystick_mode_selector, joystickMode), color = NikoTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                        IconButton(onClick = { onModeChange(if(joystickMode < 4) joystickMode + 1 else 1) }, modifier = Modifier.size(24.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            // 右面板：靈敏度/曲線調整 (加入自適應間距以對齊底部)
            Surface(modifier = Modifier.weight(1.0f).fillMaxHeight(), color = NikoTheme.colors.textPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.joystick_section_rates), color = NikoTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onResetRates, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(22.dp)) { Text(stringResource(R.string.action_reset), color = NikoTheme.colors.primary, fontSize = 10.sp) }
                        Switch(checked = useGlobalRates, onCheckedChange = onToggleGlobalRates, modifier = Modifier.scale(0.5f), colors = SwitchDefaults.colors(checkedThumbColor = NikoTheme.colors.primary))
                    }
                    
                    // [v1.5.2 優化] 移除 SpaceBetween，改用與左側一致的固定間距，達成自然且飽滿的視覺對齊
                    Spacer(Modifier.height(10.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (useGlobalRates) {
                            ProfessionalSlider("Rate", globalRate, 0.1f..2.0f, onUpdateGlobalRate)
                            ProfessionalSlider("Expo", globalExpo, 0.0f..1.0f, onUpdateGlobalExpo)
                        } else {
                            Button(onClick = { onToggleShowIndividual(true) }, modifier = Modifier.fillMaxWidth().height(38.dp).padding(vertical = 2.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB39DDB))) { 
                                Text(stringResource(R.string.joystick_btn_advanced), fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold) 
                            }
                        }
                        ProfessionalSlider(stringResource(R.string.joystick_deadzone, joystickDeadzone), joystickDeadzone, 0.0f..0.3f, onUpdateDeadzone)
                    }

                    Spacer(Modifier.weight(1f)) // 這裡是關鍵：將剩餘空間推開，使 Checkbox 貼底

                    // 底部 Checkbox 區，在視覺水平線上與左側對齊
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) { 
                        Checkbox(checked = halfThrottle, onCheckedChange = onToggleHalfThrottle, colors = CheckboxDefaults.colors(checkedColor = NikoTheme.colors.primary, uncheckedColor = NikoTheme.colors.textSecondary), modifier = Modifier.scale(0.8f))
                        Text(stringResource(R.string.joystick_half_throttle), color = NikoTheme.colors.textPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMappingRow(
    label: String, 
    mapping: ChannelMapping, 
    key: String, 
    isBinding: String?, 
    onStartBinding: (String) -> Unit, 
    onToggleInvert: (String) -> Unit, 
    onManualBind: (String, Int) -> Unit, 
    inputMode: Int, 
    isLocked: Boolean = false,
    labelWidth: androidx.compose.ui.unit.Dp = 40.dp
) {
    var showPicker by remember { mutableStateOf(false) }
    val isCurrentBinding = isBinding == key
    val chLabel = stringResource(R.string.joystick_label_ch)
    val axisLabel = stringResource(R.string.joystick_label_axis)
    val channelLabelText = if (mapping.axis == -1) "--" else if (inputMode == 1) "$chLabel ${mapping.axis - 100}" else "$axisLabel ${mapping.axis}"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
        Text(label, color = NikoTheme.colors.textPrimary, fontSize = 12.sp, modifier = Modifier.width(labelWidth), maxLines = 1)
        Surface(
            color = if (isLocked) NikoTheme.colors.textSecondary.copy(alpha = 0.1f) else if (isCurrentBinding) NikoTheme.colors.primary.copy(alpha = 0.15f) else NikoTheme.colors.textPrimary.copy(alpha = 0.1f), 
            shape = RoundedCornerShape(4.dp), 
            border = BorderStroke(1.dp, if (isLocked) NikoTheme.colors.divider else if (isCurrentBinding) NikoTheme.colors.primary else NikoTheme.colors.divider),
            modifier = Modifier.weight(1f).height(22.dp).clickable(enabled = !isLocked) { onStartBinding(key) }
        ) {
            Box(contentAlignment = Alignment.Center) { 
                Text(
                    text = if (isLocked) stringResource(R.string.joystick_mapping_locked) else if (isCurrentBinding) stringResource(R.string.joystick_mapping_detecting) else stringResource(R.string.joystick_mapping_click), 
                    color = if (isLocked) NikoTheme.colors.textSecondary else if (isCurrentBinding) NikoTheme.colors.primary else NikoTheme.colors.primary, 
                    fontSize = 11.sp
                ) 
            }
        }
        Spacer(Modifier.width(6.dp))
        Box {
            Surface(
                color = if (isLocked) NikoTheme.colors.textSecondary.copy(alpha = 0.1f) else NikoTheme.colors.surface, 
                shape = RoundedCornerShape(4.dp), 
                border = BorderStroke(1.dp, if (isLocked) NikoTheme.colors.divider else NikoTheme.colors.primary.copy(0.3f)),
                modifier = Modifier.width(50.dp).height(22.dp).clickable(enabled = !isLocked) { showPicker = true }
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(channelLabelText, color = if (isLocked) NikoTheme.colors.textSecondary else NikoTheme.colors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (!isLocked) Icon(Icons.Default.ArrowDropDown, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(12.dp))
                }
            }
            if (!isLocked) {
                DropdownMenu(
                    expanded = showPicker, 
                    onDismissRequest = { showPicker = false }, 
                    modifier = Modifier.background(NikoTheme.colors.panel).heightIn(max = 240.dp),
                    properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.joystick_mapping_unbound), fontSize = 12.sp, color = NikoTheme.colors.textSecondary) }, onClick = { onManualBind(key, -1); showPicker = false })
                    HorizontalDivider(color = NikoTheme.colors.divider)
                    if (inputMode == 1) { (1..24).forEach { ch -> DropdownMenuItem(text = { Text("$chLabel $ch", color = NikoTheme.colors.textPrimary, fontSize = 12.sp) }, onClick = { onManualBind(key, 100 + ch); showPicker = false }) } } 
                    else { (0..47).forEach { ax -> DropdownMenuItem(text = { Text("$axisLabel $ax", color = NikoTheme.colors.textPrimary, fontSize = 12.sp) }, onClick = { onManualBind(key, ax); showPicker = false }) } }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Switch(checked = mapping.inverted, onCheckedChange = { if (!isLocked) onToggleInvert(key) }, modifier = Modifier.scale(0.45f), enabled = !isLocked, colors = SwitchDefaults.colors(checkedThumbColor = NikoTheme.colors.primary))
    }
}

@Composable
fun ProfessionalSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onUpdateValue: (Float) -> Unit) {
    Column {
        Text(label, color = NikoTheme.colors.textSecondary, fontSize = 10.sp)
        Slider(value = value, onValueChange = onUpdateValue, valueRange = range, modifier = Modifier.height(24.dp), colors = SliderDefaults.colors(thumbColor = NikoTheme.colors.primary, activeTrackColor = NikoTheme.colors.primary, inactiveTrackColor = NikoTheme.colors.divider))
    }
}
