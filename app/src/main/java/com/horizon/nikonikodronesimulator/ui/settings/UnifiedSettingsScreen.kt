package com.horizon.nikonikodronesimulator.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.nikonikodronesimulator.model.AppConfig
import com.horizon.nikonikodronesimulator.model.ChannelMapping
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.SettingsTab
import com.horizon.nikonikodronesimulator.model.StickInputState
import com.horizon.nikonikodronesimulator.ui.overlays.ModelConfigConfirmDialog

import androidx.compose.ui.res.stringResource

import com.horizon.nikonikodronesimulator.R
import com.horizon.nikonikodronesimulator.ui.theme.NikoTheme

/**
 * [v1.5.9] 全球整合設定屏 - 底部空間最大化優化版
 */
@Composable
fun UnifiedSettingsScreen(
    state: DroneState,
    stickState: StickInputState,
    viewModel: com.horizon.nikonikodronesimulator.logic.DroneViewModel, // [v1.7.7] 接入 ViewModel 以便同步導覽步進
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
    onThemeChange: (String) -> Unit = {},
    availablePorts: List<String> = emptyList(),
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    var showExpertUnlockDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(state.settingsTab) { 
        scrollState.scrollTo(0) 
        
        // [v1.7.7] 核心修復：第一次進入頁面自動開啟導覽的機制回歸
        if (state.settingsTab == SettingsTab.CONTROLLER && !state.hasShownJoystickTutorial) {
            onUpdateState { 
                showJoystickTutorial = true
                hasShownJoystickTutorial = true 
            }
        }
        if (state.settingsTab == SettingsTab.ENVIRONMENT && !state.hasShownClimateTutorial) {
            onUpdateState { 
                showClimateTutorial = true
                hasShownClimateTutorial = true 
            }
        }
    }

    // [v1.7.7] 導覽自動捲動連動：確保導覽指引的項目始終在可視範圍內
    LaunchedEffect(viewModel.joystickTutorialStep, viewModel.climateTutorialStep, state.showJoystickTutorial, state.showClimateTutorial) {
        if (state.showJoystickTutorial) {
            val targetY = when(viewModel.joystickTutorialStep) {
                in 0..3 -> 0
                in 4..6 -> 300
                in 7..8 -> 600
                else -> 0
            }
            scrollState.animateScrollTo(targetY)
        } else if (state.showClimateTutorial) {
            val targetY = when(viewModel.climateTutorialStep) {
                in 0..1 -> 0
                in 2..4 -> 500 // 進階物理與垂直氣流在此區間
                5 -> 0      // [v1.7.7] 視覺與氣氛：介紹此項時自動往上捲動回到頂部
                in 6..7 -> 900 // [v1.7.7] 雲層與山脈：下一個項目時再往下捲動
                else -> 0
            }
            scrollState.animateScrollTo(targetY)
        } else {
            // [v1.7.7] 導覽結束或關閉時，自動捲回頂部
            scrollState.animateScrollTo(0)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable(enabled = false) {}) {
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxHeight(0.88f).fillMaxWidth(0.92f),
            color = NikoTheme.colors.panel,
            shape = NikoTheme.shapes.medium,
            border = BorderStroke(1.dp, NikoTheme.colors.divider)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 1. 左側導航欄
                Column(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .background(if(NikoTheme.colors.isLight) NikoTheme.colors.background else Color(0xFF181818))
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 32.dp), // 增加底部留白
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TabIcon(Icons.Default.VideogameAsset, stringResource(R.string.joystick_tab_title), state.settingsTab == SettingsTab.CONTROLLER) { onUpdateState { settingsTab = SettingsTab.CONTROLLER } }
                    TabIcon(Icons.Default.Cloud, stringResource(R.string.hud_environment), state.settingsTab == SettingsTab.ENVIRONMENT) { onUpdateState { settingsTab = SettingsTab.ENVIRONMENT } }
                    TabIcon(Icons.Default.AirplanemodeActive, stringResource(R.string.settings_tab_drone), state.settingsTab == SettingsTab.DRONE_SELECTION) { onUpdateState { settingsTab = SettingsTab.DRONE_SELECTION } }
                    TabIcon(Icons.Default.Visibility, stringResource(R.string.visual_camera_section), state.settingsTab == SettingsTab.CAMERA) { onUpdateState { settingsTab = SettingsTab.CAMERA } }
                    TabIcon(Icons.Default.Palette, stringResource(R.string.settings_theme_title), state.settingsTab == SettingsTab.THEME) { onUpdateState { settingsTab = SettingsTab.THEME } }
                    TabIcon(Icons.Default.Settings, stringResource(R.string.action_general), state.settingsTab == SettingsTab.SYSTEM) { onUpdateState { settingsTab = SettingsTab.SYSTEM } }
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
                                SettingsTab.THEME -> stringResource(R.string.settings_theme_title)
                                SettingsTab.SYSTEM -> stringResource(R.string.settings_title_system)
                            },
                            color = NikoTheme.colors.textPrimary, 
                            style = NikoTheme.typography.h2
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.settingsTab == SettingsTab.CONTROLLER) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HeaderMiniJoystickWrapper(stringResource(R.string.joystick_rate_up).first().toString(), stickState.stickLX(state), stickState.stickLY(state))
                                HeaderMiniJoystickWrapper(stringResource(R.string.joystick_rate_down).first().toString(), stickState.stickRX(state), stickState.stickRY(state))
                            }
                        } else if (state.settingsTab == SettingsTab.DRONE_SELECTION) {
                            // [v1.6.1] 恢復套用真實物理特性開關，置於關閉按鈕左側
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                                Text(stringResource(R.string.settings_apply_physics), color = NikoTheme.colors.textSecondary, fontSize = 11.sp)
                                Spacer(Modifier.width(4.dp))
                                Switch(
                                    checked = state.applyPhysicalSpecs,
                                    onCheckedChange = { onUpdateState { applyPhysicalSpecs = it } },
                                    modifier = Modifier.scale(0.6f),
                                    colors = SwitchDefaults.colors(checkedThumbColor = NikoTheme.colors.primary)
                                )
                            }
                        }
                        
                        // [v1.7.7] 導覽手動開啟按鈕：僅在搖桿與環境分頁顯示，置於關閉按鈕左側
                        if (state.settingsTab == SettingsTab.CONTROLLER || state.settingsTab == SettingsTab.ENVIRONMENT) {
                            Spacer(Modifier.width(12.dp))
                            IconButton(
                                onClick = { 
                                    onUpdateState { 
                                        if (settingsTab == SettingsTab.CONTROLLER) showJoystickTutorial = true
                                        else showClimateTutorial = true
                                    } 
                                }, 
                                modifier = Modifier.size(36.dp).background(NikoTheme.colors.primary.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Help, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).background(NikoTheme.colors.textPrimary.copy(0.1f), CircleShape)) {
                            Icon(Icons.Default.Close, null, tint = NikoTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // 滾動內容層
                    Box(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                            when(state.settingsTab) {
                                SettingsTab.ENVIRONMENT -> {
                                    Column(modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
                                        ClimateSettingsScreen(
                                            windLevel = state.windLevel, windDirection = state.windDirection, windVariation = state.windVariation, windDirVariation = state.windDirVariation, timeOfDay = state.timeOfDay, shadowIntensity = state.shadowIntensity, enableVerticalDraft = state.enableVerticalDraft, useHardcorePhysics = state.useHardcorePhysics, isSunSimEnabled = state.isSunSimEnabled, sunPosition = state.sunPosition, showClouds = state.showClouds, cloudDensity = state.cloudDensity, weatherMode = state.weatherMode,
                                            onUpdateWindLevel = { onUpdateState { windLevel = it } }, onUpdateWindDirection = { onUpdateState { windDirection = it } }, onUpdateWindVariation = { onUpdateState { windVariation = it } }, onUpdateWindDirVariation = { onUpdateState { windDirVariation = it } }, onUpdateTimeOfDay = { onUpdateState { timeOfDay = it } }, onUpdateShadowIntensity = { onUpdateState { shadowIntensity = it } }, onToggleVerticalDraft = { onUpdateState { enableVerticalDraft = it } }, onToggleHardcorePhysics = { onUpdateState { useHardcorePhysics = it } }, onToggleSunSim = { onUpdateState { isSunSimEnabled = it } }, onUpdateSunPosition = { onUpdateState { sunPosition = it } }, onToggleClouds = { onUpdateState { showClouds = it } }, onUpdateCloudDensity = { onUpdateState { cloudDensity = it } }, onUpdateWeatherMode = { onUpdateState { weatherMode = it } },
                                            showMountains = state.showMountains, onToggleMountains = { onUpdateState { showMountains = it } }, onSave = onSaveSettings, onTargetPositioned = onTargetPositioned,
                                            onRerollWind = onRerollWind
                                        )
                                    }
                                }
                                SettingsTab.THEME -> {
                                    ThemeSelectionScreen(
                                        currentThemeId = state.appTheme,
                                        onThemeSelected = onThemeChange
                                    )
                                }
                                SettingsTab.CAMERA -> VisualNavigationScreen(
                                    cameraMode = state.cameraMode, mainFOV = state.mainFOV, zoomFactor = state.zoomFactor, showSpecialTitle = state.showSpecialTitle, currentTitleText = state.currentTitleText, showSideSliders = state.showSideSliders, showSideRulers = state.showSideRulers, reverseSliderSides = state.reverseSliderSides, showGroundAnchor = state.showGroundAnchor, autoPiPRelocate = state.autoPiPRelocate, enableZoomAssistant = state.enableZoomAssistant,
                                    onUpdateCameraMode = { onUpdateState { cameraMode = it } }, onUpdateFOV = { onUpdateState { mainFOV = it } }, onUpdateZoom = { onUpdateState { zoomFactor = it } }, onToggleSpecialTitle = { onUpdateState { showSpecialTitle = it } }, onUpdateSpecialTitle = { onUpdateState { currentTitleText = it } }, onToggleSideSliders = { onUpdateState { showSideSliders = it } }, onToggleSideRulers = { onUpdateState { showSideRulers = it } }, onToggleReverseSliders = { onUpdateState { reverseSliderSides = it } }, onToggleGroundAnchor = { onUpdateState { showGroundAnchor = it } }, onTogglePiPRelocate = { onUpdateState { autoPiPRelocate = it } }, onToggleZoomAssistant = { onUpdateState { enableZoomAssistant = it } }, onSave = onSaveSettings,
                                    onManualInteraction = { onUpdateState { lastManualTouchTime = System.currentTimeMillis() } }
                                )
                                SettingsTab.DRONE_SELECTION -> DroneSelectionScreen(currentType = state.droneType, onTypeSelected = { t -> onUpdateState { droneType = t }; onLoadModelSettings(t) }, onLongPressType = { t -> onUpdateState { showModelConfigConfirm = t } }, state = state)
                                SettingsTab.CONTROLLER -> JoystickMappingScreen(
                                    mappingLY = state.mappingLY, mappingLX = state.mappingLX, mappingRY = state.mappingRY, mappingRX = state.mappingRX, isAutoBinding = state.isAutoBinding, halfThrottle = state.halfThrottle, joystickDeadzone = state.joystickDeadzone, activeAxis = state.activeAxisLabel, joystickMode = state.joystickMode, 
                                    stickLX = stickState.stickLX(state), 
                                    stickLY = stickState.stickLY(state), 
                                    stickRX = stickState.stickRX(state), 
                                    stickRY = stickState.stickRY(state), 
                                    activeHidName = state.activeHidName, useGlobalRates = state.useGlobalRates, globalRate = state.globalRate, globalExpo = state.globalExpo, rateLY = state.rateT, expoLY = state.expoT, rateLX = state.rateY, expoLX = state.expoY, rateRY = state.rateP, expoRY = state.expoP, rateRX = state.rateR, expoRX = state.expoR, showIndividualRates = state.showIndividualRates,
                                    onStartCalibration = { onUpdateState { isCalibrating = true; calibrationStep = 1 } }, onStartWizard = { onUpdateState { setupWizardStep = 1; wizardWaitingForNeutral = false } }, onToggleHalfThrottle = { b -> onUpdateState { halfThrottle = b } }, onUpdateDeadzone = { f -> onUpdateState { joystickDeadzone = f } }, onStartBinding = { k -> onUpdateState { isAutoBinding = if (isAutoBinding == k) null else k.ifEmpty { null } } }, onToggleInvert = { k -> onUpdateState { when(k) { "ly" -> mappingLY = mappingLY.copy(inverted = !mappingLY.inverted); "lx" -> mappingLX = mappingLX.copy(inverted = !mappingLX.inverted); "ry" -> mappingRY = mappingRY.copy(inverted = !mappingRY.inverted); "rx" -> mappingRX = mappingRX.copy(inverted = !mappingRX.inverted) } } }, onManualBind = { k, a -> onUpdateState { val l = if (a >= 101) "Serial CH${a - 100}" else "Axis $a"; val m = ChannelMapping(a, false, l); when(k) { "ly" -> mappingLY = m; "lx" -> mappingLX = m; "ry" -> mappingRY = m; "rx" -> mappingRX = m } } }, onModeChange = { m -> onUpdateState { joystickMode = m } }, onToggleGlobalRates = { b -> onUpdateState { useGlobalRates = b } }, onUpdateGlobalRate = { r -> onUpdateState { globalRate = r } }, onUpdateGlobalExpo = { e -> onUpdateState { globalExpo = e } }, onUpdateIndividualRate = { k, r -> onUpdateState { when(k) { "T" -> rateT = r; "Y" -> rateY = r; "P" -> rateP = r; "R" -> rateR = r } } }, onUpdateIndividualExpo = { k, e -> onUpdateState { when(k) { "T" -> expoT = e; "Y" -> expoY = e; "P" -> expoP = e; "R" -> expoR = e } } }, onToggleShowIndividual = { b -> onUpdateState { showIndividualRates = b } }, onResetRates = { onUpdateState { globalRate = AppConfig.JoystickDefaults.RATE; globalExpo = AppConfig.JoystickDefaults.EXPO; rateT = AppConfig.JoystickDefaults.RATE; expoT = AppConfig.JoystickDefaults.EXPO; rateY = AppConfig.JoystickDefaults.RATE; expoY = AppConfig.JoystickDefaults.EXPO; rateP = AppConfig.JoystickDefaults.RATE; expoP = AppConfig.JoystickDefaults.EXPO; rateR = AppConfig.JoystickDefaults.RATE; expoR = AppConfig.JoystickDefaults.EXPO; joystickDeadzone = AppConfig.JoystickDefaults.DEADZONE } }, inputMode = state.inputMode, rawChannels = stickState.visualBuffer, onToggleMappingUnlock = { b -> onUpdateState { isMappingUnlocked = b } }, activeSerialPath = state.activeSerialPath, rawHexData = state.rawHexData, linkType = state.linkType, baudRate = state.baudRate, connectionStatus = state.connectionStatus, packetsPerSecond = stickState.packetsPerSecond, detectedProtocol = state.detectedProtocol, isSerialConflict = state.isSerialConflict, conflictPid = state.conflictPid, rawBytesCount = state.rawBytesCount, bufferUsage = state.bufferUsage, isSignalActive = stickState.isSignalActive, lockedProtocol = state.lockedProtocol, onUpdateLockedProtocol = { p -> onUpdateState { lockedProtocol = p } }, isLogcatEnabled = state.isLogcatEnabled, logcatContent = state.logcatContent, onToggleLogcat = { b -> onUpdateState { isLogcatEnabled = b } }, onClearLogcat = { onUpdateState { logcatContent = "" } }, isHardwareController = state.isHardwareController, onOpenAuxMapping = { onUpdateState { showAuxMappingOverlay = true } }, onUpdateInputMode = onUpdateInputMode, onScanUsb = onScanUsb, onUpdateBaudRate = onUpdateBaudRate, onUpdateLockedPath = onUpdateLockedPath, onOpenNetworkSettings = onOpenNetworkSettings, availablePorts = availablePorts, onExportLog = onExportLog, onToggleNetworkConnection = onToggleNetworkConnection, showHardwareMonitor = state.showHardwareMonitor, onToggleHardwareMonitor = { b -> onUpdateState { showHardwareMonitor = b } }, state = state, jitter = state.jitter, stability = state.stability, onTargetPositioned = onTargetPositioned, isMappingUnlocked = state.isMappingUnlocked, isInteractionLocked = state.isInteractionLocked, diagnosticLog = state.diagnosticLog
                                )
                                SettingsTab.SYSTEM -> Column(modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
                                    // [v1.7.9.13] 語言切換選單 - 移至頂部並改為下拉式選單
                                    var langMenuExpanded by remember { mutableStateOf(false) }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_language), 
                                            color = NikoTheme.colors.textPrimary, 
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        Box {
                                            Surface(
                                                color = NikoTheme.colors.textPrimary.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, NikoTheme.colors.divider),
                                                modifier = Modifier
                                                    .width(180.dp)
                                                    .height(36.dp)
                                                    .clickable { langMenuExpanded = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    val currentLangLabel = if (state.appLanguage == "zh") stringResource(R.string.settings_lang_zh) else stringResource(R.string.settings_lang_en)
                                                    Text(currentLangLabel, color = NikoTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    Icon(Icons.Default.ArrowDropDown, null, tint = NikoTheme.colors.primary)
                                                }
                                            }
                                            
                                            DropdownMenu(
                                                expanded = langMenuExpanded,
                                                onDismissRequest = { langMenuExpanded = false },
                                                properties = androidx.compose.ui.window.PopupProperties(focusable = false), // [v1.7.9.18] 防止彈出選單喚起系統導航欄
                                                modifier = Modifier.background(NikoTheme.colors.panel).width(180.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.settings_lang_zh), color = NikoTheme.colors.textPrimary) },
                                                    onClick = { onLanguageChange("zh"); langMenuExpanded = false }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.settings_lang_en), color = NikoTheme.colors.textPrimary) },
                                                    onClick = { onLanguageChange("en"); langMenuExpanded = false }
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        color = NikoTheme.colors.textPrimary.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            // [v1.7.8] 加固：依據 AppConfig 控制是否顯示啟動自動掃描
                                            if (AppConfig.SystemDefaults.showAutoConnect()) {
                                                SystemSettingRow(
                                                    label = AppConfig.SystemDefaults.getAutoConnectLabel(state.appLanguage),
                                                    checked = state.isAutoConnectEnabled,
                                                    onToggle = { onUpdateState { isAutoConnectEnabled = it } },
                                                    thumbColor = NikoTheme.colors.primary
                                                )
                                                HorizontalDivider(color = NikoTheme.colors.divider)
                                            }

                                            // [v1.7.15] 起動動力增益切換 (解決部分硬體如 A55 起飛延遲問題)
                                            SystemSettingRow(
                                                label = "起動動力增益",
                                                description = "解決部分裝置推桿起飛延遲問題 (解決數值下溢)。",
                                                checked = state.useIdleWakeupPatch,
                                                onToggle = { onUpdateState { useIdleWakeupPatch = it } },
                                                thumbColor = NikoTheme.colors.primary
                                            )
                                            HorizontalDivider(color = NikoTheme.colors.divider)

                                            SystemSettingRow(stringResource(R.string.settings_hide_status_bar), state.hideStatusBar, { onUpdateState { hideStatusBar = it } })
                                            HorizontalDivider(color = NikoTheme.colors.divider)
                                            SystemSettingRow(stringResource(R.string.settings_pause_in_settings), state.pauseInSettings, { onUpdateState { pauseInSettings = it } })
                                            HorizontalDivider(color = NikoTheme.colors.divider)
                                            // [v1.7.8] 通訊主權切換開關：改為 HID 優先 (推薦使用)
                                            SystemSettingRow(
                                                label = stringResource(R.string.settings_hid_priority),
                                                description = stringResource(R.string.settings_hid_priority_desc),
                                                checked = state.isHidPriorityEnabled,
                                                onToggle = { enabled ->
                                                    onUpdateState { isHidPriorityEnabled = enabled }
                                                    onSaveSettings() 
                                                },
                                                thumbColor = NikoTheme.colors.primary
                                            )
                                            HorizontalDivider(color = NikoTheme.colors.divider)
                                            
                                            // [v1.7.8] 專業降落標準 (帶說明小字) 搬移至此
                                            SystemSettingRow(
                                                label = stringResource(R.string.settings_strict_landing),
                                                description = stringResource(R.string.settings_strict_landing_desc),
                                                checked = state.useStrictLanding,
                                                onToggle = { onUpdateState { useStrictLanding = it } },
                                                thumbColor = NikoTheme.colors.safety
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { 
                                                    onUpdateState { 
                                                        showTutorial = true
                                                        hasShownJoystickTutorial = false
                                                        hasShownClimateTutorial = false
                                                        showSettings = false 
                                                    } 
                                                },
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.primary.copy(0.8f)),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Help, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.settings_btn_tutorial), color = Color.White, fontSize = 11.sp)
                                            }
                                            
                                            Button(
                                                onClick = onRestoreDefaults,
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.warning.copy(0.8f)),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.action_restore_defaults), color = Color.White, fontSize = 11.sp)
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            var clickCount by remember { mutableIntStateOf(0) }
                                            val expertActiveMsg = stringResource(R.string.settings_expert_active)
                                            Text(
                                                text = "${stringResource(R.string.settings_dev_label)}${com.horizon.nikonikodronesimulator.model.AppConfig.DEVELOPER}",
                                                color = NikoTheme.colors.textSecondary, fontSize = 11.sp, 
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
                                                color = NikoTheme.colors.primary.copy(alpha = 0.7f), fontSize = 10.sp,
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

        state.showModelConfigConfirm?.let { droneId -> ModelConfigConfirmDialog(droneName = com.horizon.nikonikodronesimulator.model.DroneRegistry.getSpec(droneId).name, onConfirm = { onUpdateState { showModelConfigConfirm = null; showModelMappingOverlay = droneId } }, onDismiss = { onUpdateState { showModelConfigConfirm = null } }) }
        if (state.showIndividualRates) { com.horizon.nikonikodronesimulator.ui.settings.HandfeelTuningOverlay(title = stringResource(R.string.joystick_section_rates), rateT = state.rateT, expoT = state.expoT, rateY = state.rateY, expoY = state.expoY, rateP = state.rateP, expoP = state.expoP, rateR = state.rateR, expoR = state.expoR, onUpdateRate = { k, v -> onUpdateState { when(k){ "T" -> rateT = v; "Y" -> rateY = v; "P" -> rateP = v; "R" -> rateR = v } } }, onUpdateExpo = { k, v -> onUpdateState { when(k){ "T" -> expoT = v; "Y" -> expoY = v; "P" -> expoP = v; "R" -> expoR = v } } }, onResetAll = { onUpdateState { globalRate = 1.2f; globalExpo = 0.4f; rateT = 1.2f; expoT = 0.4f; rateY = 1.2f; expoY = 0.4f; rateP = 1.2f; expoP = 0.4f; rateR = 1.2f; expoR = 0.4f } }, onClose = { onUpdateState { showIndividualRates = false } }, joystickMode = state.joystickMode, isGeneMode = false) }
        state.showModelMappingOverlay?.let { droneId -> com.horizon.nikonikodronesimulator.ui.settings.HandfeelTuningOverlay(title = "${stringResource(R.string.settings_tab_drone)}: ${com.horizon.nikonikodronesimulator.model.DroneRegistry.getSpec(droneId).name}", rateT_Up = state.modelGene.rateT_Up, rateT_Down = state.modelGene.rateT_Down, expoT = state.modelGene.expoT, rateY = state.modelGene.rateY, expoY = state.modelGene.expoY, rateP = state.modelGene.rateP, expoP = state.modelGene.expoP, rateR = state.modelGene.rateR, expoR = state.modelGene.expoR, onUpdateRate = { k, v -> onUpdateState { when(k){ "T_Up" -> modelGene.rateT_Up = v; "T_Down" -> modelGene.rateT_Down = v; "Y" -> modelGene.rateY = v; "P" -> modelGene.rateP = v; "R" -> modelGene.rateR = v } } }, onUpdateExpo = { k, v -> onUpdateState { when(k){ "T" -> modelGene.expoT = v; "Y" -> modelGene.expoY = v; "P" -> modelGene.expoP = v; "R" -> modelGene.expoR = v } } }, onResetAll = { onUpdateState { val module = com.horizon.nikonikodronesimulator.model.DroneRegistry.getModule(droneId); modelGene.apply { rateT_Up = module.baseRateT_Up; rateT_Down = module.baseRateT_Down; expoT = module.baseExpoT; rateY = module.baseRateY; expoY = module.baseExpoY; rateP = module.baseRateP; expoP = module.baseExpoP; rateR = module.baseRateR; expoR = module.baseExpoR } } }, onClose = { onUpdateState { showModelMappingOverlay = null } }, joystickMode = state.joystickMode, isGeneMode = true) }
        if (state.showAuxMappingOverlay) { AuxMappingOverlay(state = state, onStartBinding = { k -> onUpdateState { isAutoBinding = if (isAutoBinding == k) null else k.ifEmpty { null } } }, onManualBind = { k, a -> onUpdateState { val l = if (a >= 101) "Serial CH${a - 100}" else "Axis $a"; val m = ChannelMapping(a, false, l); when(k) { "ly" -> mappingLY = m; "lx" -> mappingLX = m; "ry" -> mappingRY = m; "rx" -> mappingRX = m; "hold" -> mappingHold = m; "arm" -> mappingArm = m; "obsHeight" -> mappingObsHeight = m; "obsTilt" -> mappingObsTilt = m; "fpvTilt" -> mappingFpvTilt = m } } }, onToggleInvert = { k -> onUpdateState { when(k) { "ly" -> mappingLY = mappingLY.copy(inverted = !mappingLY.inverted); "lx" -> mappingLX = mappingLX.copy(inverted = !mappingLX.inverted); "ry" -> mappingRY = mappingRY.copy(inverted = !mappingRY.inverted); "rx" -> mappingRX = mappingRX.copy(inverted = !mappingRX.inverted); "hold" -> mappingHold = mappingHold.copy(inverted = !mappingHold.inverted); "arm" -> mappingArm = mappingArm.copy(inverted = !mappingArm.inverted); "obsHeight" -> mappingObsHeight = mappingObsHeight.copy(inverted = !mappingObsHeight.inverted); "obsTilt" -> mappingObsTilt = mappingObsTilt.copy(inverted = !mappingObsTilt.inverted); "fpvTilt" -> mappingFpvTilt = mappingFpvTilt.copy(inverted = !mappingFpvTilt.inverted) } } }, onDismiss = { onUpdateState { showAuxMappingOverlay = false; isAutoBinding = null } }) }
        if (showExpertUnlockDialog) { 
            com.horizon.nikonikodronesimulator.ui.common.NikoOverlayCard(
                title = stringResource(R.string.settings_expert_unlock_title),
                onDismiss = { showExpertUnlockDialog = false }
            ) {
                var password by remember { mutableStateOf("") }
                var error by remember { mutableStateOf(false) }
                
                Column { 
                    Text(stringResource(R.string.settings_expert_unlock_desc), color = NikoTheme.colors.textSecondary, fontSize = 11.sp) // [v1.7.9.16] 13 -> 11
                    Text(stringResource(R.string.settings_expert_unlock_hint), color = NikoTheme.colors.primary.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp)) // [v1.7.9.16] 16 -> 10
                    OutlinedTextField(
                        value = password, 
                        onValueChange = { password = it; error = false }, 
                        label = { Text(stringResource(R.string.settings_expert_unlock_label), fontSize = 12.sp) }, 
                        singleLine = true, 
                        isError = error, 
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = NikoTheme.colors.textPrimary, unfocusedTextColor = NikoTheme.colors.textPrimary)
                    ) 
                    
                    Spacer(Modifier.height(16.dp)) // [v1.7.9.16] 24 -> 16
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { showExpertUnlockDialog = false },
                            modifier = Modifier.height(32.dp)
                        ) { 
                            Text(stringResource(R.string.action_cancel), fontSize = 12.sp) 
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { 
                                if (password == com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.ADMIN_PASSWORD) {
                                    onUpdateState { isExpertModeLocked = false }
                                    showExpertUnlockDialog = false 
                                } else { 
                                    error = true 
                                } 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.primary),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) { 
                            Text(stringResource(R.string.settings_expert_unlock_btn), color = if(NikoTheme.colors.isLight) Color.White else Color.Black, fontSize = 12.sp)
                        }
                    }
                } 
            }
        }
    }
}

