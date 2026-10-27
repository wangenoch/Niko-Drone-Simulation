import Foundation
import CoreGraphics
import Observation

struct ChannelMapping: Equatable, Codable {
    var axis: Int = -1
    var inverted: Bool = false
    var label: String = "UNSET"
    var min: Float = -1.0
    var max: Float = 1.0
    var center: Float = 0.0
}

enum SettingsTab: String, CaseIterable, Identifiable {
    case controller = "CONTROLLER"
    case environment = "ENVIRONMENT"
    case droneSelection = "DRONE_SELECTION"
    case camera = "CAMERA"
    case system = "SYSTEM"
    case theme = "THEME"

    var id: String { rawValue }
}

enum ConnectionStatus: String {
    case idle = "IDLE"
    case searching = "SEARCHING"
    case linked = "LINKED"
    case active = "ACTIVE"
}

enum CrashReason: String {
    case none = "NONE"
    case impact = "IMPACT"
    case batteryLow = "BATTERY_LOW"
    case outOfBounds = "OUT_OF_BOUNDS"
}

/// iPhone Duo / 折疊裝置自適應形態模式
enum FoldablePostureMode: String, CaseIterable, Identifiable {
    case auto = "AUTO"                 // 自動偵測展開比例 / 轉軸狀態
    case standard = "STANDARD"         // 標準單螢幕橫向模式 (iPhone 15/16/17 Type-C)
    case duoVerticalDeck = "DUO_DECK"  // iPhone Duo 上下雙屏：上屏 100% 潔淨飛行視窗 + 下屏戰術地面站搖桿/儀表台
    case duoBookSplit = "DUO_BOOK"     // iPhone Duo 左右展開：左屏主視角 + 右屏戰術雷達/FPV/遙控艙
    case duoTentFlex = "DUO_TENT"      // iPhone Duo 半折立架模式 (90°~135° 外接 Type-C 實體遙控器專用)

    var id: String { rawValue }
}

/// [v1.8.01] 全域狀態中心 (Single Source of Truth - iOS & iPhone Duo Edition)
@Observable
final class DroneState {
    static let shared = DroneState()

    // --- 1. 飛行數據域 ---
    var altitude: Float = 0.08
    var posX: Float = 0.0
    var posZ: Float = 0.0
    var yaw: Float = 0.0
    var pitch: Float = 0.0
    var roll: Float = 0.0
    var speed: Float = 0.0
    var horizontalDist: Float = 0.0
    var batteryVoltage: Float = 4.2
    var batteryPercent: Int = 100
    var isCollision: Bool = false
    var isMotorLocked: Bool = true
    var motorRpmFactor: Float = 0.0
    var flightPath: [CGPoint] = []
    var currentRadarScale: Float = 1.0
    var crashReason: CrashReason = .none
    var sessionFlightTime: Float = 0.0
    var impactSpeedSnapshot: Float = 0.0

    // --- 2. 硬體與 Type-C 通訊域 ---
    var inputMode: Int = 0 // 0 = External USB-C HID / Controller, 1 = Internal / Virtual
    var connectionStatus: ConnectionStatus = .idle
    var controllerConnected: Bool = false
    var activeHidName: String = "NO_TYPE_C_CONTROLLER"
    var isTypeCHardwarePort: Bool = true
    var activeAxisLabel: String = "NONE"
    var packetsPerSecond: Int = 0
    var isSignalActive: Bool = false
    var isAutoConnectEnabled: Bool = AppConfig.SystemDefaults.AUTO_CONNECT_ENABLED
    var networkHost: String = "127.0.0.1"
    var networkPort: Int = 14550
    var isNetworkConnected: Bool = false

    // --- 3. 視覺導演域 ---
    var cameraMode: String = AppConfig.CAM_MODE_STATION_TRACK
    var mainFOV: Float = AppConfig.VisualDefaults.MAIN_FOV
    var zoomFactor: Float = AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING
    var cameraTilt: Float = 0.0
    var fovFixed: Float = AppConfig.VisualDefaults.DEFAULT_FIXED_FOV
    var fovTracking: Float = AppConfig.VisualDefaults.DEFAULT_TRACKING_FOV
    var fovSmart: Float = AppConfig.VisualDefaults.DEFAULT_SMART_FOV
    var showFpvBody: Bool = false
    var observerHeight: Float = AppConfig.VisualDefaults.OBSERVER_HEIGHT_TRACKING
    var observerTilt: Float = AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
    var enableZoomAssistant: Bool = AppConfig.VisualDefaults.ENABLE_ZOOM_ASSISTANT
    var showGroundAnchor: Bool = AppConfig.VisualDefaults.SHOW_GROUND_ANCHOR
    var lastManualTouchTime: TimeInterval = 0
    var droneScreenPos: CGPoint? = nil
    var radarZoomMode: Int = AppConfig.VisualDefaults.RADAR_ZOOM_MODE
    var hudMode: Int = AppConfig.VisualDefaults.HUD_MODE

