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
import androidx.compose.ui.graphics.Brush
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
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
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
    onSaveSettings: () -> Unit = {},
    onOpenNetworkSettings: () -> Unit = {},
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    LaunchedEffect(state.localSettingsMessage) {
        if (state.localSettingsMessage != null) {
            delay(2000)
            state.localSettingsMessage = null
        }
    }

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
                TabIcon(Icons.Default.Visibility, "視覺", state.settingsTab == SettingsTab.CAMERA) { onUpdateState { settingsTab = SettingsTab.CAMERA } }
                TabIcon(Icons.Default.Settings, "一般", state.settingsTab == SettingsTab.SYSTEM) { onUpdateState { settingsTab = SettingsTab.SYSTEM } }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        text = when(state.settingsTab) { 
                            SettingsTab.ENVIRONMENT -> "🌍 氣候與環境模擬設定"
                            SettingsTab.CAMERA -> "🔭 視覺與空域輔助設定"
                            SettingsTab.DRONE_SELECTION -> "🚁 無人機機型選擇"
                            SettingsTab.CONTROLLER -> "🎮 遙控器模式與映射設定"
                            SettingsTab.SYSTEM -> "⚙️ 一般系統設定"
                        }, 
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 20.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = 10.dp, y = (-10).dp)) {
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
                val scrollState = rememberScrollState()
                val canScrollForward = scrollState.value < scrollState.maxValue
                val canScrollBackward = scrollState.value > 0

                // [v1.4.2] 每當切換分頁時，重置捲動位置
                LaunchedEffect(state.settingsTab) {
                    scrollState.scrollTo(0)
                }

                Box(modifier = Modifier.weight(1f)) {
                    // 全域捲動容器：將所有分頁內容包裝在一起
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 12.dp)
                    ) {
                        when(state.settingsTab) {
                            SettingsTab.ENVIRONMENT -> ClimateSettingsScreen(
                                windLevel = state.windLevel,
                                windDirection = state.windDirection,
                                windVariation = state.windVariation,
                                windDirVariation = state.windDirVariation,
                                timeOfDay = state.timeOfDay,
                                shadowIntensity = state.shadowIntensity,
                                enableVerticalDraft = state.enableVerticalDraft,
                                useHardcorePhysics = state.useHardcorePhysics,
                                isSunSimEnabled = state.isSunSimEnabled,
                                sunPosition = state.sunPosition,
                                onUpdateWindLevel = { l -> onUpdateState { windLevel = l } },
                                onUpdateWindDirection = { d -> onUpdateState { windDirection = d } },
                                onUpdateWindVariation = { v -> onUpdateState { windVariation = v } },
                                onUpdateWindDirVariation = { dv -> onUpdateState { windDirVariation = dv } },
                                onUpdateTimeOfDay = { t -> onUpdateState { timeOfDay = t } },
                                onUpdateShadowIntensity = { si -> onUpdateState { shadowIntensity = si } },
                                onToggleVerticalDraft = { e -> onUpdateState { enableVerticalDraft = e } },
                                onToggleHardcorePhysics = { h -> onUpdateState { useHardcorePhysics = h } },
                                onToggleSunSim = { s -> onUpdateState { isSunSimEnabled = s } },
                                onUpdateSunPosition = { p -> onUpdateState { sunPosition = p } },
                                onSave = onSaveSettings,
                                onTargetPositioned = onTargetPositioned
                            )
                            SettingsTab.CAMERA -> Column(modifier = Modifier.fillMaxWidth().padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // 1. 視角基礎設定
                                Text("視野參數 (Perspective)", color = Color.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Column(modifier = Modifier.background(Color.White.copy(0.05f), RoundedCornerShape(8.dp)).padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("廣角視距 (FOV)", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                                        Slider(state.mainFOV, { onUpdateState { mainFOV = it } }, valueRange = 30f..70f, modifier = Modifier.weight(1f))
                                        Text(String.format(Locale.US, "%.0f°", state.mainFOV), color = Color.Cyan, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                                    }
                                    Text("調低此值可獲得更真實的高度感，適合小型裝置。", color = Color.Gray, fontSize = 11.sp)
                                }

                                // 2. 智慧輔助與儀表
                                Text("導航與智慧輔助 (Intelligence)", color = Color.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                FlowRow(maxItemsInEachRow = 2, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val itemModifier = Modifier.widthIn(min = 160.dp).background(Color.White.copy(0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
                                    Row(itemModifier, verticalAlignment = Alignment.CenterVertically) {
                                        Text("智慧觀察員模式", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Switch(state.useSmartObserver, { onUpdateState { useSmartObserver = it } }, modifier = Modifier.scale(0.7f))
                                    }
                                    Row(itemModifier, verticalAlignment = Alignment.CenterVertically) {
                                        Text("側邊導航刻度尺", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Switch(state.showSideRulers, { onUpdateState { showSideRulers = it } }, modifier = Modifier.scale(0.7f))
                                    }
                                    Row(itemModifier, verticalAlignment = Alignment.CenterVertically) {
                                        Text("地面位置投影 (AR)", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Switch(state.showGroundAnchor, { onUpdateState { showGroundAnchor = it } }, modifier = Modifier.scale(0.7f))
                                    }
                                    Row(itemModifier, verticalAlignment = Alignment.CenterVertically) {
                                        Text("視窗自動避讓", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Switch(state.autoPiPRelocate, { onUpdateState { autoPiPRelocate = it } }, modifier = Modifier.scale(0.7f))
                                    }
                                }

                                // 3. 視圖元件
                                Text("其它元件", color = Color.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.05f), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("姿態輔助(ZOOM)", color = Color.White, fontSize = 13.sp); Spacer(Modifier.width(8.dp))
                                        Switch(state.enableZoomAssistant, { onUpdateState { enableZoomAssistant = it } }, modifier = Modifier.scale(0.7f))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("顯示飛行軌跡", color = Color.White, fontSize = 13.sp); Spacer(Modifier.width(8.dp))
                                        Switch(state.showFlightPath, { onUpdateState { showFlightPath = it } }, modifier = Modifier.scale(0.7f))
                                    }
                                }
                            }
                            SettingsTab.DRONE_SELECTION -> DroneSelectionScreen(state.droneType) { t -> onUpdateState { droneType = t } }
                            SettingsTab.CONTROLLER -> JoystickMappingScreen(
                                mappingLY = state.mappingLY, mappingLX = state.mappingLX, mappingRY = state.mappingRY, mappingRX = state.mappingRX,
                                isAutoBinding = state.isAutoBinding, halfThrottle = state.halfThrottle, joystickDeadzone = state.joystickDeadzone, activeAxis = state.activeAxisLabel, joystickMode = state.joystickMode, 
                                stickLX = if(state.setupWizardStep > 0) stickState.rawLX else stickState.stickLX(state, state.isCalibrating), 
                                stickLY = if(state.setupWizardStep > 0) stickState.rawLY else stickState.stickLY(state, state.isCalibrating), 
                                stickRX = if(state.setupWizardStep > 0) stickState.rawRX else stickState.stickRX(state, state.isCalibrating), 
                                stickRY = if(state.setupWizardStep > 0) stickState.rawRY else stickState.stickRY(state, state.isCalibrating),
                                activeHidName = state.activeHidName,
                                useGlobalRates = state.useGlobalRates, globalRate = state.globalRate, globalExpo = state.globalExpo, rateLY = state.rateLY, expoLY = state.expoLY, rateLX = state.rateLX, expoLX = state.expoLX, rateRY = state.rateRY, expoRY = state.expoRY, rateRX = state.rateRX, expoRX = state.expoRX, showIndividualRates = state.showIndividualRates,
                                onStartCalibration = { onUpdateState { isCalibrating = true; calibrationStep = 1 } }, 
                                onStartWizard = { onUpdateState { setupWizardStep = 1; wizardWaitingForNeutral = false } },
                                onToggleHalfThrottle = { b -> onUpdateState { halfThrottle = b } }, onUpdateDeadzone = { f -> onUpdateState { joystickDeadzone = f } }, onStartBinding = { k -> onUpdateState { isAutoBinding = if (isAutoBinding == k) null else k.ifEmpty { null } } },
                                onToggleInvert = { k -> onUpdateState { when(k) { "ly" -> mappingLY = mappingLY.copy(inverted = !mappingLY.inverted); "lx" -> mappingLX = mappingLX.copy(inverted = !mappingLX.inverted); "ry" -> mappingRY = mappingRY.copy(inverted = !mappingRY.inverted); "rx" -> mappingRX = mappingRX.copy(inverted = !mappingRX.inverted) } } },
                                onManualBind = { k, a -> onUpdateState { val l = if (a >= 101) "Serial CH${a - 100}" else "Axis $a"; val m = com.horizon.caadronesimulator.model.ChannelMapping(a, false, l); when(k) { "ly" -> mappingLY = m; "lx" -> mappingLX = m; "ry" -> mappingRY = m; "rx" -> mappingRX = m } } },
                                onModeChange = { m -> onUpdateState { joystickMode = m } },
                                onToggleGlobalRates = { b -> onUpdateState { useGlobalRates = b } }, onUpdateGlobalRate = { r -> onUpdateState { globalRate = r } }, onUpdateGlobalExpo = { e -> onUpdateState { globalExpo = e } },
                                onUpdateIndividualRate = { k, r -> onUpdateState { when(k) { "ly" -> rateLY = r; "lx" -> rateLX = r; "ry" -> rateRY = r; "rx" -> rateRX = r } } }, onUpdateIndividualExpo = { k, e -> onUpdateState { when(k) { "ly" -> expoLY = e; "lx" -> expoLX = e; "ry" -> expoRY = e; "rx" -> expoRX = e } } },
                                onToggleShowIndividual = { b -> onUpdateState { showIndividualRates = b } }, onResetRates = { onUpdateState { globalRate = 1f; globalExpo = 0f; rateLY = 1f; expoLY = 0f; rateLX = 1f; expoLX = 0f; rateRY = 1f; expoRY = 0f; rateRX = 1f; expoRX = 0f; joystickDeadzone = 0.15f } },
                                inputMode = state.inputMode, 
                                isInteractionLocked = state.isInteractionLocked,
                                isMappingUnlocked = state.isMappingUnlocked, shouldShowExpertUI = state.shouldShowExpertUI,
                                serialByteCount = stickState.serialByteCount.toInt(), localSettingsMessage = state.localSettingsMessage,
                                onUpdateInputMode = onUpdateInputMode, onScanUsb = onScanUsb, onOpenNetworkSettings = onOpenNetworkSettings, onUpdateBaudRate = onUpdateBaudRate, onExportLog = onExportLog,
                                onToggleMappingUnlock = { b -> onUpdateState { isMappingUnlocked = b } },
                                showHardwareMonitor = state.showHardwareMonitor, rawChannels = stickState.rawChannels, onToggleHardwareMonitor = { b -> onUpdateState { showHardwareMonitor = b } }, diagnosticLog = state.diagnosticLog, activeSerialPath = state.activeSerialPath, rawHexData = state.rawHexData, linkType = state.linkType, baudRate = state.baudRate,
                                connectionStatus = state.connectionStatus,
                                packetsPerSecond = stickState.packetsPerSecond, detectedProtocol = state.detectedProtocol, isSerialConflict = state.isSerialConflict, conflictPid = state.conflictPid, rawBytesCount = state.rawBytesCount, bufferUsage = state.bufferUsage, isSignalActive = stickState.isSignalActive, lockedProtocol = state.lockedProtocol, onUpdateLockedProtocol = { p -> onUpdateState { lockedProtocol = p } },
                                isLogcatEnabled = state.isLogcatEnabled, logcatContent = state.logcatContent,
                                onToggleLogcat = { b -> onUpdateState { isLogcatEnabled = b } },
                                onClearLogcat = { onUpdateState { logcatContent = "" } },
                                isHardwareController = state.isHardwareController,
                                onTargetPositioned = onTargetPositioned
                            )
                            SettingsTab.SYSTEM -> Column(modifier = Modifier.fillMaxWidth().padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val metrics = LocalContext.current.resources.displayMetrics
                                val resText = "${metrics.widthPixels} x ${metrics.heightPixels} (${metrics.densityDpi} DPI)"
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("裝置解析度", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(resText, color = Color.Cyan.copy(0.7f), fontSize = 12.sp) }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("顯示系統狀態欄", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(state.showStatusBar, { b -> onUpdateState { showStatusBar = b } }, modifier = Modifier.scale(0.7f)) }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("15 分鐘飛行限時 (電池模擬)", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(state.useFlightLimit, { b -> onUpdateState { useFlightLimit = b } }, modifier = Modifier.scale(0.7f)) }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("簡約標線模式 (白色/無十字線)", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(state.useSimplifiedMarkers, { b -> onUpdateState { useSimplifiedMarkers = b } }, modifier = Modifier.scale(0.7f)) }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) { Text("顯示單位標題 (H點下方)", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(state.showSpecialTitle, { b -> onUpdateState { showSpecialTitle = b } }, modifier = Modifier.scale(0.7f)) }
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

                    // [v1.4.2 修正] 將搖桿校準與引導圖層提升至全域層級，防止被捲動
                    if (state.settingsTab == SettingsTab.CONTROLLER) {
                        JoystickCalibrationOverlay(
                            isCalibrating = state.isCalibrating,
                            calibrationStep = state.calibrationStep,
                            joystickMode = state.joystickMode,
                            stickLX = if(state.setupWizardStep > 0) stickState.rawLX else stickState.stickLX(state, state.isCalibrating), 
                            stickLY = if(state.setupWizardStep > 0) stickState.rawLY else stickState.stickLY(state, state.isCalibrating), 
                            stickRX = if(state.setupWizardStep > 0) stickState.rawRX else stickState.stickRX(state, state.isCalibrating), 
                            stickRY = if(state.setupWizardStep > 0) stickState.rawRY else stickState.stickRY(state, state.isCalibrating),
                            onNextStep = { onUpdateState { calibrationStep += 1 } },
                            onFinish = { onUpdateState { isCalibrating = false; calibrationStep = 0 } }
                        )

                        JoystickWizardOverlay(
                            setupWizardStep = state.setupWizardStep,
                            isWizardWaiting = state.wizardWaitingForNeutral,
                            wizardCountdown = state.wizardCountdown,
                            stickLX = if(state.setupWizardStep > 0) stickState.rawLX else stickState.stickLX(state, state.isCalibrating), 
                            stickLY = if(state.setupWizardStep > 0) stickState.rawLY else stickState.stickLY(state, state.isCalibrating), 
                            stickRX = if(state.setupWizardStep > 0) stickState.rawRX else stickState.stickRX(state, state.isCalibrating), 
                            stickRY = if(state.setupWizardStep > 0) stickState.rawRY else stickState.stickRY(state, state.isCalibrating),
                            onCancelWizard = { onUpdateState { setupWizardStep = 0; isAutoBinding = null; wizardWaitingForNeutral = false } }
                        )
                    }

                    // [v1.4.2 修正] 全域捲動提示系統 (動態漸層導引)
                    if (scrollState.maxValue > 0) {
                        if (canScrollForward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("向下捲動查看更多選項 ", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else if (canScrollBackward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)))
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                                    Text(" 向上捲動 ", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
