import Foundation

/// [v1.8.01] 全系統配置持久化存儲庫 (1:1 對齊 ConfigurationStore.kt)
final class ConfigurationStore {
    static let shared = ConfigurationStore()
    private let defaults = UserDefaults.standard

    private init() {}

    func saveSettings(_ state: DroneState) {
        defaults.set(state.joystickMode, forKey: "joystickMode")
        defaults.set(state.droneType, forKey: "droneType")
        defaults.set(state.joystickDeadzone, forKey: "joystickDeadzone")
        defaults.set(state.isMuted, forKey: "isMuted")
        defaults.set(state.enableVerticalDraft, forKey: "enableVerticalDraft")
        defaults.set(state.showShadow, forKey: "showShadow")
        defaults.set(state.useFlightLimit, forKey: "useFlightLimit")
        defaults.set(state.enableZoomAssistant, forKey: "enableZoomAssistant")
        defaults.set(state.mainFOV, forKey: "mainFOV")
        defaults.set(state.showGroundAnchor, forKey: "showGroundAnchor")
        defaults.set(state.zoomFactor, forKey: "zoomFactor")
        defaults.set(state.observerHeight, forKey: "observerHeight")
        defaults.set(state.observerTilt, forKey: "observerTilt")
        defaults.set(state.globalRate, forKey: "globalRate")
        defaults.set(state.globalExpo, forKey: "globalExpo")
        defaults.set(state.radarZoomMode, forKey: "radarZoomMode")
        defaults.set(state.showVirtualJoysticks, forKey: "showVirtualJoysticks")
        defaults.set(state.windLevel, forKey: "windLevel")
        defaults.set(state.cameraMode, forKey: "cameraMode")
        defaults.set(state.windDirection, forKey: "windDirection")
        defaults.set(state.useHardcorePhysics, forKey: "useHardcorePhysics")
        defaults.set(state.isSunSimEnabled, forKey: "isSunSimEnabled")
        defaults.set(state.sunPosition, forKey: "sunPosition")
        defaults.set(state.showClouds, forKey: "showClouds")
        defaults.set(state.showMountains, forKey: "showMountains")
        defaults.set(state.useStrictLanding, forKey: "useStrictLanding")
        defaults.set(state.useIdleWakeupPatch, forKey: "useIdleWakeupPatch")
        defaults.set(state.appLanguage, forKey: "appLanguage")
        defaults.set(state.appTheme, forKey: "appTheme")
        defaults.set(state.foldablePosture.rawValue, forKey: "foldablePosture")
        defaults.set(state.isTetherModeEnabled, forKey: "isTetherModeEnabled")
        defaults.set(state.enableGroundEffect, forKey: "enableGroundEffect")
        defaults.set(state.enableNonCenterForce, forKey: "enableNonCenterForce")
        defaults.set(AppConfig.CURRENT_VERSION, forKey: "lastSeenVersion")
    }

    func loadSettings(_ state: DroneState) {
        guard defaults.string(forKey: "lastSeenVersion") != nil else {
            let sysLang = Locale.preferredLanguages.first ?? "zh"
            state.appLanguage = sysLang.hasPrefix("zh") ? "zh" : (sysLang.hasPrefix("ja") ? "ja" : "en")
            saveSettings(state)
            return
        }

        state.joystickMode = defaults.integer(forKey: "joystickMode") != 0 ? defaults.integer(forKey: "joystickMode") : 2
        if let dt = defaults.string(forKey: "droneType") { state.droneType = dt }
        state.joystickDeadzone = defaults.object(forKey: "joystickDeadzone") as? Float ?? AppConfig.JoystickDefaults.DEADZONE
        state.isMuted = defaults.bool(forKey: "isMuted")
        state.enableVerticalDraft = defaults.bool(forKey: "enableVerticalDraft")
        state.showShadow = defaults.object(forKey: "showShadow") as? Bool ?? true
        state.useFlightLimit = defaults.object(forKey: "useFlightLimit") as? Bool ?? true
        state.enableZoomAssistant = defaults.object(forKey: "enableZoomAssistant") as? Bool ?? true
        state.mainFOV = defaults.object(forKey: "mainFOV") as? Float ?? AppConfig.VisualDefaults.MAIN_FOV
        state.showGroundAnchor = defaults.object(forKey: "showGroundAnchor") as? Bool ?? true
        state.zoomFactor = defaults.object(forKey: "zoomFactor") as? Float ?? AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING
        state.observerHeight = defaults.object(forKey: "observerHeight") as? Float ?? AppConfig.VisualDefaults.OBSERVER_HEIGHT_TRACKING
        state.observerTilt = defaults.object(forKey: "observerTilt") as? Float ?? AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
        state.globalRate = defaults.object(forKey: "globalRate") as? Float ?? AppConfig.JoystickDefaults.RATE
        state.globalExpo = defaults.object(forKey: "globalExpo") as? Float ?? AppConfig.JoystickDefaults.EXPO
        state.radarZoomMode = defaults.object(forKey: "radarZoomMode") as? Int ?? AppConfig.VisualDefaults.RADAR_ZOOM_MODE
        state.showVirtualJoysticks = defaults.object(forKey: "showVirtualJoysticks") as? Bool ?? true
        state.windLevel = defaults.integer(forKey: "windLevel")
        if let cm = defaults.string(forKey: "cameraMode") { state.cameraMode = cm }
        if let wd = defaults.string(forKey: "windDirection") { state.windDirection = wd }
        state.useHardcorePhysics = defaults.object(forKey: "useHardcorePhysics") as? Bool ?? true
        state.isSunSimEnabled = defaults.object(forKey: "isSunSimEnabled") as? Bool ?? true
        state.sunPosition = defaults.object(forKey: "sunPosition") as? Float ?? 0.5
        state.showClouds = defaults.object(forKey: "showClouds") as? Bool ?? true
        state.showMountains = defaults.object(forKey: "showMountains") as? Bool ?? true
        state.useStrictLanding = defaults.object(forKey: "useStrictLanding") as? Bool ?? true
        state.useIdleWakeupPatch = defaults.object(forKey: "useIdleWakeupPatch") as? Bool ?? true
        if let lang = defaults.string(forKey: "appLanguage") { state.appLanguage = lang }
        if let thm = defaults.string(forKey: "appTheme") { state.appTheme = thm }
        if let fp = defaults.string(forKey: "foldablePosture"), let mode = FoldablePostureMode(rawValue: fp) {
            state.foldablePosture = mode
        }
        state.isTetherModeEnabled = defaults.bool(forKey: "isTetherModeEnabled")
        state.enableGroundEffect = defaults.object(forKey: "enableGroundEffect") as? Bool ?? true
        state.enableNonCenterForce = defaults.object(forKey: "enableNonCenterForce") as? Bool ?? true
    }

    func wipeAllSettings(_ state: DroneState) {
        if let bundleID = Bundle.main.bundleIdentifier {
            defaults.removePersistentDomain(forName: bundleID)
        }
        saveSettings(state)
    }
}
