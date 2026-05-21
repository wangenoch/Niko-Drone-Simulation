package com.horizon.caadronesimulator.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.ChannelMapping
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.SettingsTab
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.ui.overlays.ModelConfigConfirmDialog

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.5.9] 全球整合設定屏 - 底部空間最大化優化版
 */
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
    onExportLog: () -> Unit = {},
    onUpdateInputMode: (Int) -> Unit = {},
    onToggleNetworkConnection: (Boolean) -> Unit = {},
    onSaveSettings: () -> Unit = {},
    onRerollWind: () -> Unit = {},
    onSaveModelSettings: (String) -> Unit = {},
    onLoadModelSettings: (String) -> Unit = {},
    onUpdateLockedPath: (String) -> Unit = {},
    onOpenNetworkSettings: () -> Unit = {},
    onRestoreDefaults: () -> Unit = {},
    onLanguageChange: (String) -> Unit = {},
    availablePorts: List<String> = emptyList(),
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    var showExpertUnlockDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(state.settingsTab) { scrollState.scrollTo(0) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable(enabled = false) {}) {
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxHeight(0.88f).fillMaxWidth(0.92f),
            color = Color(0xFF111111),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(0.1f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 1. 左側導航欄
                Column(
                    modifier = Modifier.width(80.dp).fillMaxHeight().background(Color(0xFF181818)).padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TabIcon(Icons.Default.VideogameAsset, stringResource(R.string.joystick_tab_title), state.settingsTab == SettingsTab.CONTROLLER) { onUpdateState { settingsTab = SettingsTab.CONTROLLER } }
                    TabIcon(Icons.Default.Cloud, stringResource(R.string.hud_environment), state.settingsTab == SettingsTab.ENVIRONMENT) { onUpdateState { settingsTab = SettingsTab.ENVIRONMENT } }
                    TabIcon(Icons.Default.AirplanemodeActive, stringResource(R.string.settings_tab_drone), state.settingsTab == SettingsTab.DRONE_SELECTION) { onUpdateState { settingsTab = SettingsTab.DRONE_SELECTION } }
                    TabIcon(Icons.Default.Visibility, stringResource(R.string.menu_camera_mode), state.settingsTab == SettingsTab.CAMERA) { onUpdateState { settingsTab = SettingsTab.CAMERA } }
                    TabIcon(Icons.Default.Settings, stringResource(R.string.action_general), state.settingsTab == SettingsTab.SYSTEM) { onUpdateState { settingsTab = SettingsTab.SYSTEM } }
                    Spacer(Modifier.weight(1f))
                }

                // 2. 主內容區
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when(state.settingsTab) {
                                SettingsTab.CONTROLLER -> stringResource(R.string.joystick_settings_title)
                                SettingsTab.ENVIRONMENT -> stringResource(R.string.settings_title_env)
                                SettingsTab.DRONE_SELECTION -> stringResource(R.string.settings_title_drone)
                                SettingsTab.CAMERA -> stringResource(R.string.settings_title_camera)
                                SettingsTab.SYSTEM -> stringResource(R.string.settings_title_system)
                            },
                            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.settingsTab == SettingsTab.CONTROLLER) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HeaderMiniJoystickWrapper(stringResource(R.string.joystick_rate_up).first().toString(), stickState.rawLX, stickState.rawLY)
                                HeaderMiniJoystickWrapper(stringResource(R.string.joystick_rate_down).first().toString(), stickState.rawRX, stickState.rawRY)
                            }
                        } else if (state.settingsTab == SettingsTab.DRONE_SELECTION) {
                            // [v1.6.1] 恢復套用真實物理特性開關，置於關閉按鈕左側
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                                Text(stringResource(R.string.settings_apply_physics), color = Color.White.copy(0.7f), fontSize = 11.sp)
                                Spacer(Modifier.width(4.dp))
                                Switch(
                                    checked = state.applyPhysicalSpecs,
                                    onCheckedChange = { onUpdateState { applyPhysicalSpecs = it } },
                                    modifier = Modifier.scale(0.6f),
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).background(Color.White.copy(0.1f), CircleShape)) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // 滾動內容層
                    Box(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                            when(state.settingsTab) {
                                SettingsTab.ENVIRONMENT -> ClimateSettingsScreen(
                                    windLevel = state.windLevel, windDirection = state.windDirection, windVariation = state.windVariation, windDirVariation = state.windDirVariation, timeOfDay = state.timeOfDay, shadowIntensity = state.shadowIntensity, enableVerticalDraft = state.enableVerticalDraft, useHardcorePhysics = state.useHardcorePhysics, isSunSimEnabled = state.isSunSimEnabled, sunPosition = state.sunPosition, showClouds = state.showClouds, cloudDensity = state.cloudDensity, weatherMode = state.weatherMode,
                                    onUpdateWindLevel = { onUpdateState { windLevel = it } }, onUpdateWindDirection = { onUpdateState { windDirection = it } }, onUpdateWindVariation = { onUpdateState { windVariation = it } }, onUpdateWindDirVariation = { onUpdateState { windDirVariation = it } }, onUpdateTimeOfDay = { onUpdateState { timeOfDay = it } }, onUpdateShadowIntensity = { onUpdateState { shadowIntensity = it } }, onToggleVerticalDraft = { onUpdateState { enableVerticalDraft = it } }, onToggleHardcorePhysics = { onUpdateState { useHardcorePhysics = it } }, onToggleSunSim = { onUpdateState { isSunSimEnabled = it } }, onUpdateSunPosition = { onUpdateState { sunPosition = it } }, onToggleClouds = { onUpdateState { showClouds = it } }, onUpdateCloudDensity = { onUpdateState { cloudDensity = it } }, onUpdateWeatherMode = { onUpdateState { weatherMode = it } },
                                    showMountains = state.showMountains, onToggleMountains = { onUpdateState { showMountains = it } }, onSave = onSaveSettings, onTargetPositioned = onTargetPositioned,
                                    onRerollWind = onRerollWind
                                )
                                SettingsTab.CAMERA -> VisualNavigationScreen(
                                    cameraMode = state.cameraMode, mainFOV = state.mainFOV, zoomFactor = state.zoomFactor, showSpecialTitle = state.showSpecialTitle, currentTitleText = state.currentTitleText, showSideSliders = state.showSideSliders, showSideRulers = state.showSideRulers, reverseSliderSides = state.reverseSliderSides, showGroundAnchor = state.showGroundAnchor, autoPiPRelocate = state.autoPiPRelocate, enableZoomAssistant = state.enableZoomAssistant,
                                    onUpdateCameraMode = { onUpdateState { cameraMode = it } }, onUpdateFOV = { onUpdateState { mainFOV = it } }, onUpdateZoom = { onUpdateState { zoomFactor = it } }, onToggleSpecialTitle = { onUpdateState { showSpecialTitle = it } }, onUpdateSpecialTitle = { onUpdateState { currentTitleText = it } }, onToggleSideSliders = { onUpdateState { showSideSliders = it } }, onToggleSideRulers = { onUpdateState { showSideRulers = it } }, onToggleReverseSliders = { onUpdateState { reverseSliderSides = it } }, onToggleGroundAnchor = { onUpdateState { showGroundAnchor = it } }, onTogglePiPRelocate = { onUpdateState { autoPiPRelocate = it } }, onToggleZoomAssistant = { onUpdateState { enableZoomAssistant = it } }, onSave = onSaveSettings
                                )
                                SettingsTab.DRONE_SELECTION -> DroneSelectionScreen(currentType = state.droneType, onTypeSelected = { t -> onUpdateState { droneType = t }; onLoadModelSettings(t) }, onLongPressType = { t -> onUpdateState { showModelConfigConfirm = t } }, state = state)
                                SettingsTab.CONTROLLER -> JoystickMappingScreen(
                                    mappingLY = state.mappingLY, mappingLX = state.mappingLX, mappingRY = state.mappingRY, mappingRX = state.mappingRX, isAutoBinding = state.isAutoBinding, halfThrottle = state.halfThrottle, joystickDeadzone = state.joystickDeadzone, activeAxis = state.activeAxisLabel, joystickMode = state.joystickMode, stickLX = stickState.rawLX, stickLY = stickState.rawLY, stickRX = stickState.rawRX, stickRY = stickState.rawRY, activeHidName = state.activeHidName, useGlobalRates = state.useGlobalRates, globalRate = state.globalRate, globalExpo = state.globalExpo, rateLY = state.rateT, expoLY = state.expoT, rateLX = state.rateY, expoLX = state.expoY, rateRY = state.rateP, expoRY = state.expoP, rateRX = state.rateR, expoRX = state.expoR, showIndividualRates = state.showIndividualRates, 
                                    onStartCalibration = { onUpdateState { isCalibrating = true; calibrationStep = 1 } }, onStartWizard = { onUpdateState { setupWizardStep = 1; wizardWaitingForNeutral = false } }, onToggleHalfThrottle = { b -> onUpdateState { halfThrottle = b } }, onUpdateDeadzone = { f -> onUpdateState { joystickDeadzone = f } }, onStartBinding = { k -> onUpdateState { isAutoBinding = if (isAutoBinding == k) null else k.ifEmpty { null } } }, onToggleInvert = { k -> onUpdateState { when(k) { "ly" -> mappingLY = mappingLY.copy(inverted = !mappingLY.inverted); "lx" -> mappingLX = mappingLX.copy(inverted = !mappingLX.inverted); "ry" -> mappingRY = mappingRY.copy(inverted = !mappingRY.inverted); "rx" -> mappingRX = mappingRX.copy(inverted = !mappingRX.inverted) } } }, onManualBind = { k, a -> onUpdateState { val l = if (a >= 101) "Serial CH${a - 100}" else "Axis $a"; val m = ChannelMapping(a, false, l); when(k) { "ly" -> mappingLY = m; "lx" -> mappingLX = m; "ry" -> mappingRY = m; "rx" -> mappingRX = m } } }, onModeChange = { m -> onUpdateState { joystickMode = m } }, onToggleGlobalRates = { b -> onUpdateState { useGlobalRates = b } }, onUpdateGlobalRate = { r -> onUpdateState { globalRate = r } }, onUpdateGlobalExpo = { e -> onUpdateState { globalExpo = e } }, onUpdateIndividualRate = { k, r -> onUpdateState { when(k) { "T" -> rateT = r; "Y" -> rateY = r; "P" -> rateP = r; "R" -> rateR = r } } }, onUpdateIndividualExpo = { k, e -> onUpdateState { when(k) { "T" -> expoT = e; "Y" -> expoY = e; "P" -> expoP = e; "R" -> expoR = e } } }, onToggleShowIndividual = { b -> onUpdateState { showIndividualRates = b } }, onResetRates = { onUpdateState { globalRate = AppConfig.JoystickDefaults.RATE; globalExpo = AppConfig.JoystickDefaults.EXPO; rateT = AppConfig.JoystickDefaults.RATE; expoT = AppConfig.JoystickDefaults.EXPO; rateY = AppConfig.JoystickDefaults.RATE; expoY = AppConfig.JoystickDefaults.EXPO; rateP = AppConfig.JoystickDefaults.RATE; expoP = AppConfig.JoystickDefaults.EXPO; rateR = AppConfig.JoystickDefaults.RATE; expoR = AppConfig.JoystickDefaults.EXPO; joystickDeadzone = AppConfig.JoystickDefaults.DEADZONE } }, inputMode = state.inputMode, rawChannels = stickState.rawChannels, onToggleMappingUnlock = { b -> onUpdateState { isMappingUnlocked = b } }, activeSerialPath = state.activeSerialPath, rawHexData = state.rawHexData, linkType = state.linkType, baudRate = state.baudRate, connectionStatus = state.connectionStatus, packetsPerSecond = stickState.packetsPerSecond, detectedProtocol = state.detectedProtocol, isSerialConflict = state.isSerialConflict, conflictPid = state.conflictPid, rawBytesCount = state.rawBytesCount, bufferUsage = state.bufferUsage, isSignalActive = stickState.isSignalActive, lockedProtocol = state.lockedProtocol, onUpdateLockedProtocol = { p -> onUpdateState { lockedProtocol = p } }, isLogcatEnabled = state.isLogcatEnabled, logcatContent = state.logcatContent, onToggleLogcat = { b -> onUpdateState { isLogcatEnabled = b } }, onClearLogcat = { onUpdateState { logcatContent = "" } }, isHardwareController = state.isHardwareController, onOpenAuxMapping = { onUpdateState { showAuxMappingOverlay = true } }, onUpdateInputMode = onUpdateInputMode, onScanUsb = onScanUsb, onUpdateBaudRate = onUpdateBaudRate, onUpdateLockedPath = onUpdateLockedPath, onOpenNetworkSettings = onOpenNetworkSettings, availablePorts = availablePorts, onExportLog = onExportLog, onToggleNetworkConnection = onToggleNetworkConnection, showHardwareMonitor = state.showHardwareMonitor, onToggleHardwareMonitor = { b -> onUpdateState { showHardwareMonitor = b } }, state = state, jitter = state.jitter, stability = state.stability, onTargetPositioned = onTargetPositioned, isMappingUnlocked = state.isMappingUnlocked, isInteractionLocked = state.isInteractionLocked, localSettingsMessage = state.localSettingsMessage, diagnosticLog = state.diagnosticLog
                                )
                                SettingsTab.SYSTEM -> Column(modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
                                    SystemSettingRow(stringResource(R.string.settings_auto_connect), state.isAutoConnectEnabled, { onUpdateState { isAutoConnectEnabled = it } }, Color.Cyan)
                                    HorizontalDivider(color = Color.White.copy(0.05f))
                                    SystemSettingRow(stringResource(R.string.settings_hide_status_bar), state.hideStatusBar, { onUpdateState { hideStatusBar = it } })
                                    HorizontalDivider(color = Color.White.copy(0.05f))
                                    SystemSettingRow(stringResource(R.string.settings_pause_in_settings), state.pauseInSettings, { onUpdateState { pauseInSettings = it } })
                                    HorizontalDivider(color = Color.White.copy(0.05f))
                                    
                                    // [v1.7.6] 語言切換選單
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(stringResource(R.string.settings_language), color = Color.White, fontSize = 14.sp)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CompactChip(
                                                text = stringResource(R.string.settings_lang_zh),
                                                selected = state.appLanguage == "zh",
                                                onClick = { onLanguageChange("zh") },
                                                modifier = Modifier.width(80.dp)
                                            )
                                            CompactChip(
                                                text = stringResource(R.string.settings_lang_en),
                                                selected = state.appLanguage == "en",
                                                onClick = { onLanguageChange("en") },
                                                modifier = Modifier.width(80.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(0.05f))

                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { 
                                        Column(modifier = Modifier.weight(1f)) { 
                                            Text(stringResource(R.string.settings_strict_landing), color = Color.White, fontSize = 14.sp)
                                            Text(stringResource(R.string.settings_strict_landing_desc), color = Color.Gray, fontSize = 10.sp)
                                        } 
                                        Switch(state.useStrictLanding, { b -> onUpdateState { useStrictLanding = b } }, modifier = Modifier.scale(0.7f), colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow)) 
                                    }
                                    
                                    HorizontalDivider(color = Color.White.copy(0.05f))
                                    Spacer(Modifier.height(16.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { onUpdateState { showTutorial = true; showSettings = false } },
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2).copy(0.8f)),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Icon(Icons.Default.Help, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.settings_btn_tutorial), fontSize = 11.sp)
                                            }
                                            
                                            Button(
                                                onClick = onRestoreDefaults,
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(0.8f)),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.action_restore_defaults), fontSize = 11.sp)
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            var clickCount by remember { mutableIntStateOf(0) }
                                            val expertActiveMsg = stringResource(R.string.settings_expert_active)
                                            Text(
                                                text = "${stringResource(R.string.settings_dev_label)}${com.horizon.caadronesimulator.model.AppConfig.DEVELOPER}", 
                                                color = Color.Gray, fontSize = 11.sp, 
                                                modifier = Modifier.clickable { 
                                                    clickCount++; 
                                                    if (clickCount >= 7) { 
                                                        if (state.isExpertModeLocked) showExpertUnlockDialog = true 
                                                        else state.systemMessage = expertActiveMsg
                                                        clickCount = 0 
                                                    } 
                                                }
                                            )
                                            Text(
                                                text = "${stringResource(R.string.settings_version_label)}${AppConfig.CURRENT_VERSION} (${AppConfig.RELEASE_DATE})", 
                                                color = Color.Cyan.copy(alpha = 0.7f), fontSize = 10.sp, 
                                                modifier = Modifier.clickable { onUpdateState { showUpdateNotice = true; showSettings = false } }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }

                        if (scrollState.canScrollForward) {
                            val infiniteTransition = rememberInfiniteTransition(label = "")
                            val arrowOffset by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 10f, animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "")
                            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp + arrowOffset.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.Cyan.copy(alpha = 0.8f), modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }

        state.showModelConfigConfirm?.let { droneId -> ModelConfigConfirmDialog(droneName = com.horizon.caadronesimulator.model.DroneRegistry.getSpec(droneId).name, onConfirm = { onUpdateState { showModelConfigConfirm = null; showModelMappingOverlay = droneId } }, onDismiss = { onUpdateState { showModelConfigConfirm = null } }) }
        if (state.showIndividualRates) { com.horizon.caadronesimulator.ui.settings.HandfeelTuningOverlay(title = stringResource(R.string.joystick_section_rates), rateT = state.rateT, expoT = state.expoT, rateY = state.rateY, expoY = state.expoY, rateP = state.rateP, expoP = state.expoP, rateR = state.rateR, expoR = state.expoR, onUpdateRate = { k, v -> onUpdateState { when(k){ "T" -> rateT = v; "Y" -> rateY = v; "P" -> rateP = v; "R" -> rateR = v } } }, onUpdateExpo = { k, v -> onUpdateState { when(k){ "T" -> expoT = v; "Y" -> expoY = v; "P" -> expoP = v; "R" -> expoR = v } } }, onResetAll = { onUpdateState { globalRate = 1.2f; globalExpo = 0.4f; rateT = 1.2f; expoT = 0.4f; rateY = 1.2f; expoY = 0.4f; rateP = 1.2f; expoP = 0.4f; rateR = 1.2f; expoR = 0.4f } }, onClose = { onUpdateState { showIndividualRates = false } }, joystickMode = state.joystickMode, isGeneMode = false) }
        state.showModelMappingOverlay?.let { droneId -> com.horizon.caadronesimulator.ui.settings.HandfeelTuningOverlay(title = "${stringResource(R.string.settings_tab_drone)}: ${com.horizon.caadronesimulator.model.DroneRegistry.getSpec(droneId).name}", rateT_Up = state.modelGene.rateT_Up, rateT_Down = state.modelGene.rateT_Down, expoT = state.modelGene.expoT, rateY = state.modelGene.rateY, expoY = state.modelGene.expoY, rateP = state.modelGene.rateP, expoP = state.modelGene.expoP, rateR = state.modelGene.rateR, expoR = state.modelGene.expoR, onUpdateRate = { k, v -> onUpdateState { when(k){ "T_Up" -> modelGene.rateT_Up = v; "T_Down" -> modelGene.rateT_Down = v; "Y" -> modelGene.rateY = v; "P" -> modelGene.rateP = v; "R" -> modelGene.rateR = v } } }, onUpdateExpo = { k, v -> onUpdateState { when(k){ "T" -> modelGene.expoT = v; "Y" -> modelGene.expoY = v; "P" -> modelGene.expoP = v; "R" -> modelGene.expoR = v } } }, onResetAll = { onUpdateState { val module = com.horizon.caadronesimulator.model.DroneRegistry.getModule(droneId); modelGene.apply { rateT_Up = module.baseRateT_Up; rateT_Down = module.baseRateT_Down; expoT = module.baseExpoT; rateY = module.baseRateY; expoY = module.baseExpoY; rateP = module.baseRateP; expoP = module.baseExpoP; rateR = module.baseRateR; expoR = module.baseExpoR } } }, onClose = { onUpdateState { showModelMappingOverlay = null } }, joystickMode = state.joystickMode, isGeneMode = true) }
        if (state.showAuxMappingOverlay) { AuxMappingOverlay(state = state, onStartBinding = { k -> onUpdateState { isAutoBinding = if (isAutoBinding == k) null else k.ifEmpty { null } } }, onManualBind = { k, a -> onUpdateState { val l = if (a >= 101) "Serial CH${a - 100}" else "Axis $a"; val m = ChannelMapping(a, false, l); when(k) { "ly" -> mappingLY = m; "lx" -> mappingLX = m; "ry" -> mappingRY = m; "rx" -> mappingRX = m; "hold" -> mappingHold = m; "arm" -> mappingArm = m; "obsHeight" -> mappingObsHeight = m; "obsTilt" -> mappingObsTilt = m; "fpvTilt" -> mappingFpvTilt = m } } }, onToggleInvert = { k -> onUpdateState { when(k) { "ly" -> mappingLY = mappingLY.copy(inverted = !mappingLY.inverted); "lx" -> mappingLX = mappingLX.copy(inverted = !mappingLX.inverted); "ry" -> mappingRY = mappingRY.copy(inverted = !mappingRY.inverted); "rx" -> mappingRX = mappingRX.copy(inverted = !mappingRX.inverted); "hold" -> mappingHold = mappingHold.copy(inverted = !mappingHold.inverted); "arm" -> mappingArm = mappingArm.copy(inverted = !mappingArm.inverted); "obsHeight" -> mappingObsHeight = mappingObsHeight.copy(inverted = !mappingObsHeight.inverted); "obsTilt" -> mappingObsTilt = mappingObsTilt.copy(inverted = !mappingObsTilt.inverted); "fpvTilt" -> mappingFpvTilt = mappingFpvTilt.copy(inverted = !mappingFpvTilt.inverted) } } }, onDismiss = { onUpdateState { showAuxMappingOverlay = false; isAutoBinding = null } }) }
        if (showExpertUnlockDialog) { 
            ExpertUnlockDialog(onDismiss = { showExpertUnlockDialog = false }, onUnlock = { onUpdateState { isExpertModeLocked = false } }) 
        }
    }
}

