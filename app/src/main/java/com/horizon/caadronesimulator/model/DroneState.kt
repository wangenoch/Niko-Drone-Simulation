package com.horizon.caadronesimulator.model

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset

data class ChannelMapping(
    val axis: Int = -1,
    val inverted: Boolean = false,
    val label: String = "未設定",
    val min: Float = -1f,
    val max: Float = 1f,
    val center: Float = 0f
)

/**
 * [v1.2.71] 設定分頁列舉
 */
enum class SettingsTab {
    CONTROLLER,  // 搖桿映射、靈敏度
    ENVIRONMENT, // 天氣、光照、陰影
    DRONE_SELECTION, // 無人機機型選擇
    CAMERA,      // 視角模式、倍率、仰角
    SYSTEM       // 一般系統設定
}

/**
 * [v1.3.6] 通訊連線狀態列舉
 */
enum class ConnectionStatus {
    IDLE,       // 閒置/未連接
    SEARCHING,  // 偵測協議中
    LINKED,     // 已建立物理連線但無有效信號
    ACTIVE      // 正常運作且有穩定數據流
}

/**
 * [v1.2.71] 深度效能優化版 DroneState
 * 採用 "局部觀測架構 (Scheme C)"，徹底解決全域重繪導致的模擬器 LAG。
 */
class DroneState {
    // --- 高頻更新數據 (使用原生 FloatState 以避免 Boxing) ---
    var altitude by mutableFloatStateOf(0f)
    var posX by mutableFloatStateOf(0f)
    var posZ by mutableFloatStateOf(-6.0f)
    var yaw by mutableFloatStateOf(0f)
    var pitch by mutableFloatStateOf(0f)
    var roll by mutableFloatStateOf(0f)
    var cameraTilt by mutableFloatStateOf(0f)
    var observerHeight by mutableFloatStateOf(6.0f)
    var observerTilt by mutableFloatStateOf(0f) // [v1.3.8] 站位視角仰角
    var reverseSliderSides by mutableStateOf(false)
    var lastInZoomZone by mutableStateOf(false)
    var activeHidName by mutableStateOf("通用手把")
    var speed by mutableFloatStateOf(0f)
    var lastYaw by mutableFloatStateOf(0f)
    var zoomFactor by mutableFloatStateOf(1.5f)

    // --- [v1.2.81 階段二] 硬件感知與輸入鎖 ---
    var hardwareProfile: com.horizon.caadronesimulator.logic.HardwareProfile? by mutableStateOf(null)
    var activeInputSource: Int by mutableIntStateOf(1) // 0: HID, 1: Internal, 2: Virtual

    // --- 狀態開關 ---
    var isMotorLocked by mutableStateOf(true)
    var isCollision by mutableStateOf(false)
    var isMuted by mutableStateOf(true)
    var isHardwareController by mutableStateOf(false) // 標記是否為專業一體化遙控器 (AX12/MK15)
    var wasInternalSuccess by mutableStateOf(false)   // 紀錄內置系統是否曾連線成功
    var showShadow by mutableStateOf(true)
    var shadowIntensity by mutableFloatStateOf(0.5f)
    var showObstacles by mutableStateOf(false)
    var showStatusBar by mutableStateOf(false)
    var pauseInSettings by mutableStateOf(true)
    var applyPhysicalSpecs by mutableStateOf(true)
    var showTutorial by mutableStateOf(true)
    var showJoystickTutorial by mutableStateOf(false)
    var showClimateTutorial by mutableStateOf(false)
    var hasShownJoystickTutorial by mutableStateOf(false)
    var hasShownClimateTutorial by mutableStateOf(false)
    var controllerConnected by mutableStateOf(false)
    var usbSerialConnected by mutableStateOf(false)
    var connectionStatus by mutableStateOf(ConnectionStatus.IDLE)
    var showVirtualJoysticks by mutableStateOf(false)
    var isMenuExpanded by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showHardwareMonitor by mutableStateOf(false)
    var showFlightPath by mutableStateOf(false)
    var showUpdateNotice by mutableStateOf(false)
    var isInteractionLocked by mutableStateOf(false)
    var isMappingUnlocked by mutableStateOf(false)
    var useSimplifiedMarkers by mutableStateOf(false) // [v1.3.8] 簡約標線模式
    var showSpecialTitle by mutableStateOf(false)    // [v1.4.1] 顯示高雄市消防局義消總隊標題
    var specialTitleScreenPos by mutableStateOf<Offset?>(null) // [v1.4.1] 標題在螢幕上的投影位置
    var useFlightLimit by mutableStateOf(true)
    var enableZoomAssistant by mutableStateOf(true)
    var isNearBoundary by mutableStateOf(false)
    var batteryVoltage by mutableFloatStateOf(4.2f)
    var batteryPercent by mutableIntStateOf(100)

