package com.horizon.caadronesimulator.model

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.horizon.caadronesimulator.logic.HardwareProfile
import com.horizon.caadronesimulator.model.AppConfig

data class ChannelMapping(
    val axis: Int = -1,
    val inverted: Boolean = false,
    val label: String = "未設定",
    val min: Float = -1f,
    val max: Float = 1f,
    val center: Float = 0f
)

enum class SettingsTab {
    CONTROLLER, ENVIRONMENT, DRONE_SELECTION, CAMERA, SYSTEM
}

enum class ConnectionStatus {
    IDLE, SEARCHING, LINKED, ACTIVE
}

enum class CommDecisionState {
    IDLE, SCANNING, AWAITING_PERMISSION, ENGAGED, LOCKED, ERROR_PERMISSION
}

/**
 * [v1.7.5] 域化狀態架構 (Detoxified Edition)
 * 已移除殭屍變數：cloudOffsetX, cloudOffsetZ, localSettingsMessage, isThrottleHoldEnabled
 */
class DroneState {
    companion object {
        private var instance: DroneState? = null
        fun getInstance(): DroneState {
            if (instance == null) instance = DroneState()
            return instance!!
        }
    }

    // --- 1. 飛行數據域 ---
    class FlightDomain {
        var altitude by mutableFloatStateOf(0f); var posX by mutableFloatStateOf(0f); var posZ by mutableFloatStateOf(0f) 
        var yaw by mutableFloatStateOf(0f); var pitch by mutableFloatStateOf(0f); var roll by mutableFloatStateOf(0f); var speed by mutableFloatStateOf(0f)
        var horizontalDist by mutableFloatStateOf(0f); var batteryVoltage by mutableFloatStateOf(4.2f); var batteryPercent by mutableIntStateOf(100)
        var isCollision by mutableStateOf(false); var isMotorLocked by mutableStateOf(true); var motorRpmFactor by mutableFloatStateOf(0f)
        var flightPath by mutableStateOf<List<Offset>>(emptyList()); var currentRadarScale by mutableFloatStateOf(1.0f)
    }
    val flight = FlightDomain()

    // --- 2. 硬件通訊域 ---
    class HardwareDomain {
        var inputMode by mutableIntStateOf(-1); var connectionStatus by mutableStateOf(ConnectionStatus.IDLE); var commDecisionState by mutableStateOf(CommDecisionState.IDLE)
        var detectedProtocol by mutableStateOf("未知"); var activeSerialPath by mutableStateOf("None"); var usbSerialConnected by mutableStateOf(false)
        var controllerConnected by mutableStateOf(false); var activeHidName by mutableStateOf("通用手把"); var activeAxisLabel by mutableStateOf("NONE")
        var packetsPerSecond by mutableIntStateOf(0); var isSignalActive by mutableStateOf(false); var isAutoConnectEnabled by mutableStateOf(false)
        var newHardwareDetected: android.hardware.usb.UsbDevice? by mutableStateOf(null); var isHardwareController by mutableStateOf(false)
        var rawBytesCount by mutableIntStateOf(0); var bufferUsage by mutableStateOf("0/512"); var jitter by mutableStateOf("0.0 ms")
        var stability by mutableStateOf("100%"); var isHandshaking by mutableStateOf(false); var lockedProtocol by mutableStateOf("")
        var baudRate by mutableIntStateOf(115200); var isSerialConflict by mutableStateOf(false); var conflictPid by mutableStateOf("None")
        var wasInternalSuccess by mutableStateOf(false); var isHardwareVerified by mutableStateOf(false); var probeAttempts by mutableIntStateOf(0)
        var isProbing by mutableStateOf(false); var lockedSerialPath by mutableStateOf(""); var linkType by mutableStateOf("None")
        var rawHexData by mutableStateOf(""); var networkHost by mutableStateOf("127.0.0.1"); var networkPort by mutableIntStateOf(14550)
        var networkProtocol by mutableStateOf("UDP"); var isNetworkConnected by mutableStateOf(false); var showNetworkSettingsDialog by mutableStateOf(false)
        var hardwareProfile: HardwareProfile? by mutableStateOf(null)
    }
    val hardware = HardwareDomain()