@Composable
fun ExpertUnlockDialog(onDismiss: () -> Unit, onUnlock: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text(stringResource(R.string.settings_expert_unlock_title), color = Color.White) }, 
        text = { 
            Column { 
                Text(stringResource(R.string.settings_expert_unlock_desc), color = Color.Gray, fontSize = 13.sp)
                Text(stringResource(R.string.settings_expert_unlock_hint), color = Color.Cyan.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password, 
                    onValueChange = { password = it; error = false }, 
                    label = { Text(stringResource(R.string.settings_expert_unlock_label)) }, 
                    singleLine = true, 
                    isError = error, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                ) 
            } 
        }, 
        confirmButton = { Button(onClick = { if (password == com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.ADMIN_PASSWORD) { onUnlock(); onDismiss() } else { error = true } }) { Text(stringResource(R.string.settings_expert_unlock_btn)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = Color(0xFF222222)
    )
}

@Composable
fun SystemSettingRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, thumbColor: Color = Color.White) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(32.dp)) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.scale(0.7f), colors = SwitchDefaults.colors(checkedThumbColor = thumbColor))
    }
}

@Composable
fun TabIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Color.Cyan else Color.White.copy(0.4f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.width(60.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun HeaderMiniJoystickWrapper(label: String, x: Float, y: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 8.sp)
        Box(modifier = Modifier.size(24.dp).background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)).border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.offset(x = (x * 8).dp, y = (y * 8).dp).size(6.dp).background(Color.Cyan, CircleShape))
        }
    }
}