    // --- 4. 安全保護域 ---
    var isThrottleHoldActive: Bool = AppConfig.SystemDefaults.IS_THROTTLE_HOLD_ACTIVE
    var lastInteractionTime: TimeInterval = Date().timeIntervalSince1970

    // --- 5. 環境配置域 ---
    var windLevel: Int = AppConfig.EnvironmentDefaults.WIND_LEVEL
    var windDirection: String = AppConfig.EnvironmentDefaults.WIND_DIRECTION
    var windVariation: Int = 0
    var windDirVariation: Int = 0
    var enableVerticalDraft: Bool = AppConfig.EnvironmentDefaults.ENABLE_VERTICAL_DRAFT
    var timeOfDay: String = AppConfig.TIME_NOON
    var isSunSimEnabled: Bool = AppConfig.EnvironmentDefaults.SUN_ENABLED
    var sunPosition: Float = AppConfig.EnvironmentDefaults.SUN_POSITION
    var useHardcorePhysics: Bool = AppConfig.EnvironmentDefaults.HARDCORE_PHYSICS
    var showClouds: Bool = AppConfig.EnvironmentDefaults.SHOW_CLOUDS
    var cloudDensity: Float = AppConfig.EnvironmentDefaults.CLOUD_DENSITY
    var showMountains: Bool = AppConfig.EnvironmentDefaults.SHOW_MOUNTAINS
    var weatherMode: Int = AppConfig.EnvironmentDefaults.WEATHER_MODE
    var cloudU: Float = 0.0
    var cloudV: Float = 0.0
    var currentWindAngle: Float = 0.0
    var randomWindAngle: Float = 0.0

    // --- 6. iPhone Duo 折疊特性域 ---
    var foldablePosture: FoldablePostureMode = .auto
    var hingeCreaseRatio: CGFloat = 0.52 // 展開時轉軸折痕位置比例
    var simulatedHingeAngle: Double = 180.0 // 180° 平展, 115° 帳篷/立架模式

    // --- 7. 全局與偏好設定 ---
    var droneType: String = "QUAD_STANDARD" {
        didSet {
            applyDroneGene(droneType)
        }
    }
    var joystickMode: Int = 2 // 預設美國手 Mode 2
    var isMuted: Bool = AppConfig.SystemDefaults.IS_MUTED
    var showShadow: Bool = AppConfig.SystemDefaults.SHOW_SHADOW
    var shadowIntensity: Float = AppConfig.EnvironmentDefaults.SHADOW_INTENSITY
    var appLanguage: String = "zh"
    var appTheme: String = AppConfig.THEME_CLASSIC
    var showObstacles: Bool = AppConfig.SystemDefaults.SHOW_OBSTACLES
    var hideStatusBar: Bool = AppConfig.SystemDefaults.HIDE_STATUS_BAR
    var pauseInSettings: Bool = AppConfig.SystemDefaults.PAUSE_IN_SETTINGS
    var applyPhysicalSpecs: Bool = AppConfig.SystemDefaults.APPLY_PHYSICAL_SPECS
    var useFlightLimit: Bool = AppConfig.SystemDefaults.USE_FLIGHT_LIMIT
    var useSimplifiedMarkers: Bool = false
    var showSpecialTitle: Bool = AppConfig.VisualDefaults.SHOW_SPECIAL_TITLE
    var settingsTab: SettingsTab = .controller
    var showSettings: Bool = false
    var useStrictLanding: Bool = AppConfig.SystemDefaults.USE_STRICT_LANDING
    var isTetherModeEnabled: Bool = false
    var enableGroundEffect: Bool = true
    var enableNonCenterForce: Bool = true
    var systemMessage: String? = nil
    var isHidPriorityEnabled: Bool = AppConfig.SystemDefaults.USE_HID_PRIORITY
    var useIdleWakeupPatch: Bool = AppConfig.SystemDefaults.ENABLE_IDLE_WAKEUP_PATCH
    var currentTitleText: String = ""