    // --- 3. 視覺導演域 ---
    class CameraDomain {
        var cameraMode by mutableStateOf("站位視角 (追蹤)"); var mainFOV by mutableFloatStateOf(45f); var zoomFactor by mutableFloatStateOf(1.5f); var cameraTilt by mutableFloatStateOf(0f)
        var observerHeight by mutableFloatStateOf(6.0f); var observerTilt by mutableFloatStateOf(0f); var enableZoomAssistant by mutableStateOf(true); var showGroundAnchor by mutableStateOf(false)
        var lastManualTouchTime by mutableLongStateOf(0L); var specialTitleScreenPos by mutableStateOf<Offset?>(null); var useSmartObserver by mutableStateOf(false)
        var radarZoomMode by mutableIntStateOf(0); var hudMode by mutableIntStateOf(0)
    }
    val camera = CameraDomain()

    // --- 4. 安全保護域 ---
    class SafetyDomain {
        var isArmSafetyPassed by mutableStateOf(false); var isHoldSafetyPassed by mutableStateOf(false); var lastInteractionTime by mutableLongStateOf(0L)
        var isThrottleHoldEnabled by mutableStateOf(false) // 補回：供 DroneSelectionScreen 使用
        var isThrottleHoldActive by mutableStateOf(true)
    }
    val safety = SafetyDomain()

    // --- 5. 環境配置域 ---
    class EnvironmentDomain {
        var windLevel by mutableIntStateOf(0); var windDirection by mutableStateOf("無"); var windVariation by mutableIntStateOf(0); var windDirVariation by mutableIntStateOf(0)
        var enableVerticalDraft by mutableStateOf(false); var timeOfDay by mutableStateOf("中午"); var isSunSimEnabled by mutableStateOf(false); var sunPosition by mutableFloatStateOf(0.5f)
        var useHardcorePhysics by mutableStateOf(false); var showClouds by mutableStateOf(true); var cloudDensity by mutableFloatStateOf(0.5f); var showMountains by mutableStateOf(true)
        var weatherMode by mutableIntStateOf(1)
        var cloudU by mutableFloatStateOf(0f); var cloudV by mutableFloatStateOf(0f) // 歸位：環境域專屬
    }
    val env = EnvironmentDomain()

    // --- 配置與全局狀態 ---
    var droneType by mutableStateOf("QUAD_STANDARD"); var joystickMode by mutableIntStateOf(2); var isMuted by mutableStateOf(true); var showShadow by mutableStateOf(true); var shadowIntensity by mutableFloatStateOf(0.5f)
    var showObstacles by mutableStateOf(false); var hideStatusBar by mutableStateOf(true); var pauseInSettings by mutableStateOf(true); var applyPhysicalSpecs by mutableStateOf(true); var useFlightLimit by mutableStateOf(true)
    var useSimplifiedMarkers by mutableStateOf(true); var showSpecialTitle by mutableStateOf(true); var customTitle by mutableStateOf(""); var settingsTab by mutableStateOf(SettingsTab.CONTROLLER); var showSettings by mutableStateOf(false)
    var showHardwareMonitor by mutableStateOf(false); var isInteractionLocked by mutableStateOf(false)
    var useStrictLanding by mutableStateOf(true) // [v1.8.24] 專業考核降落安全標準開關
    var isUsbStickyActive by mutableStateOf(false) // [v1.8.36] USB 外接主權鎖定，防止背景掃描干擾
    var systemMessage by mutableStateOf<String?>(null); var localSettingsMessage by mutableStateOf<String?>(null); var diagnosticLog by mutableStateOf("等待啟動...")

