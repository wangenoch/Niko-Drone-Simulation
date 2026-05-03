package com.horizon.caadronesimulator.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.horizon.caadronesimulator.model.ChannelMapping
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import java.util.Locale
import kotlin.math.*

@Composable
fun JoystickMappingScreen(
    mappingLY: ChannelMapping,
    mappingLX: ChannelMapping,
    mappingRY: ChannelMapping,
    mappingRX: ChannelMapping,
    isAutoBinding: String?,
    halfThrottle: Boolean,
    joystickDeadzone: Float,
    activeAxis: String,
    joystickMode: Int,
    stickLX: Float, stickLY: Float,
    stickRX: Float, stickRY: Float,
    isCalibrating: Boolean,
    calibrationStep: Int,
    setupWizardStep: Int,
    wizardCountdown: Int = 0,
    isWizardWaiting: Boolean = false,
    activeHidName: String = "通用手把",
    
    // 靈敏度與曲線參數
    useGlobalRates: Boolean,
    globalRate: Float,
    globalExpo: Float,
    rateLY: Float, expoLY: Float,
    rateLX: Float, expoLX: Float,
    rateRY: Float, expoRY: Float,
    rateRX: Float, expoRX: Float,
    showIndividualRates: Boolean,

    onStartCalibration: () -> Unit,
    onNextCalibrationStep: () -> Unit,
    onFinishCalibration: () -> Unit,
    onStartWizard: () -> Unit,
    onCancelWizard: () -> Unit,
    onToggleHalfThrottle: (Boolean) -> Unit,
    onUpdateDeadzone: (Float) -> Unit,
    onStartBinding: (String) -> Unit,
    onToggleInvert: (String) -> Unit,
    onManualBind: (String, Int) -> Unit,
    onModeChange: (Int) -> Unit,
    
    // 靈敏度回傳
    onToggleGlobalRates: (Boolean) -> Unit,
    onUpdateGlobalRate: (Float) -> Unit,
    onUpdateGlobalExpo: (Float) -> Unit,
    onUpdateIndividualRate: (String, Float) -> Unit,
    onUpdateIndividualExpo: (String, Float) -> Unit,
    onToggleShowIndividual: (Boolean) -> Unit,
    onResetRates: () -> Unit,

    // 診斷控制
    showHardwareMonitor: Boolean,
    rawChannels: List<Float>,
    onToggleHardwareMonitor: (Boolean) -> Unit,
    diagnosticLog: String,
    activeSerialPath: String,
    rawHexData: String,
    linkType: String = "None",
    baudRate: Int = 115200,
    
    // 新增診斷參數
    packetsPerSecond: Int = 0,
    detectedProtocol: String = "未知",
    isSerialConflict: Boolean = false,
    conflictPid: String = "None",
    rawBytesCount: Int = 0,
    bufferUsage: String = "0/512",
    isSignalActive: Boolean = false,
    onExportLog: () -> Unit = {},
    lockedProtocol: String = "",
    onUpdateLockedProtocol: (String) -> Unit = {},

    // Logcat 監測
    isLogcatEnabled: Boolean = false,
    logcatContent: String = "",
    onToggleLogcat: (Boolean) -> Unit = {},
    onClearLogcat: () -> Unit = {},

    // 輸入源與連線狀態控制
    isHardwareController: Boolean = false,
    inputMode: Int,
    usbSerialConnected: Boolean,
    isHandshaking: Boolean = false,
    isInteractionLocked: Boolean = false,
    isMappingUnlocked: Boolean = false,
    shouldShowExpertUI: Boolean = true,
    serialByteCount: Int,
    localSettingsMessage: String?,
    onUpdateInputMode: (Int) -> Unit,
    onScanUsb: () -> Unit,
    onUpdateBaudRate: (Int) -> Unit = {},
    onToggleMappingUnlock: (Boolean) -> Unit = {},

    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    val configuration = LocalConfiguration.current
    val isSmallHeight = configuration.screenHeightDp < 420
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xEE111111)).clickable(enabled = false) {}) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header (標題與模式切換已整合至 HardwareConnectionSection，此處僅留視覺輔助)
            Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("搖桿與輸入源設定", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (inputMode == 0) {
                        Text("當前裝置：$activeHidName", color = Color.Green.copy(alpha = 0.7f), fontSize = 9.sp)
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniStickVisual(joystickMode, true, stickLX, stickLY)
                    Spacer(modifier = Modifier.width(6.dp))
                    MiniStickVisual(joystickMode, false, stickRX, stickRY)
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                Surface(color = Color.Black, shape = RoundedCornerShape(4.dp)) {
                    Text("活動: $activeAxis", color = Color.Cyan, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // [v1.2.68] 呼叫獨立出的硬體連線組件
                HardwareConnectionSection(
                    inputMode = inputMode,
                    isHardwareController = isHardwareController,
                    usbSerialConnected = usbSerialConnected,
                    isHandshaking = isHandshaking,
                    isInteractionLocked = isInteractionLocked,
                    serialByteCount = serialByteCount,
                    infoMessage = localSettingsMessage,
                    showHardwareMonitor = showHardwareMonitor,
                    onUpdateInputMode = onUpdateInputMode,
                    onScanUsb = onScanUsb,
                    onToggleHardwareMonitor = onToggleHardwareMonitor,
                    onTargetPositioned = onTargetPositioned
                )

            // --- 獨立硬體診斷面板 (已抽離至 HardwareDiagnosticPanel.kt) ---
            if (showHardwareMonitor) {
                HardwareDiagnosticPanel(
                    serialByteCount = serialByteCount.toLong(),
                    linkType = linkType,
                    activeSerialPath = activeSerialPath,
                    detectedProtocol = detectedProtocol,
                    lockedProtocol = lockedProtocol,
                    conflictPid = conflictPid,
                    isSerialConflict = isSerialConflict,
                    packetsPerSecond = packetsPerSecond,
                    bufferUsage = bufferUsage,
                    rawBytesCount = rawBytesCount,
                    isSignalActive = isSignalActive,
                    baudRate = baudRate,
                    diagnosticLog = diagnosticLog,
                    rawHexData = rawHexData,
                    rawChannels = rawChannels,
                    isLogcatEnabled = isLogcatEnabled,
                    logcatContent = logcatContent,
                    isMappingUnlocked = isMappingUnlocked,
                    onUpdateLockedProtocol = onUpdateLockedProtocol,
                    onUpdateBaudRate = onUpdateBaudRate,
                    onExportLog = onExportLog,
                    onToggleLogcat = onToggleLogcat,
                    onClearLogcat = onClearLogcat,
                    onToggleMappingUnlock = onToggleMappingUnlock
                )
            }

            // 原有的設定按鈕列
            if (isSmallHeight) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Color(0x11FFFFFF), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1.0f)) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (shouldShowExpertUI) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onStartWizard, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(24.dp).weight(1f).onGloballyPositioned { onTargetPositioned("wizard", it.boundsInRoot()) }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.7f))) { Text("引導設定", fontSize = 10.sp) }
                                    Button(onClick = onStartCalibration, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(24.dp).weight(1f).onGloballyPositioned { onTargetPositioned("calib", it.boundsInRoot()) }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.7f))) { Text("校準", fontSize = 10.sp) }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            val labelLY = when(joystickMode) { 1 -> "俯仰"; 2 -> "油門"; 3 -> "俯仰"; 4 -> "俯仰"; else -> "LY" }
                            val labelLX = when(joystickMode) { 3 -> "橫滾"; 4 -> "橫滾"; else -> "航向" }
                            val labelRY = when(joystickMode) { 1 -> "油門"; 2 -> "俯仰"; 3 -> "油門"; 4 -> "油門"; else -> "RY" }
                            val labelRX = when(joystickMode) { 3 -> "航向"; 4 -> "航向"; else -> "橫滾" }
                            CompactMappingLine(labelLY, mappingLY, "ly", isAutoBinding, stickLY, onStartBinding, onToggleInvert, onManualBind, isSmall = true, inputMode = inputMode, showExpert = shouldShowExpertUI, onTargetPositioned = onTargetPositioned)
                            CompactMappingLine(labelLX, mappingLX, "lx", isAutoBinding, stickLX, onStartBinding, onToggleInvert, onManualBind, isSmall = true, inputMode = inputMode, showExpert = shouldShowExpertUI, onTargetPositioned = onTargetPositioned)
                            CompactMappingLine(labelRY, mappingRY, "ry", isAutoBinding, stickRY, onStartBinding, onToggleInvert, onManualBind, isSmall = true, inputMode = inputMode, showExpert = shouldShowExpertUI, onTargetPositioned = onTargetPositioned)
                            CompactMappingLine(labelRX, mappingRX, "rx", isAutoBinding, stickRX, onStartBinding, onToggleInvert, onManualBind, isSmall = true, inputMode = inputMode, showExpert = shouldShowExpertUI, onTargetPositioned = onTargetPositioned)
                            HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("mode", it.boundsInRoot()) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                IconButton(modifier = Modifier.size(24.dp), onClick = { onModeChange(if(joystickMode > 1) joystickMode - 1 else 4) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.Cyan, modifier = Modifier.size(18.dp)) }
                                Text("Mode $joystickMode", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                IconButton(modifier = Modifier.size(24.dp), onClick = { onModeChange(if(joystickMode < 4) joystickMode + 1 else 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Cyan, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1.0f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // [v1.2.68] 呼叫獨立的手感靈敏度區塊
                        JoystickRatesSection(
                            useGlobalRates = useGlobalRates,
                            globalRate = globalRate,
                            globalExpo = globalExpo,
                            rateLY = rateLY, expoLY = expoLY,
                            rateLX = rateLX, expoLX = expoLX,
                            rateRY = rateRY, expoRY = expoRY,
                            rateRX = rateRX, expoRX = expoRX,
                            showIndividualRates = showIndividualRates,
                            onToggleGlobalRates = onToggleGlobalRates,
                            onUpdateGlobalRate = onUpdateGlobalRate,
                            onUpdateGlobalExpo = onUpdateGlobalExpo,
                            onUpdateIndividualRate = onUpdateIndividualRate,
                            onUpdateIndividualExpo = onUpdateIndividualExpo,
                            onToggleShowIndividual = onToggleShowIndividual,
                            onResetRates = onResetRates,
                            onTargetPositioned = onTargetPositioned
                        )

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("死區 DZ: ${String.format(Locale.US, "%.2f", joystickDeadzone)}", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(65.dp))
                            Slider(value = joystickDeadzone, onValueChange = onUpdateDeadzone, valueRange = 0f..0.5f, modifier = Modifier.weight(1f).height(16.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                            Checkbox(checked = halfThrottle, onCheckedChange = onToggleHalfThrottle, modifier = Modifier.size(18.dp).scale(0.8f), colors = CheckboxDefaults.colors(checkedColor = Color.Cyan))
                            Text("半油門", color = Color.Gray, fontSize = 9.sp)
                        }
                    }
                }
            } else {
                // 標準直向排版
                Surface(color = Color(0x11FFFFFF), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (shouldShowExpertUI) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = onStartWizard, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(30.dp).weight(1f).onGloballyPositioned { onTargetPositioned("wizard", it.boundsInRoot()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) { Text("引導設定", fontSize = 12.sp) }
                                Button(onClick = onStartCalibration, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(30.dp).weight(1f).onGloballyPositioned { onTargetPositioned("calib", it.boundsInRoot()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("校準", fontSize = 12.sp) }
                            }
                            HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                        }
                        val labelLY = when(joystickMode) { 1 -> "俯仰 Pitch"; 2 -> "油門 Throttle"; 3 -> "俯仰 Pitch"; 4 -> "俯仰 Pitch"; else -> "LY" }
                        val labelLX = when(joystickMode) { 3 -> "橫滾 Roll"; 4 -> "橫滾 Roll"; else -> "航向 Yaw" }
                        val labelRY = when(joystickMode) { 1 -> "油門 Throttle"; 2 -> "俯仰 Pitch"; 3 -> "油門 Throttle"; 4 -> "油門 Throttle"; else -> "RY" }
                        val labelRX = when(joystickMode) { 3 -> "航向 Yaw"; 4 -> "航向 Yaw"; else -> "橫滾 Roll" }
                        CompactMappingLine("$labelLY (左垂直)", mappingLY, "ly", isAutoBinding, stickLY, onStartBinding, onToggleInvert, onManualBind, inputMode = inputMode, showExpert = shouldShowExpertUI, onTargetPositioned = onTargetPositioned)
                        HorizontalDivider(color = Color(0x0AFFFFFF))
                        CompactMappingLine("$labelLX (左水平)", mappingLX, "lx", isAutoBinding, stickLX, onStartBinding, onToggleInvert, onManualBind, inputMode = inputMode, showExpert = shouldShowExpertUI, onTargetPositioned = onTargetPositioned)
                        HorizontalDivider(color = Color(0x0AFFFFFF))
                        CompactMappingLine("$labelRY (右垂直)", mappingRY, "ry", isAutoBinding, stickRY, onStartBinding, onToggleInvert, onManualBind, inputMode = inputMode, showExpert = shouldShowExpertUI, onTargetPositioned = onTargetPositioned)
                        HorizontalDivider(color = Color(0x0AFFFFFF))
                        CompactMappingLine("$labelRX (右水平)", mappingRX, "rx", isAutoBinding, stickRX, onStartBinding, onToggleInvert, onManualBind, inputMode = inputMode, showExpert = shouldShowExpertUI, onTargetPositioned = onTargetPositioned)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                // [v1.2.68] 呼叫獨立的手感靈敏度區塊 (直向排版)
                JoystickRatesSection(
                    useGlobalRates = useGlobalRates,
                    globalRate = globalRate,
                    globalExpo = globalExpo,
                    rateLY = rateLY, expoLY = expoLY,
                    rateLX = rateLX, expoLX = expoLX,
                    rateRY = rateRY, expoRY = expoRY,
                    rateRX = rateRX, expoRX = expoRX,
                    showIndividualRates = showIndividualRates,
                    onToggleGlobalRates = onToggleGlobalRates,
                    onUpdateGlobalRate = onUpdateGlobalRate,
                    onUpdateGlobalExpo = onUpdateGlobalExpo,
                    onUpdateIndividualRate = onUpdateIndividualRate,
                    onUpdateIndividualExpo = onUpdateIndividualExpo,
                    onToggleShowIndividual = onToggleShowIndividual,
                    onResetRates = onResetRates,
                    onTargetPositioned = onTargetPositioned
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("mode", it.boundsInRoot()) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { onModeChange(if(joystickMode > 1) joystickMode - 1 else 4) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.Cyan) }
                    Text("Mode $joystickMode", color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onModeChange(if(joystickMode < 4) joystickMode + 1 else 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Cyan) }
                }
            }
        }

        // [v1.2.68] 呼叫獨立的校準圖層
        JoystickCalibrationOverlay(
            isCalibrating = isCalibrating,
            calibrationStep = calibrationStep,
            joystickMode = joystickMode,
            stickLX = stickLX,
            stickLY = stickLY,
            stickRX = stickRX,
            stickRY = stickRY,
            onNextStep = onNextCalibrationStep,
            onFinish = onFinishCalibration
        )

        // [v1.2.68] 呼叫獨立的引導精靈圖層
        JoystickWizardOverlay(
            setupWizardStep = setupWizardStep,
            isWizardWaiting = isWizardWaiting,
            wizardCountdown = wizardCountdown,
            stickLX = stickLX,
            stickLY = stickLY,
            stickRX = stickRX,
            stickRY = stickRY,
            onCancelWizard = onCancelWizard
        )
    }
}

