import Foundation

/// [v1.8.01] 全系統配置持久化存儲庫 (1:1 對齊 ConfigurationStore.kt)
final class ConfigurationStore {
    static let shared = ConfigurationStore()
    private let defaults = UserDefaults.standard

    private init() {}

    func saveSettings(_ state: DroneState) {
        defaults.set(state.joystickMode, forKey: "joystickMode")
        defaults.set(state.halfThrottle, forKey: "halfThrottle")
        defaults.set(state.droneType, forKey: "droneType")
        defaults.set(state.joystickDeadzone, forKey: "joystickDeadzone")
        defaults.set(state.isMuted, forKey: "isMuted")
        defaults.set(state.enableVerticalDraft, forKey: "enableVerticalDraft")
        defaults.set(state.showShadow, forKey: "showShadow")
        defaults.set(state.shadowIntensity, forKey: "shadowIntensity")
        defaults.set(state.useFlightLimit, forKey: "useFlightLimit")
        defaults.set(state.enableZoomAssistant, forKey: "enableZoomAssistant")
        defaults.set(state.mainFOV, forKey: "mainFOV")
        defaults.set(state.cameraTilt, forKey: "cameraTilt")
        defaults.set(state.showGroundAnchor, forKey: "showGroundAnchor")
        defaults.set(state.zoomFactor, forKey: "zoomFactor")
        defaults.set(state.observerHeight, forKey: "observerHeight")
        defaults.set(state.observerTilt, forKey: "observerTilt")
        defaults.set(state.useGlobalRates, forKey: "useGlobalRates")
        defaults.set(state.globalRate, forKey: "globalRate")
        defaults.set(state.globalExpo, forKey: "globalExpo")
        defaults.set(state.rateT_Up, forKey: "rateT_Up")
        defaults.set(state.rateT_Down, forKey: "rateT_Down")
        defaults.set(state.rateY, forKey: "rateY")
        defaults.set(state.rateP, forKey: "rateP")
        defaults.set(state.rateR, forKey: "rateR")
        defaults.set(state.expoT, forKey: "expoT")
        defaults.set(state.expoY, forKey: "expoY")
        defaults.set(state.expoP, forKey: "expoP")
        defaults.set(state.expoR, forKey: "expoR")
        defaults.set(state.radarZoomMode, forKey: "radarZoomMode")
        defaults.set(state.showVirtualJoysticks, forKey: "showVirtualJoysticks")
        defaults.set(state.showFlightPath, forKey: "showFlightPath")
        defaults.set(state.windLevel, forKey: "windLevel")
        defaults.set(state.windVariation, forKey: "windVariation")
        defaults.set(state.windDirVariation, forKey: "windDirVariation")
        defaults.set(state.cameraMode, forKey: "cameraMode")
        defaults.set(state.windDirection, forKey: "windDirection")
        defaults.set(state.timeOfDay, forKey: "timeOfDay")
        defaults.set(state.weatherMode, forKey: "weatherMode")
        defaults.set(state.cloudDensity, forKey: "cloudDensity")
        defaults.set(state.useHardcorePhysics, forKey: "useHardcorePhysics")
        defaults.set(state.applyPhysicalSpecs, forKey: "applyPhysicalSpecs")
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
        defaults.set(state.isSpotTimerEnabled, forKey: "isSpotTimerEnabled")
        if let encoded = try? JSONEncoder().encode([state.mappingLY, state.mappingLX, state.mappingRY, state.mappingRX]) {
            defaults.set(encoded, forKey: "channelMappings")
        }
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
        state.halfThrottle = defaults.bool(forKey: "halfThrottle")
        if let dt = defaults.string(forKey: "droneType") { state.droneType = dt }
        state.joystickDeadzone = defaults.object(forKey: "joystickDeadzone") as? Float ?? AppConfig.JoystickDefaults.DEADZONE
        state.isMuted = defaults.bool(forKey: "isMuted")
        state.enableVerticalDraft = defaults.bool(forKey: "enableVerticalDraft")
        state.showShadow = defaults.object(forKey: "showShadow") as? Bool ?? true
        state.shadowIntensity = defaults.object(forKey: "shadowIntensity") as? Float ?? AppConfig.EnvironmentDefaults.SHADOW_INTENSITY
        state.useFlightLimit = defaults.object(forKey: "useFlightLimit") as? Bool ?? true
        state.enableZoomAssistant = defaults.object(forKey: "enableZoomAssistant") as? Bool ?? true
        state.mainFOV = defaults.object(forKey: "mainFOV") as? Float ?? AppConfig.VisualDefaults.MAIN_FOV
        state.cameraTilt = defaults.object(forKey: "cameraTilt") as? Float ?? 0.0
        state.showGroundAnchor = defaults.object(forKey: "showGroundAnchor") as? Bool ?? true
        state.zoomFactor = defaults.object(forKey: "zoomFactor") as? Float ?? AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING
        state.observerHeight = defaults.object(forKey: "observerHeight") as? Float ?? AppConfig.VisualDefaults.OBSERVER_HEIGHT_TRACKING
        state.observerTilt = defaults.object(forKey: "observerTilt") as? Float ?? AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
        state.useGlobalRates = defaults.object(forKey: "useGlobalRates") as? Bool ?? true
        state.globalRate = defaults.object(forKey: "globalRate") as? Float ?? AppConfig.JoystickDefaults.RATE
        state.globalExpo = defaults.object(forKey: "globalExpo") as? Float ?? AppConfig.JoystickDefaults.EXPO
        if let rTU = defaults.object(forKey: "rateT_Up") as? Float { state.rateT_Up = rTU }
        if let rTD = defaults.object(forKey: "rateT_Down") as? Float { state.rateT_Down = rTD }
        if let rY = defaults.object(forKey: "rateY") as? Float { state.rateY = rY }
        if let rP = defaults.object(forKey: "rateP") as? Float { state.rateP = rP }
        if let rR = defaults.object(forKey: "rateR") as? Float { state.rateR = rR }
        if let eT = defaults.object(forKey: "expoT") as? Float { state.expoT = eT }
        if let eY = defaults.object(forKey: "expoY") as? Float { state.expoY = eY }
        if let eP = defaults.object(forKey: "expoP") as? Float { state.expoP = eP }
        if let eR = defaults.object(forKey: "expoR") as? Float { state.expoR = eR }
        state.radarZoomMode = defaults.object(forKey: "radarZoomMode") as? Int ?? AppConfig.VisualDefaults.RADAR_ZOOM_MODE
        state.showVirtualJoysticks = defaults.object(forKey: "showVirtualJoysticks") as? Bool ?? true
        state.showFlightPath = defaults.bool(forKey: "showFlightPath")
        state.windLevel = defaults.integer(forKey: "windLevel")
        state.windVariation = defaults.integer(forKey: "windVariation")
        state.windDirVariation = defaults.integer(forKey: "windDirVariation")
        if let cm = defaults.string(forKey: "cameraMode") { state.cameraMode = cm }
        if let wd = defaults.string(forKey: "windDirection") { state.windDirection = wd }
        if let tod = defaults.string(forKey: "timeOfDay") { state.timeOfDay = tod }
        state.weatherMode = defaults.object(forKey: "weatherMode") as? Int ?? AppConfig.EnvironmentDefaults.WEATHER_MODE
        state.cloudDensity = defaults.object(forKey: "cloudDensity") as? Float ?? AppConfig.EnvironmentDefaults.CLOUD_DENSITY
        state.useHardcorePhysics = defaults.object(forKey: "useHardcorePhysics") as? Bool ?? true
        state.applyPhysicalSpecs = defaults.object(forKey: "applyPhysicalSpecs") as? Bool ?? true
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
        state.isSpotTimerEnabled = defaults.object(forKey: "isSpotTimerEnabled") as? Bool ?? true
        if let data = defaults.data(forKey: "channelMappings"),
           let mappings = try? JSONDecoder().decode([ChannelMapping].self, from: data),
           mappings.count == 4 {
            state.mappingLY = mappings[0]
            state.mappingLX = mappings[1]
            state.mappingRY = mappings[2]
            state.mappingRX = mappings[3]
        }
    }

    func wipeAllSettings(_ state: DroneState) {
        if let bundleID = Bundle.main.bundleIdentifier {
            defaults.removePersistentDomain(forName: bundleID)
        }
        saveSettings(state)
    }
}