    // --- [向下相容代理] ---
    var altitude: Float get() = flight.altitude; set(v) { flight.altitude = v }
    var posX: Float get() = flight.posX; set(v) { flight.posX = v }
    var posZ: Float get() = flight.posZ; set(v) { flight.posZ = v }
    var yaw: Float get() = flight.yaw; set(v) { flight.yaw = v }
    var pitch: Float get() = flight.pitch; set(v) { flight.pitch = v }
    var roll: Float get() = flight.roll; set(v) { flight.roll = v }
    var speed: Float get() = flight.speed; set(v) { flight.speed = v }
    var horizontalDist: Float get() = flight.horizontalDist; set(v) { flight.horizontalDist = v }
    var batteryVoltage: Float get() = flight.batteryVoltage; set(v) { flight.batteryVoltage = v }
    var batteryPercent: Int get() = flight.batteryPercent; set(v) { flight.batteryPercent = v }
    var isCollision: Boolean get() = flight.isCollision; set(v) { flight.isCollision = v }
    var isMotorLocked: Boolean get() = flight.isMotorLocked; set(v) { flight.isMotorLocked = v }
    var motorRpmFactor: Float get() = flight.motorRpmFactor; set(v) { flight.motorRpmFactor = v }
    var flightPath: List<Offset> get() = flight.flightPath; set(v) { flight.flightPath = v }
    var currentRadarScale: Float get() = flight.currentRadarScale; set(v) { flight.currentRadarScale = v }

    var inputMode: Int get() = hardware.inputMode; set(v) { hardware.inputMode = v }
    var connectionStatus: ConnectionStatus get() = hardware.connectionStatus; set(v) { hardware.connectionStatus = v }
    var commDecisionState: CommDecisionState get() = hardware.commDecisionState; set(v) { hardware.commDecisionState = v }
    var detectedProtocol: String get() = hardware.detectedProtocol; set(v) { hardware.detectedProtocol = v }
    var activeSerialPath: String get() = hardware.activeSerialPath; set(v) { hardware.activeSerialPath = v }
    var usbSerialConnected: Boolean get() = hardware.usbSerialConnected; set(v) { hardware.usbSerialConnected = v }
    var controllerConnected: Boolean get() = hardware.controllerConnected; set(v) { hardware.controllerConnected = v }
    var activeHidName: String get() = hardware.activeHidName; set(v) { hardware.activeHidName = v }
    var activeAxisLabel: String get() = hardware.activeAxisLabel; set(v) { hardware.activeAxisLabel = v }
    var packetsPerSecond: Int get() = hardware.packetsPerSecond; set(v) { hardware.packetsPerSecond = v }
    var isSignalActive: Boolean get() = hardware.isSignalActive; set(v) { hardware.isSignalActive = v }
    var isAutoConnectEnabled: Boolean get() = hardware.isAutoConnectEnabled; set(v) { hardware.isAutoConnectEnabled = v }
    var newHardwareDetected: android.hardware.usb.UsbDevice? get() = hardware.newHardwareDetected; set(v) { hardware.newHardwareDetected = v }
    var isHardwareController: Boolean get() = hardware.isHardwareController; set(v) { hardware.isHardwareController = v }
    var rawBytesCount: Int get() = hardware.rawBytesCount; set(v) { hardware.rawBytesCount = v }
    var bufferUsage: String get() = hardware.bufferUsage; set(v) { hardware.bufferUsage = v }
    var jitter: String get() = hardware.jitter; set(v) { hardware.jitter = v }
    var stability: String get() = hardware.stability; set(v) { hardware.stability = v }
    var isHandshaking: Boolean get() = hardware.isHandshaking; set(v) { hardware.isHandshaking = v }
    var lockedProtocol: String get() = hardware.lockedProtocol; set(v) { hardware.lockedProtocol = v }
    var baudRate: Int get() = hardware.baudRate; set(v) { hardware.baudRate = v }
    var isSerialConflict: Boolean get() = hardware.isSerialConflict; set(v) { hardware.isSerialConflict = v }
    var conflictPid: String get() = hardware.conflictPid; set(v) { hardware.conflictPid = v }
    var wasInternalSuccess: Boolean get() = hardware.wasInternalSuccess; set(v) { hardware.wasInternalSuccess = v }
    var isHardwareVerified: Boolean get() = hardware.isHardwareVerified; set(v) { hardware.isHardwareVerified = v }
    var probeAttempts: Int get() = hardware.probeAttempts; set(v) { hardware.probeAttempts = v }
    var isProbing: Boolean get() = hardware.isProbing; set(v) { hardware.isProbing = v }
    var lockedSerialPath: String get() = hardware.lockedSerialPath; set(v) { hardware.lockedSerialPath = v }
    var linkType: String get() = hardware.linkType; set(v) { hardware.linkType = v }
    var rawHexData: String get() = hardware.rawHexData; set(v) { hardware.rawHexData = v }
    var networkHost: String get() = hardware.networkHost; set(v) { hardware.networkHost = v }
    var networkPort: Int get() = hardware.networkPort; set(v) { hardware.networkPort = v }
    var networkProtocol: String get() = hardware.networkProtocol; set(v) { hardware.networkProtocol = v }
    var isNetworkConnected: Boolean get() = hardware.isNetworkConnected; set(v) { hardware.isNetworkConnected = v }
    var showNetworkSettingsDialog: Boolean get() = hardware.showNetworkSettingsDialog; set(v) { hardware.showNetworkSettingsDialog = v }
    var hardwareProfile: HardwareProfile? get() = hardware.hardwareProfile; set(v) { hardware.hardwareProfile = v }

