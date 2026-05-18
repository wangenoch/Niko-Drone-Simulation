package com.horizon.caadronesimulator.logic.storage

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.horizon.caadronesimulator.logic.DeviceProfileManager
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.ChannelMapping
import com.horizon.caadronesimulator.model.DroneState

/**
 * [v1.6.1] 全系統配置存儲庫 (Encapsulated Security Version)
 * 職責：落實數據隱藏 (Data Hiding)，新增「記憶抹除 (Wipe)」功能。
 */
class ConfigurationStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("drone_settings", Context.MODE_PRIVATE)
    private val ax12v1Prefs: SharedPreferences = context.getSharedPreferences("ax12_v1_settings", Context.MODE_PRIVATE)
    private val ax12v2Prefs: SharedPreferences = context.getSharedPreferences("ax12_v2_settings", Context.MODE_PRIVATE)
    private val genericExternalPrefs: SharedPreferences = context.getSharedPreferences("external_settings", Context.MODE_PRIVATE)

    /** [v1.6.3] 徹底抹除所有設定檔 (恢復原廠設定專用) */
    fun wipeAllSettings() {
        prefs.edit().clear().apply()
        ax12v1Prefs.edit().clear().apply()
        ax12v2Prefs.edit().clear().apply()
        genericExternalPrefs.edit().clear().apply()
    }

    fun isFirstLaunch(): Boolean = !prefs.contains("lastSeenVersion")

    /** 保存全域設定 */
    fun saveSettings(state: DroneState) {
        prefs.edit().apply {
            putInt("joystickMode", state.joystickMode); putInt("inputMode", state.inputMode); putString("droneType", state.droneType)
            putBoolean("halfThrottle", state.halfThrottle); putFloat("joystickDeadzone", state.joystickDeadzone); putBoolean("isMuted", state.isMuted)
            putBoolean("enableVerticalDraft", state.enableVerticalDraft); putBoolean("showShadow", state.showShadow); putBoolean("showTutorial", state.showTutorial)
            putBoolean("useSimplifiedMarkers", state.useSimplifiedMarkers); putBoolean("showSpecialTitle", state.showSpecialTitle); putString("customTitle", state.customTitle)
            putBoolean("useFlightLimit", state.useFlightLimit); putBoolean("enableZoomAssistant", state.enableZoomAssistant); putFloat("mainFOV", state.mainFOV)
            putBoolean("showSideRulers", state.showSideRulers); putBoolean("showGroundAnchor", state.showGroundAnchor); putBoolean("autoPiPRelocate", state.autoPiPRelocate)
            putBoolean("hasShownJoystickTutorial", state.hasShownJoystickTutorial); putBoolean("hasShownClimateTutorial", state.hasShownClimateTutorial); putBoolean("isMappingUnlocked", state.isMappingUnlocked)
            putBoolean("isExpertModeLocked", state.isExpertModeLocked); putFloat("zoomFactor", state.zoomFactor); putString("lockedProtocol", state.lockedProtocol)
            putString("lastSeenVersion", AppConfig.CURRENT_VERSION); putBoolean("reverseSliderSides", state.reverseSliderSides); putFloat("observerHeight", state.observerHeight)
            putFloat("observerTilt", state.observerTilt); putBoolean("useGlobalRates", state.useGlobalRates); putBoolean("showIndividualRates", state.showIndividualRates)
            putFloat("globalRate", state.globalRate); putFloat("globalExpo", state.globalExpo); putInt("radarZoomMode", state.radarZoomMode)
            putBoolean("showVirtualJoysticks", state.showVirtualJoysticks); putInt("baudRate", state.baudRate); putInt("windLevel", state.windLevel)
            putString("windDirection", state.windDirection); putInt("windVariation", state.windVariation); putInt("windDirVariation", state.windDirVariation)
            putString("timeOfDay", state.timeOfDay); putFloat("shadowIntensity", state.shadowIntensity); putBoolean("useHardcorePhysics", state.useHardcorePhysics)
            putBoolean("isSunSimEnabled", state.isSunSimEnabled); putFloat("sunPosition", state.sunPosition)
            putBoolean("showClouds", state.showClouds); putFloat("cloudDensity", state.cloudDensity); putBoolean("showMountains", state.showMountains)
            putBoolean("useStrictLanding", state.useStrictLanding); putBoolean("optimizationPromptIgnored", state.optimizationPromptIgnored)
            apply()
        }
        
        val targetPrefs = if (state.inputMode == 1) {
            val proto = state.hardware.detectedProtocol
            if (proto.contains("V2")) ax12v2Prefs else ax12v1Prefs
        } else {
            val fingerprint = DeviceProfileManager.getActiveHidFingerprint(context)
            context.getSharedPreferences(fingerprint, Context.MODE_PRIVATE)
        }

        targetPrefs.edit().apply {
            saveMapping("ly", state.mappingLY); saveMapping("lx", state.mappingLX); saveMapping("ry", state.mappingRY); saveMapping("rx", state.mappingRX)
            saveMapping("hold", state.mappingHold); saveMapping("arm", state.mappingArm); saveMapping("obsHeight", state.mappingObsHeight); saveMapping("obsTilt", state.mappingObsTilt); saveMapping("fpvTilt", state.mappingFpvTilt); saveMapping("flightMode", state.mappingFlightMode)
            
            putFloat("rateT", state.rateT); putFloat("expoT", state.expoT)
            putFloat("rateY", state.rateY); putFloat("expoY", state.expoY)
            putFloat("rateP", state.rateP); putFloat("expoP", state.expoP)
            putFloat("rateR", state.rateR); putFloat("expoR", state.expoR)
            commit()
        }
    }

    private fun SharedPreferences.Editor.saveMapping(key: String, mapping: ChannelMapping) {
        putInt("${key}_axis", mapping.axis); putBoolean("${key}_inverted", mapping.inverted)
        putString("${key}_label", mapping.label); putFloat("${key}_min", mapping.min)
        putFloat("${key}_max", mapping.max); putFloat("${key}_center", mapping.center)
    }

    /** 載入全域設定 */
    fun loadSettings(state: DroneState): DroneState {
        if (isFirstLaunch()) performInitialSetup(state)
        val loadedProtocol = prefs.getString("lockedProtocol", state.lockedProtocol) ?: state.lockedProtocol
        val inputMode = if (state.inputMode != -1) state.inputMode else prefs.getInt("inputMode", 0)
        
        val targetPrefs = if (inputMode == 1) {
            val proto = state.hardware.detectedProtocol
            if (proto.contains("V2")) ax12v2Prefs else ax12v1Prefs
        } else {
            val fingerprint = DeviceProfileManager.getActiveHidFingerprint(context)
            val profile = context.getSharedPreferences(fingerprint, Context.MODE_PRIVATE)
            if (!profile.contains("ly_axis") && fingerprint != "external_settings") {
                profile.edit().apply {
                    listOf("ly", "lx", "ry", "rx", "hold", "arm", "obsHeight", "obsTilt", "fpvTilt", "flightMode").forEach { key ->
                        putInt("${key}_axis", genericExternalPrefs.getInt("${key}_axis", -1)); putBoolean("${key}_inverted", genericExternalPrefs.getBoolean("${key}_inverted", false))
                        putString("${key}_label", genericExternalPrefs.getString("${key}_label", "未設定")); putFloat("${key}_min", genericExternalPrefs.getFloat("${key}_min", -1f))
                        putFloat("${key}_max", genericExternalPrefs.getFloat("${key}_max", 1f)); putFloat("${key}_center", genericExternalPrefs.getFloat("${key}_center", 0f))
                    }
                    apply()
                }
            }
            profile
        }
        
        state.apply {
            this.joystickMode = prefs.getInt("joystickMode", 2); this.inputMode = inputMode; this.droneType = prefs.getString("droneType", "QUAD_STANDARD") ?: "QUAD_STANDARD"
            this.halfThrottle = prefs.getBoolean("halfThrottle", false); this.joystickDeadzone = prefs.getFloat("joystickDeadzone", 0.05f)
            this.isMuted = prefs.getBoolean("isMuted", false); this.enableVerticalDraft = prefs.getBoolean("enableVerticalDraft", false)
            this.showShadow = prefs.getBoolean("showShadow", true); this.showTutorial = prefs.getBoolean("showTutorial", true)
            this.useSimplifiedMarkers = prefs.getBoolean("useSimplifiedMarkers", true); this.showSpecialTitle = prefs.getBoolean("showSpecialTitle", true)
            this.customTitle = prefs.getString("customTitle", "") ?: ""; this.useFlightLimit = prefs.getBoolean("useFlightLimit", true)
            this.enableZoomAssistant = prefs.getBoolean("enableZoomAssistant", true); this.mainFOV = prefs.getFloat("mainFOV", 45f)
            this.showSideRulers = prefs.getBoolean("showSideRulers", true); this.showGroundAnchor = prefs.getBoolean("showGroundAnchor", false)
            this.autoPiPRelocate = prefs.getBoolean("autoPiPRelocate", true); this.hasShownJoystickTutorial = prefs.getBoolean("hasShownJoystickTutorial", false)
            this.hasShownClimateTutorial = prefs.getBoolean("hasShownClimateTutorial", false); this.isMappingUnlocked = prefs.getBoolean("isMappingUnlocked", false)
            this.isExpertModeLocked = prefs.getBoolean("isExpertModeLocked", false); this.zoomFactor = prefs.getFloat("zoomFactor", 1.5f)
            this.lockedProtocol = loadedProtocol; this.showVirtualJoysticks = prefs.getBoolean("showVirtualJoysticks", false)
            this.reverseSliderSides = prefs.getBoolean("reverseSliderSides", true); this.observerHeight = prefs.getFloat("observerHeight", 6.0f)
            this.observerTilt = prefs.getFloat("observerTilt", 0f); this.shadowIntensity = prefs.getFloat("shadowIntensity", 0.5f)
            this.windLevel = prefs.getInt("windLevel", 0); this.windDirection = prefs.getString("windDirection", "無") ?: "無"
            this.windVariation = prefs.getInt("windVariation", 0); this.windDirVariation = prefs.getInt("windDirVariation", 0)
            this.timeOfDay = prefs.getString("timeOfDay", "中午") ?: "中午"; this.useHardcorePhysics = prefs.getBoolean("useHardcorePhysics", false)
            this.isSunSimEnabled = prefs.getBoolean("isSunSimEnabled", false); this.sunPosition = prefs.getFloat("sunPosition", 0.5f)
            this.showClouds = prefs.getBoolean("showClouds", true); this.cloudDensity = prefs.getFloat("cloudDensity", 0.5f); this.showMountains = prefs.getBoolean("showMountains", true)
            this.useStrictLanding = prefs.getBoolean("useStrictLanding", true); this.optimizationPromptIgnored = prefs.getBoolean("optimizationPromptIgnored", false)
            
            this.rateT = targetPrefs.getFloat("rateT", 1.0f)
            this.rateY = targetPrefs.getFloat("rateY", 1.0f); this.rateP = targetPrefs.getFloat("rateP", 1.0f); this.rateR = targetPrefs.getFloat("rateR", 1.0f)
            this.expoT = targetPrefs.getFloat("expoT", 0.0f); this.expoY = targetPrefs.getFloat("expoY", 0.0f); this.expoP = targetPrefs.getFloat("expoP", 0.0f); this.expoR = targetPrefs.getFloat("expoR", 0.0f)
            
            loadModelSettings(this.droneType, this)
            this.isSettingsLoaded = true
        }
        return state
    }

    private fun performInitialSetup(state: DroneState) {
        val hasTouchScreen = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        state.apply { 
            if (hasTouchScreen) { 
                showVirtualJoysticks = true
                globalRate = AppConfig.JoystickDefaults.RATE
                globalExpo = AppConfig.JoystickDefaults.EXPO
                joystickDeadzone = AppConfig.JoystickDefaults.DEADZONE
            }
            
            // [v1.6.1] 初始安裝強制同步 AppConfig
            useFlightLimit = AppConfig.SystemDefaults.USE_FLIGHT_LIMIT
            useStrictLanding = AppConfig.SystemDefaults.USE_STRICT_LANDING
            isMuted = AppConfig.SystemDefaults.IS_MUTED
            applyPhysicalSpecs = AppConfig.SystemDefaults.APPLY_PHYSICAL_SPECS
            isExpertModeLocked = AppConfig.SystemDefaults.IS_EXPERT_MODE_LOCKED
            
            // 環境初始化
            isSunSimEnabled = AppConfig.EnvironmentDefaults.SUN_ENABLED
            sunPosition = AppConfig.EnvironmentDefaults.SUN_POSITION
            weatherMode = AppConfig.EnvironmentDefaults.WEATHER_MODE
            
            showTutorial = true
        }; saveSettings(state)
    }

    private fun loadMapping(p: SharedPreferences, key: String, default: ChannelMapping): ChannelMapping {
        val axis = p.getInt("${key}_axis", default.axis); if (axis == -1) return default
        return ChannelMapping(axis = axis, inverted = p.getBoolean("${key}_inverted", default.inverted), label = p.getString("${key}_label", default.label) ?: default.label, min = p.getFloat("${key}_min", default.min), max = p.getFloat("${key}_max", default.max), center = p.getFloat("${key}_center", default.center))
    }

    fun saveModelSettings(droneId: String, state: DroneState) {
        val modelPrefs = context.getSharedPreferences("model_settings_$droneId", Context.MODE_PRIVATE)
        modelPrefs.edit().apply {
            putFloat("rateT_Up", state.modelGene.rateT_Up); putFloat("rateT_Down", state.modelGene.rateT_Down); putFloat("expoT", state.modelGene.expoT)
            putFloat("rateY", state.modelGene.rateY); putFloat("expoY", state.modelGene.expoY)
            putFloat("rateP", state.modelGene.rateP); putFloat("expoP", state.modelGene.expoP)
            putFloat("rateR", state.modelGene.rateR); putFloat("expoR", state.modelGene.expoR)
            apply()
        }
    }

    fun loadModelSettings(droneId: String, state: DroneState) {
        val modelPrefs = context.getSharedPreferences("model_settings_$droneId", Context.MODE_PRIVATE)
        val module = com.horizon.caadronesimulator.model.DroneRegistry.getModule(droneId)
        state.modelGene.apply {
            rateT_Up = modelPrefs.getFloat("rateT_Up", module.baseRateT_Up)
            rateT_Down = modelPrefs.getFloat("rateT_Down", module.baseRateT_Down)
            expoT = modelPrefs.getFloat("expoT", module.baseExpoT)
            rateY = modelPrefs.getFloat("rateY", module.baseRateY)
            expoY = modelPrefs.getFloat("expoY", module.baseExpoY)
            rateP = modelPrefs.getFloat("rateP", module.baseRateP)
            expoP = modelPrefs.getFloat("expoP", module.baseExpoP)
            rateR = modelPrefs.getFloat("rateR", module.baseRateR)
            expoR = modelPrefs.getFloat("expoR", module.baseExpoR)
        }
    }
}