    // --- [v1.4.0] 視覺與空域輔助系統狀態 ---
    var mainFOV by mutableFloatStateOf(45f)          // 廣角視距 (30-70)
    var useSmartObserver by mutableStateOf(false)   // 智慧觀察員 (自動追蹤)
    var showSideRulers by mutableStateOf(true)      // 側邊導航刻度標尺
    var showGroundAnchor by mutableStateOf(false)   // 地面位置投影 (AR)
    var autoPiPRelocate by mutableStateOf(true)     // 視窗自動避讓邏輯
    var isManualOverrideActive by mutableStateOf(false) // 手動覆蓋屏蔽標記
    var lastManualTouchTime by mutableLongStateOf(0L)   // 最後手動操作時間
    var pipOffsetXPct by mutableFloatStateOf(0.5f)  // PiP 水平位置百分比 (0:左, 0.5:中, 1:右)
    var showUsbSelectionDialog by mutableStateOf(false)
    var showTroubleshootingHint by mutableStateOf(false)
    var showAuxMappingOverlay by mutableStateOf(false) // [v1.5.0] 輔助映射彈窗開關
    
    // --- [v1.5.0] 專業動力狀態機 ---
    var isThrottleHoldActive by mutableStateOf(true)  // 熄火開關狀態 (預設為熄火)
    var motorRpmFactor by mutableFloatStateOf(0f)     // 馬達實際轉速比例 (0.0 ~ 1.0)
    var isSafetyStartupPassed by mutableStateOf(false) // 安全啟動檢查是否通過


    // --- 智慧型動態 UI 邏輯 ---
    val shouldShowExpertUI: Boolean get() {
        // 外接模式永遠顯示，內置模式僅在 非 UMBUS 或 已手動解鎖時顯示
        return (inputMode == 0) || (!detectedProtocol.contains("UMBUS")) || isMappingUnlocked
    }

    // --- 環境參數 ---
    var windLevel by mutableIntStateOf(0)
    var windDirection by mutableStateOf("無")
    var windVariation by mutableIntStateOf(0)
    var windDirVariation by mutableIntStateOf(0)
    var enableVerticalDraft by mutableStateOf(false)
    var timeOfDay by mutableStateOf("中午")
    var isSunSimEnabled by mutableStateOf(false)
    var sunPosition by mutableFloatStateOf(0.5f) // 0.0 (東) ~ 1.0 (西)
    var useHardcorePhysics by mutableStateOf(false)
    
    // --- [v1.3.9] 網絡通訊參數 ---
    var networkHost by mutableStateOf("127.0.0.1")
    var networkPort by mutableIntStateOf(14550)
    var networkProtocol by mutableStateOf("UDP") // UDP or TCP
    var isNetworkConnected by mutableStateOf(false)
    var showNetworkSettingsDialog by mutableStateOf(false)

    // --- 設定分頁與模式 ---
    var settingsTab by mutableStateOf(SettingsTab.CONTROLLER)
    var inputMode by mutableIntStateOf(-1)
    var joystickMode by mutableIntStateOf(2)
    var droneType by mutableStateOf("QUAD_STANDARD")
    var cameraMode by mutableStateOf("站位視角 (追蹤)")

    var isCalibrating by mutableStateOf(false)
    var calibrationStep by mutableIntStateOf(0)
    var isAutoBinding by mutableStateOf<String?>(null)
    var setupWizardStep by mutableIntStateOf(0)
    var wizardWaitingForNeutral by mutableStateOf(false)
    var wizardReadyToTrigger by mutableStateOf(false)
    var wizardCountdown by mutableIntStateOf(0)
    var useRawMapping by mutableStateOf(false)
    var halfThrottle by mutableStateOf(false)
    var joystickDeadzone by mutableFloatStateOf(0.15f)
    var activeAxisLabel by mutableStateOf("NONE")

    // --- 系統訊息與日誌 [v1.2.81 階段三修正] ---
    var systemMessage by mutableStateOf<String?>(null)        // 全域系統警告 (HUD)
    var localSettingsMessage by mutableStateOf<String?>(null) // 設定視窗內部回饋
    var diagnosticLog by mutableStateOf("等待掃描...")
    