    var cameraMode: String get() = camera.cameraMode; set(v) { camera.cameraMode = v }
    var mainFOV: Float get() = camera.mainFOV; set(v) { camera.mainFOV = v }
    var zoomFactor: Float get() = camera.zoomFactor; set(v) { camera.zoomFactor = v }
    var cameraTilt: Float get() = camera.cameraTilt; set(v) { camera.cameraTilt = v }
    var observerHeight: Float get() = camera.observerHeight; set(v) { camera.observerHeight = v }
    var observerTilt: Float get() = camera.observerTilt; set(v) { camera.observerTilt = v }
    var enableZoomAssistant: Boolean get() = camera.enableZoomAssistant; set(v) { camera.enableZoomAssistant = v }
    var showGroundAnchor: Boolean get() = camera.showGroundAnchor; set(v) { camera.showGroundAnchor = v }
    var lastManualTouchTime: Long get() = camera.lastManualTouchTime; set(v) { camera.lastManualTouchTime = v }
    var specialTitleScreenPos: Offset? get() = camera.specialTitleScreenPos; set(v) { camera.specialTitleScreenPos = v }
    var useSmartObserver: Boolean get() = camera.useSmartObserver; set(v) { camera.useSmartObserver = v }
    var radarZoomMode: Int get() = camera.radarZoomMode; set(v) { camera.radarZoomMode = v }
    var hudMode: Int get() = camera.hudMode; set(v) { camera.hudMode = v }

    var isArmSafetyPassed: Boolean get() = safety.isArmSafetyPassed; set(v) { safety.isArmSafetyPassed = v }
    var isHoldSafetyPassed: Boolean get() = safety.isHoldSafetyPassed; set(v) { safety.isHoldSafetyPassed = v }
    var lastInteractionTime: Long get() = safety.lastInteractionTime; set(v) { safety.lastInteractionTime = v }
    var isThrottleHoldEnabled: Boolean get() = safety.isThrottleHoldEnabled; set(v) { safety.isThrottleHoldEnabled = v }
    var isThrottleHoldActive: Boolean get() = safety.isThrottleHoldActive; set(v) { safety.isThrottleHoldActive = v }

