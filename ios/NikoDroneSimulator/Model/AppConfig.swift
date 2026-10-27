import Foundation

/// [v1.8.01] 全域應用程式配置 (憲法級預設值基準 - iOS / iPhone Duo 版)
enum AppConfig {
    static let CURRENT_VERSION = "1.8.01"
    static let RELEASE_DATE = "2026-10-26"
    static let DEVELOPER = "Enoch Wang"

    static let SPECIAL_TITLE_ZH = "NikoNiko考照場地模擬器"
    static let SPECIAL_TITLE_EN = "NikoNiko Drone Licensing Simulator"
    static let SPECIAL_TITLE_JA = "ニコニコドローン考照シミュレーター"

    static var isProVersion = true

    static func getDefaultSpecialTitle(lang: String) -> String {
        switch lang {
        case "zh": return SPECIAL_TITLE_ZH
        case "ja": return SPECIAL_TITLE_JA
        default: return SPECIAL_TITLE_EN
        }
    }

    // 攝影機模式內部 ID
    static let CAM_MODE_STATION_TRACK = "STATION_TRACK"
    static let CAM_MODE_STATION_SMART = "STATION_SMART"
    static let CAM_MODE_STATION_FIXED = "STATION_FIXED"
    static let CAM_MODE_FOLLOW = "FOLLOW"
    static let CAM_MODE_FPV = "FPV"
    static let CAM_MODE_OBS = "OBSERVER"

    // 風向內部 ID
    static let WIND_DIR_NONE = "NONE"
    static let WIND_DIR_N = "N"
    static let WIND_DIR_NE = "NE"
    static let WIND_DIR_E = "E"
    static let WIND_DIR_SE = "SE"
    static let WIND_DIR_S = "S"
    static let WIND_DIR_SW = "SW"
    static let WIND_DIR_W = "W"
    static let WIND_DIR_NW = "NW"
    static let WIND_DIR_RANDOM = "RANDOM"

    // 時間內部 ID
    static let TIME_MORNING = "MORNING"
    static let TIME_NOON = "NOON"
    static let TIME_AFTERNOON = "AFTERNOON"

    // 主題內部 ID
    static let THEME_CLASSIC = "CLASSIC"
    static let THEME_LIGHT = "LIGHT"

    enum JoystickDefaults {
        static let RATE: Float = 1.2
        static let EXPO: Float = 0.4
        static let DEADZONE: Float = 0.05
    }

    enum EnvironmentDefaults {
        static let WIND_LEVEL: Int = 0
        static let WIND_DIRECTION: String = WIND_DIR_NONE
        static let SUN_ENABLED: Bool = true
        static let SUN_POSITION: Float = 0.5
        static let SHADOW_INTENSITY: Float = 1.0
        static let CLOUD_DENSITY: Float = 0.5
        static let WEATHER_MODE: Int = 2
        static let SHOW_CLOUDS: Bool = true
        static let SHOW_MOUNTAINS: Bool = true
        static let ENABLE_VERTICAL_DRAFT: Bool = false
        static let HARDCORE_PHYSICS: Bool = true
    }

    enum VisualDefaults {
        static let MAIN_FOV: Float = 45.0
        static let DEFAULT_FPV_FOV: Float = 85.0
        static let DEFAULT_FIXED_FOV: Float = 45.0
        static let OBSERVER_HEIGHT: Float = 1.6
        static let OBSERVER_TILT: Float = 32.0
        static let ZOOM_FACTOR_FIXED: Float = 0.5

        static let DEFAULT_TRACKING_FOV: Float = 45.0
        static let OBSERVER_HEIGHT_TRACKING: Float = 6.0
        static let OBSERVER_TILT_TRACKING: Float = 0.0
        static let ZOOM_FACTOR_TRACKING: Float = 1.3

        static let DEFAULT_SMART_FOV: Float = 45.0
        static let OBSERVER_HEIGHT_SMART: Float = 6.0
        static let OBSERVER_TILT_SMART: Float = -4.0
        static let ZOOM_FACTOR_SMART: Float = 1.2

        static let SHOW_SPECIAL_TITLE: Bool = true
        static let SHOW_SIDE_SLIDERS: Bool = true
        static let SHOW_SIDE_RULERS: Bool = true
        static let REVERSE_SLIDERS: Bool = true
        static let SHOW_GROUND_ANCHOR: Bool = true
        static let AUTO_PIP_RELOCATE: Bool = true
        static let ENABLE_ZOOM_ASSISTANT: Bool = true
        static let ZOOM_ASSISTANT_DISTANCE: Float = 8.0
        static let SHOW_FLIGHT_PATH: Bool = false
        static let HUD_MODE: Int = 0
        static let RADAR_ZOOM_MODE: Int = 1

        static let ZOOM_STEPS: [Float] = [0.5, 1.0, 1.3, 1.5, 2.0, 3.0]
    }

    enum AudioDefaults {
        static let MASTER_MOTOR_VOLUME: Float = 0.8
        static let MASTER_ENV_VOLUME: Float = 0.5
    }

    enum SystemDefaults {
        static let USE_FLIGHT_LIMIT: Bool = true
        static let MAX_ALTITUDE: Float = 30.0
        static let FIELD_WIDTH_HALF: Float = 50.0
        static let FIELD_Z_FRONT: Float = 46.0
        static let FIELD_Z_BACK: Float = -9.0
        static let WARNING_BUFFER: Float = 5.0

        static let USE_STRICT_LANDING: Bool = true
        static let HIDE_STATUS_BAR: Bool = true
        static let PAUSE_IN_SETTINGS: Bool = true
        static let AUTO_CONNECT_ENABLED: Bool = true
        static let APPLY_PHYSICAL_SPECS: Bool = true
        static let IS_THROTTLE_HOLD_ACTIVE: Bool = true
        static let IS_MUTED: Bool = false
        static let SHOW_SHADOW: Bool = true
        static let SHOW_OBSTACLES: Bool = true

        static let IMPACT_THRESHOLD_SAFE: Float = 2.2
        static let IMPACT_THRESHOLD_CRITICAL: Float = 3.5

        static let USE_HID_PRIORITY: Bool = true
        static let ENABLE_IDLE_WAKEUP_PATCH: Bool = true

        static let ADMIN_PASSWORD: String = "12345678"
        static let IS_EXPERT_MODE_LOCKED: Bool = false

        static let IS_SPOT_TIMER_ENABLED: Bool = false
        static let SPOT_TIMER_SECONDS: Float = 5.0

        static let TETHER_MAX_ALTITUDE: Float = 3.5
        static let TETHER_RADIUS_LIMIT: Float = 2.0
        static let TETHER_CENTER_Z: Float = 0.0
    }
}