@Composable
fun CompactMappingLine(label: String, mapping: ChannelMapping, key: String, isBinding: String?, liveValue: Float, onStartBinding: (String) -> Unit, onToggleInvert: (String) -> Unit, onManualBind: (String, Int) -> Unit, isSmall: Boolean = false, inputMode: Int = 0, showExpert: Boolean = true, onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().height(if (isSmall) 22.dp else 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(if (isSmall) 60.dp else 165.dp)) {
            Text(text = label, color = Color.White, fontSize = if (isSmall) 10.sp else 13.sp, maxLines = 1)
            // 即時數值條 (Mini Progress Bar)
            Box(modifier = Modifier.fillMaxWidth(0.8f).height(2.dp).background(Color.White.copy(alpha = 0.1f))) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(abs(liveValue).coerceIn(0f, 1f))
                        .align(if (liveValue >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .background(if (abs(liveValue) > 0.9f) Color.Red else Color.Cyan)
                )
            }
        }
        
        if (showExpert) {
            Button(onClick = { onStartBinding(key) }, colors = ButtonDefaults.buttonColors(containerColor = if(isBinding == key) Color.Cyan else Color(0x22FFFFFF)), shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(0.dp), modifier = Modifier.height(if (isSmall) 18.dp else 26.dp).weight(1f).onGloballyPositioned { if (key == "ly") onTargetPositioned("auto_bind", it.boundsInRoot()) }) { Text(if(isBinding == key) "偵測" else "Auto", fontSize = 9.sp, color = if(isBinding == key) Color.Black else Color.White) }
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.width(if (isSmall) 55.dp else 90.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(if (isSmall) 18.dp else 26.dp).background(Color(0x33FFFFFF), RoundedCornerShape(5.dp)).clickable { expanded = true }, contentAlignment = Alignment.Center) { 
                    val displayLabel = if(mapping.axis == -1) "未設" 
                                       else if (mapping.axis >= 101) "S${mapping.axis - 100}"
                                       else mapping.label.replace("Axis ","A")
                    Text(text = displayLabel, color = if(mapping.axis == -1) Color.Gray else Color.Cyan, fontSize = 9.sp)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(140.dp), properties = PopupProperties(focusable = false)) {
                    Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        DropdownMenuItem(
                            text = { Text("🚫 取消映射 (未設定)", fontSize = 11.sp, color = Color.Gray) }, 
                            onClick = { onManualBind(key, -1); expanded = false }
                        )
                        HorizontalDivider(color = Color(0x11FFFFFF))

                        if (inputMode == 0) {
                            // 外接模式：僅顯示 Axis 0~28
                            (0..28).forEach { axisId -> 
                                DropdownMenuItem(text = { Text("Axis $axisId", fontSize = 11.sp) }, onClick = { onManualBind(key, axisId); expanded = false }) 
                            }
                        } else {
                            // 內置模式：僅顯示 Serial CH 1~24
                            (1..24).forEach { ch ->
                                val serialAxisId = 100 + ch
                                DropdownMenuItem(text = { Text("Serial CH$ch", fontSize = 11.sp, color = if(ch <= 4) Color.Cyan else Color.White) }, onClick = { onManualBind(key, serialAxisId); expanded = false })
                            }
                        }
                    }
                }
            }
        } else {
            // 極簡模式：僅顯示已鎖定的通道編號
            val displayLabel = if(mapping.axis >= 101) "已鎖定 S${mapping.axis - 100}" else "自動對準中"
            Box(modifier = Modifier.weight(1f).height(if (isSmall) 18.dp else 26.dp), contentAlignment = Alignment.CenterStart) {
                Text(displayLabel, color = Color.Cyan.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.width(4.dp))
        Switch(checked = mapping.inverted, onCheckedChange = { onToggleInvert(key) }, modifier = Modifier.scale(if(isSmall) 0.45f else 0.6f).onGloballyPositioned { if (key == "ly") onTargetPositioned("invert", it.boundsInRoot()) }, colors = SwitchDefaults.colors(checkedTrackColor = Color.Red))
    }
}

@Composable
fun MiniStickVisual(mode: Int, isLeft: Boolean, x: Float, y: Float) {
    Box(modifier = Modifier.size(30.dp).background(Color(0x44000000), RoundedCornerShape(4.dp)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))) {
        Box(modifier = Modifier.size(6.dp).offset(x = (x * 10).dp, y = -(y * 10).dp).align(Alignment.Center).background(Color.Cyan, CircleShape))
        val label = if (isLeft) { when(mode) { 1 -> "Y/P"; 3 -> "R/P"; else -> "Y/T" } } else { when(mode) { 1 -> "R/T"; 3 -> "Y/T"; else -> "R/P" } }
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 6.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp))
    }
}