    var windLevel: Int get() = env.windLevel; set(v) { env.windLevel = v }
    var windDirection: String get() = env.windDirection; set(v) { env.windDirection = v }
    var windVariation: Int get() = env.windVariation; set(v) { env.windVariation = v }
    var windDirVariation: Int get() = env.windDirVariation; set(v) { env.windDirVariation = v }
    var enableVerticalDraft: Boolean get() = env.enableVerticalDraft; set(v) { env.enableVerticalDraft = v }
    var timeOfDay: String get() = env.timeOfDay; set(v) { env.timeOfDay = v }
    var isSunSimEnabled: Boolean get() = env.isSunSimEnabled; set(v) { env.isSunSimEnabled = v }
    var sunPosition: Float get() = env.sunPosition; set(v) { env.sunPosition = v }
    var useHardcorePhysics: Boolean get() = env.useHardcorePhysics; set(v) { env.useHardcorePhysics = v }
    var showClouds: Boolean get() = env.showClouds; set(v) { env.showClouds = v }
    var cloudDensity: Float get() = env.cloudDensity; set(v) { env.cloudDensity = v }
    var showMountains: Boolean get() = env.showMountains; set(v) { env.showMountains = v }
    var weatherMode: Int get() = env.weatherMode; set(v) { env.weatherMode = v }
    var cloudU: Float get() = env.cloudU; set(v) { env.cloudU = v }
    var cloudV: Float get() = env.cloudV; set(v) { env.cloudV = v }

    var reverseSliderSides by mutableStateOf(true); var autoPiPRelocate by mutableStateOf(true); var showSideRulers by mutableStateOf(true); var showSideSliders by mutableStateOf(true)
    var showTutorial by mutableStateOf(true); var showJoystickTutorial by mutableStateOf(false); var showClimateTutorial by mutableStateOf(false); var hasShownJoystickTutorial by mutableStateOf(false); var hasShownClimateTutorial by mutableStateOf(false); var showVirtualJoysticks by mutableStateOf(false)
    var isMenuExpanded by mutableStateOf(false); var showFlightPath by mutableStateOf(false); var showUpdateNotice by mutableStateOf(false); var isNearBoundary by mutableStateOf(false); var showAuxMappingOverlay by mutableStateOf(false); var showTroubleshootingHint by mutableStateOf(false)
    var showModelConfigConfirm by mutableStateOf<String?>(null); var showModelMappingOverlay by mutableStateOf<String?>(null); var isCalibrating by mutableStateOf(false); var calibrationStep by mutableIntStateOf(0)
    var isAutoBinding by mutableStateOf<String?>(null); var setupWizardStep by mutableIntStateOf(0); var wizardWaitingForNeutral by mutableStateOf(false); var wizardCountdown by mutableIntStateOf(0)
    var halfThrottle by mutableStateOf(false); var joystickDeadzone by mutableFloatStateOf(0.15f); var isSettingsLoaded by mutableStateOf(false); var isLogcatEnabled by mutableStateOf(false); var logcatContent by mutableStateOf("")
    var isSpotTimerEnabled: Boolean by mutableStateOf(false); var spotTimerSeconds: Float by mutableFloatStateOf(5.0f); var spotTimerTargetId: Int by mutableIntStateOf(-1); var spotTimerMessage: String? by mutableStateOf(null); var spotTimerSuccess: Boolean by mutableStateOf(false); var spotTimerInZone: Boolean by mutableStateOf(false); var spotTimerStable: Boolean by mutableStateOf(false); var spotTimerLastYaw: Float by mutableFloatStateOf(0f); var spotTimerMessageTimer: Float by mutableFloatStateOf(0f)
    var useGlobalRates by mutableStateOf(true); var showIndividualRates by mutableStateOf(false); var globalRate by mutableFloatStateOf(1.0f); var globalExpo by mutableFloatStateOf(0.0f); var isExpertModeLocked by mutableStateOf(false); var isMappingUnlocked by mutableStateOf(false)
    var lastInZoomZone by mutableStateOf(false); var lastYaw by mutableFloatStateOf(0f)