    // [v1.2.82] 智慧識別與協議定鎖
    var isHardwareVerified by mutableStateOf(false)
    var probeAttempts by mutableIntStateOf(0)
    var isProbing by mutableStateOf(false)
    
    // [v1.2.86] RadarHUD 智慧縮放模式 (0:全圖, 1:八字自動, 2:跟隨)
    var radarZoomMode by mutableIntStateOf(0)
    var currentRadarScale by mutableFloatStateOf(1.0f)

    var activeSerialPath by mutableStateOf("None")
    var rawHexData by mutableStateOf("")
    var linkType by mutableStateOf("None")
    var connectionType by mutableStateOf("None")
    var baudRate by mutableIntStateOf(115200)
    var detectedProtocol by mutableStateOf("未知")
    var lockedProtocol by mutableStateOf("")
    var isSerialConflict by mutableStateOf(false)
    var conflictPid by mutableStateOf("None")
    var rawBytesCount by mutableIntStateOf(0)
    var bufferUsage by mutableStateOf("0/512")
    var isHandshaking by mutableStateOf(false)
    var handshakePPS by mutableIntStateOf(0)
    var isSettingsLoaded by mutableStateOf(false)
    var isLogcatEnabled by mutableStateOf(false)
    var logcatContent by mutableStateOf("")

    // --- 定點計時器 ---
    var isSpotTimerEnabled: Boolean by mutableStateOf(false)
    var spotTimerSeconds: Float by mutableFloatStateOf(5.0f)
    var spotTimerTargetId: Int by mutableIntStateOf(-1)
    var spotTimerMessage: String? by mutableStateOf(null)
    var spotTimerSuccess: Boolean by mutableStateOf(false)
    var spotTimerInZone: Boolean by mutableStateOf(false)
    var spotTimerStable: Boolean by mutableStateOf(false)
    var spotTimerLastYaw: Float by mutableFloatStateOf(0f)
    var spotTimerMessageTimer: Float by mutableFloatStateOf(0f)

    var flightPath: List<Offset> by mutableStateOf(emptyList())

    // --- 靈敏度曲線 ---
    var useGlobalRates by mutableStateOf(true)
    var showIndividualRates by mutableStateOf(false)
    var globalRate by mutableFloatStateOf(1.0f)
    var globalExpo by mutableFloatStateOf(0.0f)

    // --- [v1.5.0 擴充] 雙軌參數獨立架構 (含輔助開關) ---
    class ControllerProfile(defaultLabel: String = "未設定") {
        var mappingLY by mutableStateOf(ChannelMapping(-1, false, defaultLabel))
        var mappingLX by mutableStateOf(ChannelMapping(-1, false, defaultLabel))
        var mappingRY by mutableStateOf(ChannelMapping(-1, false, defaultLabel))
        var mappingRX by mutableStateOf(ChannelMapping(-1, false, defaultLabel))
        
        // [v1.5.0] 輔助開關映射
        var mappingHold by mutableStateOf(ChannelMapping(-1, false, "熄火開關"))
        var mappingArm by mutableStateOf(ChannelMapping(-1, false, "解鎖開關"))
        var mappingObsHeight by mutableStateOf(ChannelMapping(-1, false, "站位高度"))
        var mappingObsTilt by mutableStateOf(ChannelMapping(-1, false, "抬頭角度"))
        var mappingFpvTilt by mutableStateOf(ChannelMapping(-1, false, "FPV 雲台"))

        var rateLY by mutableFloatStateOf(1.0f); var expoLY by mutableFloatStateOf(0.0f)
        var rateLX by mutableFloatStateOf(1.0f); var expoLX by mutableFloatStateOf(0.0f)
        var rateRY by mutableFloatStateOf(1.0f); var expoRY by mutableFloatStateOf(0.0f)
        var rateRX by mutableFloatStateOf(1.0f); var expoRX by mutableFloatStateOf(0.0f)
    }

    val internalProfile = ControllerProfile("內置通道")
    val externalProfile = ControllerProfile("外接軸向")

    val activeProfile get() = if (inputMode == 1) internalProfile else externalProfile

    // 核心映射對象獲取器
    var mappingLY: ChannelMapping get() = activeProfile.mappingLY; set(v) { activeProfile.mappingLY = v }
    var mappingLX: ChannelMapping get() = activeProfile.mappingLX; set(v) { activeProfile.mappingLX = v }
    var mappingRY: ChannelMapping get() = activeProfile.mappingRY; set(v) { activeProfile.mappingRY = v }
    var mappingRX: ChannelMapping get() = activeProfile.mappingRX; set(v) { activeProfile.mappingRX = v }