// 移除原有的 ExpertUnlockDialog

@Composable
fun SystemSettingRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, thumbColor: Color = NikoTheme.colors.primary, description: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = NikoTheme.colors.textPrimary, style = NikoTheme.typography.body1)
            description?.let { Text(it, color = NikoTheme.colors.textSecondary, fontSize = 10.sp) }
        }
        Switch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.scale(0.7f), colors = SwitchDefaults.colors(checkedThumbColor = thumbColor, uncheckedThumbColor = if(NikoTheme.colors.isLight) Color.Gray else Color.White))
    }
}

@Composable
fun TabIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) NikoTheme.colors.primary else NikoTheme.colors.textSecondary
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.width(60.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, style = NikoTheme.typography.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun HeaderMiniJoystickWrapper(label: String, x: Float, y: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = NikoTheme.colors.textSecondary, style = NikoTheme.typography.caption)
        Box(modifier = Modifier.size(24.dp).background(NikoTheme.colors.textPrimary.copy(0.1f), NikoTheme.shapes.small).border(0.5.dp, NikoTheme.colors.divider, NikoTheme.shapes.small), contentAlignment = Alignment.Center) {
            // [v1.7.8] 歸一化同步：與主畫面保持一致，Y 軸取反以對齊螢幕座標系
            Box(modifier = Modifier.offset(x = (x * 8).dp, y = (-y * 8).dp).size(6.dp).background(NikoTheme.colors.primary, CircleShape))
        }
    }
}