    // --- [v1.8.20] 攝影機模式切換緩衝 ---
    var isSwitchingMode by mutableStateOf(false)
    var switchProgress by mutableFloatStateOf(0f)
    var switchMessage by mutableStateOf("")

    // --- [v1.7.1] 雙層手感架構的核心定義 ---

    class ModelGene {
        var rateT_Up by mutableFloatStateOf(1.0f); var rateT_Down by mutableFloatStateOf(1.0f)
        var rateY by mutableFloatStateOf(1.0f); var rateP by mutableFloatStateOf(1.0f); var rateR by mutableFloatStateOf(1.0f)
        var expoT by mutableFloatStateOf(0.0f); var expoY by mutableFloatStateOf(0.0f); var expoP by mutableFloatStateOf(0.0f); var expoR by mutableFloatStateOf(0.0f)
    }
    val modelGene = ModelGene()

    class ControllerProfile(defaultLabel: String = "未設定") {
        var mappingLY by mutableStateOf(ChannelMapping(-1, false, defaultLabel))
        var mappingLX by mutableStateOf(ChannelMapping(-1, false, defaultLabel))
        var mappingRY by mutableStateOf(ChannelMapping(-1, false, defaultLabel))
        var mappingRX by mutableStateOf(ChannelMapping(-1, false, defaultLabel))
        var mappingHold by mutableStateOf(ChannelMapping(-1, false, "熄火開關"))
        var mappingArm by mutableStateOf(ChannelMapping(-1, false, "解鎖開關"))
        var mappingObsHeight by mutableStateOf(ChannelMapping(-1, false, "站位高度"))
        var mappingObsTilt by mutableStateOf(ChannelMapping(-1, false, "抬頭角度"))
        var mappingFpvTilt by mutableStateOf(ChannelMapping(-1, false, "FPV 雲台"))
        var mappingFlightMode by mutableStateOf(ChannelMapping(-1, false, "飛行模式"))
        var rateT by mutableFloatStateOf(1.0f)
        var rateY by mutableFloatStateOf(1.0f); var rateP by mutableFloatStateOf(1.0f); var rateR by mutableFloatStateOf(1.0f)
        var expoT by mutableFloatStateOf(0.0f); var expoY by mutableFloatStateOf(0.0f); var expoP by mutableFloatStateOf(0.0f); var expoR by mutableFloatStateOf(0.0f)
    }

    val internalProfile = ControllerProfile("內置系統")
    val externalProfile = ControllerProfile("外接軸向")
    val activeProfile get() = if (inputMode == 1) internalProfile else externalProfile

    var rateT: Float get() = activeProfile.rateT; set(v) { activeProfile.rateT = v }
    var rateY: Float get() = activeProfile.rateY; set(v) { activeProfile.rateY = v }
    var rateP: Float get() = activeProfile.rateP; set(v) { activeProfile.rateP = v }
    var rateR: Float get() = activeProfile.rateR; set(v) { activeProfile.rateR = v }
    var expoT: Float get() = activeProfile.expoT; set(v) { activeProfile.expoT = v }
    var expoY: Float get() = activeProfile.expoY; set(v) { activeProfile.expoY = v }
    var expoP: Float get() = activeProfile.expoP; set(v) { activeProfile.expoP = v }
    var expoR: Float get() = activeProfile.expoR; set(v) { activeProfile.expoR = v }

