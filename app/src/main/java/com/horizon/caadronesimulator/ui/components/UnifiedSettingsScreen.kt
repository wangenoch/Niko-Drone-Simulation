package com.horizon.caadronesimulator.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.SettingsTab
import com.horizon.caadronesimulator.model.StickInputState
import kotlinx.coroutines.delay

@Composable
fun UnifiedSettingsScreen(
    state: DroneState,
    stickState: StickInputState,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: () -> Unit = {},
    onScanUsb: () -> Unit = {},
    onUpdateBaudRate: (Int) -> Unit = {},
    onUpdateInputMode: (Int) -> Unit = {},
    onExportLog: () -> Unit = {},
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    // [v1.2.81 階段三修正] 局部回饋自動隱藏
    LaunchedEffect(state.localSettingsMessage) {
        if (state.localSettingsMessage != null) {
            delay(2000)
            state.localSettingsMessage = null
        }
    }

    // [v1.2.71] 首次進入分頁時自動觸發導覽
    LaunchedEffect(state.settingsTab) {
        if (state.settingsTab == SettingsTab.CONTROLLER && !state.hasShownJoystickTutorial) {
            onUpdateState { 
                showJoystickTutorial = true
                hasShownJoystickTutorial = true
            }
        } else if (state.settingsTab == SettingsTab.ENVIRONMENT && !state.hasShownClimateTutorial) {
            onUpdateState { 
                showClimateTutorial = true
                hasShownClimateTutorial = true
            }
        }
    }

    var showResetConfirm by remember { mutableStateOf(false) }
    if (showResetConfirm) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).zIndex(500f).pointerInput(Unit) { detectTapGestures { showResetConfirm = false } }, contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.widthIn(max = 320.dp).padding(20.dp).pointerInput(Unit) { detectTapGestures { } }, color = Color(0xFF1B2535), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(0.1f))) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("恢復出廠預設值", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp)); Text("這將清除所有搖桿映射、校準數據與環境設定，並重置無人機位置。確定要繼續嗎？", color = Color.White.copy(0.8f), fontSize = 14.sp, lineHeight = 21.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showResetConfirm = false }) { Text("取消", color = Color.Gray, fontSize = 15.sp) }
                        Spacer(modifier = Modifier.width(16.dp))
                        TextButton(onClick = { showResetConfirm = false; onReset(); onUpdateState { updateFrom(DroneState()); showSettings = true; settingsTab = SettingsTab.SYSTEM; localSettingsMessage = "已恢復預設設定"; showTutorial = true; isMenuExpanded = true } }) { Text("確定重置", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xEE111111)).clickable(enabled = false) {}) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(80.dp).fillMaxHeight().background(Color(0x22FFFFFF)).padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TabIcon(Icons.Default.VideogameAsset, "遙控", state.settingsTab == SettingsTab.CONTROLLER) { onUpdateState { settingsTab = SettingsTab.CONTROLLER } }
                TabIcon(Icons.Default.Cloud, "環境", state.settingsTab == SettingsTab.ENVIRONMENT) { onUpdateState { settingsTab = SettingsTab.ENVIRONMENT } }
                TabIcon(Icons.Default.AirplanemodeActive, "機型", state.settingsTab == SettingsTab.DRONE_SELECTION) { onUpdateState { settingsTab = SettingsTab.DRONE_SELECTION } }
                TabIcon(Icons.Default.Videocam, "相機", state.settingsTab == SettingsTab.CAMERA) { onUpdateState { settingsTab = SettingsTab.CAMERA } }
                TabIcon(Icons.Default.Settings, "一般", state.settingsTab == SettingsTab.SYSTEM) { onUpdateState { settingsTab = SettingsTab.SYSTEM } }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        when(state.settingsTab) { 
                            SettingsTab.ENVIRONMENT -> "🌍 氣候與環境模擬設定"
                            SettingsTab.CAMERA -> "📸 相機視覺與倍率設定"
                            SettingsTab.DRONE_SELECTION -> "🚁 無人機機型選擇"
                            SettingsTab.CONTROLLER -> "🎮 遙控器模式與映射設定"
                            SettingsTab.SYSTEM -> "⚙️ 一般系統設定"
                        }, 
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 20.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = 10.dp, y = (-10).dp)) {
                        // [v1.2.81 階段三] 局部操作回饋 Toast - 改為浮動在頂部中央
                        AnimatedVisibility(
                            visible = state.localSettingsMessage != null,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .background(Color(0xFF2E7D32), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(state.localSettingsMessage ?: "", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (state.settingsTab == SettingsTab.DRONE_SELECTION) {
                            Text("啟用機型物理特性", color = if (state.applyPhysicalSpecs) Color.Cyan else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium); Spacer(modifier = Modifier.width(8.dp))
                            Switch(state.applyPhysicalSpecs, { enabled -> onUpdateState { applyPhysicalSpecs = enabled } }, modifier = Modifier.scale(0.7f), colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Cyan))
                            Spacer(modifier = Modifier.width(8.dp)); Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x22FFFFFF))); Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (state.settingsTab == SettingsTab.ENVIRONMENT) {
                            IconButton(onClick = { onUpdateState { showClimateTutorial = true } }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = Color.Cyan.copy(0.8f), modifier = Modifier.size(24.dp)) }
                        }
                        if (state.settingsTab == SettingsTab.CONTROLLER) {
                            IconButton(onClick = { onUpdateState { showJoystickTutorial = true } }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = Color.Cyan.copy(0.8f), modifier = Modifier.size(24.dp)) }
                        }
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(28.dp)) }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    when(state.settingsTab) {
                        SettingsTab.ENVIRONMENT -> ClimateSettingsScreen(state.windLevel, state.windDirection, state.windVariation, state.windDirVariation, state.timeOfDay, state.shadowIntensity, state.enableAirPressure, { l -> onUpdateState { windLevel = l } }, { d -> onUpdateState { windDirection = d } }, { v -> onUpdateState { windVariation = v } }, { dv -> onUpdateState { windDirVariation = dv } }, { t -> onUpdateState { timeOfDay = t } }, { si -> onUpdateState { shadowIntensity = si } }, { e -> onUpdateState { enableAirPressure = e } }, onTargetPositioned)
                        SettingsTab.CAMERA -> CameraSettingsScreen(
                            cameraMode = state.cameraMode,
                            zoomFactor = state.zoomFactor,
                            cameraTilt = state.cameraTilt,
                            onUpdateCameraMode = { m -> onUpdateState { cameraMode = m } },
                            onUpdateZoom = { z -> onUpdateState { zoomFactor = z } },
                            onUpdateTilt = { t -> onUpdateState { cameraTilt = t } }
                        )
                        SettingsTab.DRONE_SELECTION -> DroneSelectionScreen(state.droneType) { t -> onUpdateState { droneType = t } }
                        SettingsTab.CONTROLLER -> JoystickMappingScreen(
                            mappingLY = state.mappingLY, mappingLX = state.mappingLX, mappingRY = state.mappingRY, mappingRX = state.mappingRX,
                            isAutoBinding = state.isAutoBinding, halfThrottle = state.halfThrottle, joystickDeadzone = state.joystickDeadzone, activeAxis = state.activeAxisLabel, joystickMode = state.joystickMode, 
                            stickLX = if(state.setupWizardStep > 0) stickState.rawLX else stickState.stickLX(state, state.isCalibrating), 
                            stickLY = if(state.setupWizardStep > 0) stickState.rawLY else stickState.stickLY(state, state.isCalibrating), 
                            stickRX = if(state.setupWizardStep > 0) stickState.rawRX else stickState.stickRX(state, state.isCalibrating), 
                            stickRY = if(state.setupWizardStep > 0) stickState.rawRY else stickState.stickRY(state, state.isCalibrating),
                            isCalibrating = state.isCalibrating, calibrationStep = state.calibrationStep, setupWizardStep = state.setupWizardStep,
                            useGlobalRates = state.useGlobalRates, globalRate = state.globalRate, globalExpo = state.globalExpo, rateLY = state.rateLY, expoLY = state.expoLY, rateLX = state.rateLX, expoLX = state.expoLX, rateRY = state.rateRY, expoRY = state.expoRY, rateRX = state.rateRX, expoRX = state.expoRX, showIndividualRates = state.showIndividualRates,
                            onStartCalibration = { onUpdateState { isCalibrating = true; calibrationStep = 1 } }, onNextCalibrationStep = { onUpdateState { calibrationStep += 1 } }, onFinishCalibration = { onUpdateState { isCalibrating = false; calibrationStep = 0 } },
                            onStartWizard = { onUpdateState { setupWizardStep = 1; wizardWaitingForNeutral = false } }, onCancelWizard = { onUpdateState { setupWizardStep = 0; isAutoBinding = null; wizardWaitingForNeutral = false } },
                            isWizardWaiting = state.wizardWaitingForNeutral, wizardCountdown = state.wizardCountdown,
                            onToggleHalfThrottle = { b -> onUpdateState { halfThrottle = b } }, onUpdateDeadzone = { f -> onUpdateState { joystickDeadzone = f } }, onStartBinding = { k -> onUpdateState { isAutoBinding = if (isAutoBinding == k) null else k.ifEmpty { null } } },
                            onToggleInvert = { k -> onUpdateState { when(k) { "ly" -> mappingLY = mappingLY.copy(inverted = !mappingLY.inverted); "lx" -> mappingLX = mappingLX.copy(inverted = !mappingLX.inverted); "ry" -> mappingRY = mappingRY.copy(inverted = !mappingRY.inverted); "rx" -> mappingRX = mappingRX.copy(inverted = !mappingRX.inverted) } } },
                            onManualBind = { k, a -> onUpdateState { val l = if (a >= 101) "Serial CH${a - 100}" else "Axis $a"; val m = com.horizon.caadronesimulator.model.ChannelMapping(a, false, l); when(k) { "ly" -> mappingLY = m; "lx" -> mappingLX = m; "ry" -> mappingRY = m; "rx" -> mappingRX = m } } },
                            onModeChange = { m -> onUpdateState { joystickMode = m } },
                            onToggleGlobalRates = { b -> onUpdateState { useGlobalRates = b } }, onUpdateGlobalRate = { r -> onUpdateState { globalRate = r } }, onUpdateGlobalExpo = { e -> onUpdateState { globalExpo = e } },
                            onUpdateIndividualRate = { k, r -> onUpdateState { when(k) { "ly" -> rateLY = r; "lx" -> rateLX = r; "ry" -> rateRY = r; "rx" -> rateRX = r } } }, onUpdateIndividualExpo = { k, e -> onUpdateState { when(k) { "ly" -> expoLY = e; "lx" -> expoLX = e; "ry" -> expoRY = e; "rx" -> expoRX = e } } },
                            onToggleShowIndividual = { b -> onUpdateState { showIndividualRates = b } }, onResetRates = { onUpdateState { globalRate = 1f; globalExpo = 0f; rateLY = 1f; expoLY = 0f; rateLX = 1f; expoLX = 0f; rateRY = 1f; expoRY = 0f; rateRX = 1f; expoRX = 0f; joystickDeadzone = 0.15f } },
                            inputMode = state.inputMode, usbSerialConnected = state.usbSerialConnected, 
                            isHandshaking = state.isHandshaking, isInteractionLocked = state.isInteractionLocked,
                            isMappingUnlocked = state.isMappingUnlocked, shouldShowExpertUI = state.shouldShowExpertUI,
                            serialByteCount = stickState.serialByteCount.toInt(), localSettingsMessage = state.localSettingsMessage,
                            onUpdateInputMode = onUpdateInputMode, onScanUsb = onScanUsb, onUpdateBaudRate = onUpdateBaudRate, onExportLog = onExportLog,
                            onToggleMappingUnlock = { b -> onUpdateState { isMappingUnlocked = b } },
                            showHardwareMonitor = state.showHardwareMonitor, rawChannels = stickState.rawChannels, onToggleHardwareMonitor = { b -> onUpdateState { showHardwareMonitor = b } }, diagnosticLog = state.diagnosticLog, activeSerialPath = state.activeSerialPath, rawHexData = state.rawHexData, linkType = state.linkType, baudRate = state.baudRate,
                            packetsPerSecond = stickState.packetsPerSecond, detectedProtocol = state.detectedProtocol, isSerialConflict = state.isSerialConflict, conflictPid = state.conflictPid, rawBytesCount = state.rawBytesCount, bufferUsage = state.bufferUsage, isSignalActive = stickState.isSignalActive, lockedProtocol = state.lockedProtocol, onUpdateLockedProtocol = { p -> onUpdateState { lockedProtocol = p } },
                            isLogcatEnabled = state.isLogcatEnabled, logcatContent = state.logcatContent,
                            onToggleLogcat = { b -> onUpdateState { isLogcatEnabled = b } },
                            onClearLogcat = { onUpdateState { logcatContent = "" } },
                            isHardwareController = state.isHardwareController,
                            activeHidName = state.activeHidName,
                            onTargetPositioned = onTargetPositioned
                        )
                        SettingsTab.SYSTEM -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val metrics = LocalContext.current.resources.displayMetrics
                            val resText = "${metrics.widthPixels} x ${metrics.heightPixels} (${metrics.densityDpi} DPI)"
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("裝置解析度", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(resText, color = Color.Cyan.copy(0.7f), fontSize = 12.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("顯示系統狀態欄", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(state.showStatusBar, { b -> onUpdateState { showStatusBar = b } }, modifier = Modifier.scale(0.7f)) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("開啟設定時暫停模擬", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(state.pauseInSettings, { b -> onUpdateState { pauseInSettings = b } }, modifier = Modifier.scale(0.7f)) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("對調側邊調整桿位置 (左/右)", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(state.reverseSliderSides, { b -> onUpdateState { reverseSliderSides = b } }, modifier = Modifier.scale(0.7f)) }
                            HorizontalDivider(color = Color(0x11FFFFFF))
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { onUpdateState { showTutorial = true; showSettings = false } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0x332196F3)), shape = RoundedCornerShape(6.dp), modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), border = BorderStroke(0.5.dp, Color.Cyan.copy(0.3f))) { Icon(Icons.AutoMirrored.Filled.Help, null, tint = Color.White, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("操作導覽", color = Color.White, fontSize = 10.sp) }
                                Button(onClick = { showResetConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5252)), shape = RoundedCornerShape(6.dp), modifier = Modifier.height(28.dp).widthIn(max = 140.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), border = BorderStroke(0.5.dp, Color.Red.copy(0.3f))) { Icon(Icons.Default.DeleteForever, null, tint = Color.White, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("還原預設", color = Color.White, fontSize = 10.sp) }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onUpdateState { showUpdateNotice = true } }
                                        .padding(2.dp)
                                ) { 
                                    Text(AppConfig.DEVELOPER, color = Color.Gray, fontSize = 9.sp)
                                    Text("V${AppConfig.CURRENT_VERSION} (${AppConfig.RELEASE_DATE})", color = Color.Cyan.copy(0.6f), fontSize = 9.sp) 
                                    Text("查看更新與感謝 ➔", color = Color.Gray.copy(0.5f), fontSize = 7.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabIcon(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (isSelected) Color(0x44FFFFFF) else Color.Transparent).clickable { onClick() }.padding(10.dp)) { Icon(icon, label, tint = if (isSelected) Color.Cyan else Color.White, modifier = Modifier.size(30.dp)) }
}