    var reverseSliderSides: Bool = AppConfig.VisualDefaults.REVERSE_SLIDERS
    var autoPiPRelocate: Bool = AppConfig.VisualDefaults.AUTO_PIP_RELOCATE
    var showSideRulers: Bool = AppConfig.VisualDefaults.SHOW_SIDE_RULERS
    var showSideSliders: Bool = AppConfig.VisualDefaults.SHOW_SIDE_SLIDERS
    var showVirtualJoysticks: Bool = true
    var isMenuExpanded: Bool = false
    var showFlightPath: Bool = false
    var showUpdateNotice: Bool = false
    var isNearBoundary: Bool = false

    // 定點計時評測器 (Spot Timer)
    var isSpotTimerEnabled: Bool = AppConfig.SystemDefaults.IS_SPOT_TIMER_ENABLED
    var spotTimerSeconds: Float = AppConfig.SystemDefaults.SPOT_TIMER_SECONDS
    var spotTimerTargetId: Int = -1
    var spotTimerMessage: String? = "IDLE"
    var spotTimerSuccess: Bool = false
    var spotTimerInZone: Bool = false
    var spotTimerStable: Bool = false
    var spotTimerLastYaw: Float = 0.0
    var spotTimerMessageTimer: Float = 0.0

    // 手感與通道映射 (Rates & Expo)
    var joystickDeadzone: Float = AppConfig.JoystickDefaults.DEADZONE
    var useGlobalRates: Bool = true
    var globalRate: Float = AppConfig.JoystickDefaults.RATE
    var globalExpo: Float = AppConfig.JoystickDefaults.EXPO
    var isExpertModeLocked: Bool = AppConfig.SystemDefaults.IS_EXPERT_MODE_LOCKED

    var rateT_Up: Float = 1.0
    var rateT_Down: Float = 1.0
    var rateY: Float = 1.0
    var rateP: Float = 1.0
    var rateR: Float = 1.0
    var expoT: Float = 0.0
    var expoY: Float = 0.0
    var expoP: Float = 0.0
    var expoR: Float = 0.0

    // 標準 USB-C HID 預設軸位映射 (0=LX, 1=LY, 2=RY, 3=RX)
    var mappingLY = ChannelMapping(axis: 1, inverted: false, label: "AXIS_LY")
    var mappingLX = ChannelMapping(axis: 0, inverted: false, label: "AXIS_LX")
    var mappingRY = ChannelMapping(axis: 2, inverted: false, label: "AXIS_RY")
    var mappingRX = ChannelMapping(axis: 3, inverted: false, label: "AXIS_RX")

    init() {
        applyDroneGene(droneType)
        ConfigurationStore.shared.loadSettings(self)
    }

    func applyDroneGene(_ id: String) {
        let mod = DroneRegistry.getModule(id)
        rateT_Up = mod.baseRateT_Up
        rateT_Down = mod.baseRateT_Down
        rateY = mod.baseRateY
        rateP = mod.baseRateP
        rateR = mod.baseRateR
        expoT = mod.baseExpo
        expoY = mod.baseExpo
        expoP = mod.baseExpo
        expoR = mod.baseExpo
    }

    func getMappingSnapshot() -> [ChannelMapping] {
        [mappingLY, mappingLX, mappingRY, mappingRX]
    }

    func getRate(_ key: String, stickValue: Float = 0.0) -> Float {
        let userRate = globalRate
        let geneRate: Float
        switch key {
        case "T": geneRate = stickValue >= 0 ? rateT_Up : rateT_Down
        case "Y": geneRate = rateY
        case "P": geneRate = rateP
        case "R": geneRate = rateR
        default: geneRate = 1.0
        }
        return userRate * geneRate
    }

    func getExpo(_ key: String) -> Float {
        let userExpo = globalExpo
        let geneExpo: Float
        switch key {
        case "T": geneExpo = expoT
        case "Y": geneExpo = expoY
        case "P": geneExpo = expoP
        case "R": geneExpo = expoR
        default: geneExpo = 0.0
        }
        return min(max(userExpo + geneExpo, 0.0), 1.0)
    }
}