    var mappingLY: ChannelMapping get() = activeProfile.mappingLY; set(v) { activeProfile.mappingLY = v }
    var mappingLX: ChannelMapping get() = activeProfile.mappingLX; set(v) { activeProfile.mappingLX = v }
    var mappingRY: ChannelMapping get() = activeProfile.mappingRY; set(v) { activeProfile.mappingRY = v }
    var mappingRX: ChannelMapping get() = activeProfile.mappingRX; set(v) { activeProfile.mappingRX = v }
    var mappingHold: ChannelMapping get() = activeProfile.mappingHold; set(v) { activeProfile.mappingHold = v }
    var mappingArm: ChannelMapping get() = activeProfile.mappingArm; set(v) { activeProfile.mappingArm = v }
    var mappingObsHeight: ChannelMapping get() = activeProfile.mappingObsHeight; set(v) { activeProfile.mappingObsHeight = v }
    var mappingObsTilt: ChannelMapping get() = activeProfile.mappingObsTilt; set(v) { activeProfile.mappingObsTilt = v }
    var mappingFpvTilt: ChannelMapping get() = activeProfile.mappingFpvTilt; set(v) { activeProfile.mappingFpvTilt = v }
    var mappingFlightMode: ChannelMapping get() = activeProfile.mappingFlightMode; set(v) { activeProfile.mappingFlightMode = v }

    fun getRate(key: String, stickValue: Float = 0f): Float {
        val userRate = if (useGlobalRates) globalRate else when(key) { "T" -> activeProfile.rateT; "Y" -> activeProfile.rateY; "P" -> activeProfile.rateP; "R" -> activeProfile.rateR; else -> 1.0f }
        val geneRate = when(key) { "T" -> if (stickValue >= 0) modelGene.rateT_Up else modelGene.rateT_Down; "Y" -> modelGene.rateY; "P" -> modelGene.rateP; "R" -> modelGene.rateR; else -> 1.0f }
        return userRate * geneRate
    }

    fun getExpo(key: String): Float {
        val userExpo = if (useGlobalRates) globalExpo else when(key) { "T" -> activeProfile.expoT; "Y" -> activeProfile.expoY; "P" -> activeProfile.expoP; "R" -> activeProfile.expoR; else -> 0.0f }
        val geneExpo = when(key) { "T" -> modelGene.expoT; "Y" -> modelGene.expoY; "P" -> modelGene.expoP; "R" -> modelGene.expoR; else -> 0.0f }
        return (userExpo + geneExpo).coerceIn(0f, 1f)
    }

    fun updateFrom(other: DroneState) {
        this.inputMode = other.inputMode; this.joystickMode = other.joystickMode; this.droneType = other.droneType
        this.mappingLY = other.mappingLY; this.mappingLX = other.mappingLX; this.mappingRY = other.mappingRY; this.mappingRX = other.mappingRX
        this.useGlobalRates = other.useGlobalRates; this.globalRate = other.globalRate; this.globalExpo = other.globalExpo
        this.rateT = other.rateT; this.rateY = other.rateY; this.rateP = other.rateP; this.rateR = other.rateR
        this.expoT = other.expoT; this.expoY = other.expoY; this.expoP = other.expoP; this.expoR = other.expoR
        this.joystickDeadzone = other.joystickDeadzone; this.halfThrottle = other.halfThrottle; this.hideStatusBar = other.hideStatusBar
        this.pauseInSettings = other.pauseInSettings; this.applyPhysicalSpecs = other.applyPhysicalSpecs; this.showSideSliders = other.showSideSliders
        this.showSpecialTitle = other.showSpecialTitle; this.useSimplifiedMarkers = other.useSimplifiedMarkers; this.windLevel = other.windLevel
        this.windDirection = other.windDirection; this.timeOfDay = other.timeOfDay; this.showShadow = other.showShadow
        this.shadowIntensity = other.shadowIntensity; this.observerTilt = other.observerTilt; this.isSunSimEnabled = other.isSunSimEnabled
        this.sunPosition = other.sunPosition; this.enableZoomAssistant = other.enableZoomAssistant; this.showMountains = other.showMountains; this.weatherMode = other.weatherMode
        this.useStrictLanding = other.useStrictLanding
    }
}