    // [v1.5.0] 輔助映射對象獲取器
    var mappingHold: ChannelMapping get() = activeProfile.mappingHold; set(v) { activeProfile.mappingHold = v }
    var mappingArm: ChannelMapping get() = activeProfile.mappingArm; set(v) { activeProfile.mappingArm = v }
    var mappingObsHeight: ChannelMapping get() = activeProfile.mappingObsHeight; set(v) { activeProfile.mappingObsHeight = v }
    var mappingObsTilt: ChannelMapping get() = activeProfile.mappingObsTilt; set(v) { activeProfile.mappingObsTilt = v }
    var mappingFpvTilt: ChannelMapping get() = activeProfile.mappingFpvTilt; set(v) { activeProfile.mappingFpvTilt = v }

    var rateLY: Float get() = activeProfile.rateLY; set(v) { activeProfile.rateLY = v }
    var expoLY: Float get() = activeProfile.expoLY; set(v) { activeProfile.expoLY = v }
    var rateLX: Float get() = activeProfile.rateLX; set(v) { activeProfile.rateLX = v }
    var expoLX: Float get() = activeProfile.expoLX; set(v) { activeProfile.expoLX = v }
    var rateRY: Float get() = activeProfile.rateRY; set(v) { activeProfile.rateRY = v }
    var expoRY: Float get() = activeProfile.expoRY; set(v) { activeProfile.expoRY = v }
    var rateRX: Float get() = activeProfile.rateRX; set(v) { activeProfile.rateRX = v }
    var expoRX: Float get() = activeProfile.expoRX; set(v) { activeProfile.expoRX = v }

    fun getRate(key: String): Float = if (useGlobalRates) globalRate else when(key) {
        "ly" -> activeProfile.rateLY; "lx" -> activeProfile.rateLX; "ry" -> activeProfile.rateRY; "rx" -> activeProfile.rateRX; else -> 1.0f
    }
    fun getExpo(key: String): Float = if (useGlobalRates) globalExpo else when(key) {
        "ly" -> activeProfile.expoLY; "lx" -> activeProfile.expoLX; "ry" -> activeProfile.expoRY; "rx" -> activeProfile.expoRX; else -> 0.0f
    }

    val mapT get() = when(joystickMode) { 1 -> activeProfile.mappingRY; 4 -> activeProfile.mappingRY; else -> activeProfile.mappingLY }
    val mapY get() = when(joystickMode) { 3 -> activeProfile.mappingRX; 4 -> activeProfile.mappingRX; else -> activeProfile.mappingLX }
    val mapP get() = when(joystickMode) { 1 -> activeProfile.mappingLY; 4 -> activeProfile.mappingLY; else -> activeProfile.mappingRY }
    val mapR get() = when(joystickMode) { 3 -> activeProfile.mappingLX; 4 -> activeProfile.mappingLX; else -> activeProfile.mappingRX }

    /**
     * 手動實作一個 copy 函數，方便 SettingsManager 進行結構化賦值
     */
    fun updateFrom(other: DroneState) {
        this.inputMode = other.inputMode
        this.joystickMode = other.joystickMode
        this.droneType = other.droneType
        this.mappingLY = other.mappingLY
        this.mappingLX = other.mappingLX
        this.mappingRY = other.mappingRY
        this.mappingRX = other.mappingRX
        this.useGlobalRates = other.useGlobalRates
        this.globalRate = other.globalRate
        this.globalExpo = other.globalExpo
        this.rateLY = other.rateLY
        this.expoLY = other.expoLY
        this.rateLX = other.rateLX
        this.expoLX = other.expoLX
        this.rateRY = other.rateRY
        this.expoRY = other.expoRY
        this.rateRX = other.rateRX
        this.expoRX = other.expoRX
        this.joystickDeadzone = other.joystickDeadzone
        this.halfThrottle = other.halfThrottle
        this.showStatusBar = other.showStatusBar
        this.pauseInSettings = other.pauseInSettings
        this.applyPhysicalSpecs = other.applyPhysicalSpecs
        this.windLevel = other.windLevel
        this.windDirection = other.windDirection
        this.timeOfDay = other.timeOfDay
        this.showShadow = other.showShadow
        this.shadowIntensity = other.shadowIntensity
        this.observerTilt = other.observerTilt
        this.isSunSimEnabled = other.isSunSimEnabled
        this.sunPosition = other.sunPosition
        this.enableZoomAssistant = other.enableZoomAssistant
    }
}
