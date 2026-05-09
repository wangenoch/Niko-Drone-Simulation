package com.horizon.caadronesimulator.model

import android.content.Context
import android.content.SharedPreferences
import com.horizon.caadronesimulator.logic.DeviceProfileManager

/**
 * v1.3.5 全系統狀態持久化管理
 */
class SettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("drone_settings", Context.MODE_PRIVATE)
    private val ax12Prefs: SharedPreferences = context.getSharedPreferences("ax12_settings", Context.MODE_PRIVATE)
    private val genericExternalPrefs: SharedPreferences = context.getSharedPreferences("external_settings", Context.MODE_PRIVATE)

    fun isFirstLaunch(): Boolean = !prefs.contains("lastSeenVersion")

    fun saveSettings(state: DroneState) {
        prefs.edit().apply {
            putInt("joystickMode", state.joystickMode)
            putInt("inputMode", state.inputMode)
            putString("droneType", state.droneType)
            putBoolean("halfThrottle", state.halfThrottle)
            putFloat("joystickDeadzone", state.joystickDeadzone)
            putBoolean("isMuted", state.isMuted)
            putBoolean("enableVerticalDraft", state.enableVerticalDraft)
            putBoolean("showShadow", state.showShadow)
            putBoolean("showTutorial", state.showTutorial)
            putBoolean("useSimplifiedMarkers", state.useSimplifiedMarkers)
            putBoolean("showSpecialTitle", state.showSpecialTitle)
            putBoolean("useFlightLimit", state.useFlightLimit)
            putBoolean("enableZoomAssistant", state.enableZoomAssistant)
            putFloat("mainFOV", state.mainFOV)
            putBoolean("useSmartObserver", state.useSmartObserver)
            putBoolean("showSideRulers", state.showSideRulers)
            putBoolean("showGroundAnchor", state.showGroundAnchor)
            putBoolean("autoPiPRelocate", state.autoPiPRelocate)
            putBoolean("hasShownJoystickTutorial", state.hasShownJoystickTutorial)
            putBoolean("hasShownClimateTutorial", state.hasShownClimateTutorial)
            putBoolean("isMappingUnlocked", state.isMappingUnlocked)
            putFloat("zoomFactor", state.zoomFactor)
            putString("lockedProtocol", state.lockedProtocol)
            putString("lastSeenVersion", AppConfig.CURRENT_VERSION)
            putBoolean("reverseSliderSides", state.reverseSliderSides)
            putFloat("observerHeight", state.observerHeight)
            putFloat("observerTilt", state.observerTilt)
            
            putBoolean("useGlobalRates", state.useGlobalRates)
            putBoolean("showIndividualRates", state.showIndividualRates)
            putFloat("globalRate", state.globalRate)
            putFloat("globalExpo", state.globalExpo)

            putBoolean("hw_verified_ax12", state.isHardwareVerified)
            putInt("ax12_probe_attempts", state.probeAttempts)
            putBoolean("was_internal_success", state.wasInternalSuccess)
            
            putInt("radarZoomMode", state.radarZoomMode)
            putBoolean("showVirtualJoysticks", state.showVirtualJoysticks)
            putInt("baudRate", state.baudRate)
            
            // [v1.3.5] 環境與氣候狀態
            putInt("windLevel", state.windLevel)
            putString("windDirection", state.windDirection)
            putInt("windVariation", state.windVariation)
            putInt("windDirVariation", state.windDirVariation)
            putString("timeOfDay", state.timeOfDay)
            putFloat("shadowIntensity", state.shadowIntensity)
            putBoolean("useHardcorePhysics", state.useHardcorePhysics)
            putBoolean("isSunSimEnabled", state.isSunSimEnabled)
            putFloat("sunPosition", state.sunPosition)
            
            // [v1.3.9] 網絡通訊儲存
            putString("networkHost", state.networkHost)
            putInt("networkPort", state.networkPort)
            putString("networkProtocol", state.networkProtocol)

            apply()
        }
        
        val targetPrefs = if (state.inputMode == 1) {
            ax12Prefs
        } else {
            val fingerprint = DeviceProfileManager.getActiveHidFingerprint(context)
            context.getSharedPreferences(fingerprint, Context.MODE_PRIVATE)
        }

        targetPrefs.edit().apply {
            saveMapping("ly", state.mappingLY)
            saveMapping("lx", state.mappingLX)
            saveMapping("ry", state.mappingRY)
            saveMapping("rx", state.mappingRX)
            
            // [v1.5.0] 儲存輔助開關映射
            saveMapping("hold", state.mappingHold)
            saveMapping("arm", state.mappingArm)
            saveMapping("obsHeight", state.mappingObsHeight)
            saveMapping("obsTilt", state.mappingObsTilt)
            saveMapping("fpvTilt", state.mappingFpvTilt)
            
            putFloat("rateLY", state.rateLY); putFloat("expoLY", state.expoLY)
            putFloat("rateLX", state.rateLX); putFloat("expoLX", state.expoLX)
            putFloat("rateRY", state.rateRY); putFloat("expoRY", state.expoRY)
            putFloat("rateRX", state.rateRX); putFloat("expoRX", state.expoRX)
            commit()
        }
    }


    private fun SharedPreferences.Editor.saveMapping(key: String, mapping: ChannelMapping) {
        putInt("${key}_axis", mapping.axis)
        putBoolean("${key}_inverted", mapping.inverted)
        putString("${key}_label", mapping.label)
        putFloat("${key}_min", mapping.min)
        putFloat("${key}_max", mapping.max)
        putFloat("${key}_center", mapping.center)
    }

    fun loadSettings(state: DroneState): DroneState {
        var loadedProtocol = prefs.getString("lockedProtocol", state.lockedProtocol) ?: state.lockedProtocol
        if (loadedProtocol == "UMBUS") {
            loadedProtocol = "AX12(UMBUS)"
        }
        
        val inputMode = if (state.inputMode != -1) state.inputMode else prefs.getInt("inputMode", 0)
        
        val targetPrefs = if (inputMode == 1) {
            ax12Prefs
        } else {
            val fingerprint = DeviceProfileManager.getActiveHidFingerprint(context)
            val profile = context.getSharedPreferences(fingerprint, Context.MODE_PRIVATE)
            
            if (!profile.contains("ly_axis") && fingerprint != "external_settings") {
                profile.edit().apply {
                    listOf("ly", "lx", "ry", "rx", "hold", "arm", "obsHeight", "obsTilt", "fpvTilt").forEach { key ->
                        putInt("${key}_axis", genericExternalPrefs.getInt("${key}_axis", -1))
                        putBoolean("${key}_inverted", genericExternalPrefs.getBoolean("${key}_inverted", false))
                        putString("${key}_label", genericExternalPrefs.getString("${key}_label", "未設定"))
                        putFloat("${key}_min", genericExternalPrefs.getFloat("${key}_min", -1f))
                        putFloat("${key}_max", genericExternalPrefs.getFloat("${key}_max", 1f))
                        putFloat("${key}_center", genericExternalPrefs.getFloat("${key}_center", 0f))
                    }
                    putFloat("rateLY", genericExternalPrefs.getFloat("rateLY", 1f))
                    putFloat("expoLY", genericExternalPrefs.getFloat("expoLY", 0f))
                    apply()
                }
            }
            profile
        }
        
        state.apply {
            this.joystickMode = prefs.getInt("joystickMode", state.joystickMode)
            this.inputMode = inputMode
            this.droneType = prefs.getString("droneType", state.droneType) ?: state.droneType
            this.halfThrottle = prefs.getBoolean("halfThrottle", state.halfThrottle)
            this.joystickDeadzone = prefs.getFloat("joystickDeadzone", state.joystickDeadzone)
            this.isMuted = prefs.getBoolean("isMuted", state.isMuted)
            this.enableVerticalDraft = prefs.getBoolean("enableVerticalDraft", state.enableVerticalDraft)
            this.showShadow = prefs.getBoolean("showShadow", state.showShadow)
            this.showTutorial = prefs.getBoolean("showTutorial", state.showTutorial)
            this.useSimplifiedMarkers = prefs.getBoolean("useSimplifiedMarkers", false)
            this.showSpecialTitle = prefs.getBoolean("showSpecialTitle", false)
            this.useFlightLimit = prefs.getBoolean("useFlightLimit", true)
            this.enableZoomAssistant = prefs.getBoolean("enableZoomAssistant", true)
            this.mainFOV = prefs.getFloat("mainFOV", 45f)
            this.useSmartObserver = prefs.getBoolean("useSmartObserver", false)
            this.showSideRulers = prefs.getBoolean("showSideRulers", true)
            this.showGroundAnchor = prefs.getBoolean("showGroundAnchor", false)
            this.autoPiPRelocate = prefs.getBoolean("autoPiPRelocate", true)
            this.hasShownJoystickTutorial = prefs.getBoolean("hasShownJoystickTutorial", false)
            this.hasShownClimateTutorial = prefs.getBoolean("hasShownClimateTutorial", false)
            this.isMappingUnlocked = prefs.getBoolean("isMappingUnlocked", state.isMappingUnlocked)
            this.zoomFactor = prefs.getFloat("zoomFactor", state.zoomFactor)
            this.lockedProtocol = loadedProtocol
            this.showVirtualJoysticks = prefs.getBoolean("showVirtualJoysticks", this.showVirtualJoysticks)
            this.reverseSliderSides = prefs.getBoolean("reverseSliderSides", state.reverseSliderSides)
            this.observerHeight = prefs.getFloat("observerHeight", state.observerHeight)
            this.observerTilt = prefs.getFloat("observerTilt", 0f)
            this.shadowIntensity = prefs.getFloat("shadowIntensity", state.shadowIntensity)
            
            // [v1.3.5] 環境與氣候狀態載入
            this.windLevel = prefs.getInt("windLevel", state.windLevel)
            this.windDirection = prefs.getString("windDirection", state.windDirection) ?: state.windDirection
            this.windVariation = prefs.getInt("windVariation", state.windVariation)
            this.windDirVariation = prefs.getInt("windDirVariation", state.windDirVariation)
            this.timeOfDay = prefs.getString("timeOfDay", state.timeOfDay) ?: state.timeOfDay
            this.useHardcorePhysics = prefs.getBoolean("useHardcorePhysics", false)
            this.isSunSimEnabled = prefs.getBoolean("isSunSimEnabled", false)
            this.sunPosition = prefs.getFloat("sunPosition", 0.5f)
            
            // [v1.3.9] 網絡通訊載入
            this.networkHost = prefs.getString("networkHost", "127.0.0.1") ?: "127.0.0.1"
            this.networkPort = prefs.getInt("networkPort", 14550)
            this.networkProtocol = prefs.getString("networkProtocol", "UDP") ?: "UDP"

            this.activeHidName = if (inputMode == 0) DeviceProfileManager.getActiveHidName(context) else "內置系統"
            
            val lastSeen = prefs.getString("lastSeenVersion", "")
            if (lastSeen != AppConfig.CURRENT_VERSION) {
                this.showUpdateNotice = true
            }
            
            this.internalProfile.mappingRY = loadMapping(ax12Prefs, "ry", ChannelMapping(102, false, "俯仰 Pitch"))
            this.internalProfile.mappingRX = loadMapping(ax12Prefs, "rx", ChannelMapping(103, false, "橫滾 Roll"))
            
            // [v1.5.0] 載入內置輔助開關
            this.internalProfile.mappingHold = loadMapping(ax12Prefs, "hold", ChannelMapping(-1, false, "熄火開關"))
            this.internalProfile.mappingArm = loadMapping(ax12Prefs, "arm", ChannelMapping(-1, false, "解鎖開關"))
            this.internalProfile.mappingObsHeight = loadMapping(ax12Prefs, "obsHeight", ChannelMapping(-1, false, "站位高度"))
            this.internalProfile.mappingObsTilt = loadMapping(ax12Prefs, "obsTilt", ChannelMapping(-1, false, "抬頭角度"))
            this.internalProfile.mappingFpvTilt = loadMapping(ax12Prefs, "fpvTilt", ChannelMapping(-1, false, "FPV 雲台"))

            val hidFingerprint = DeviceProfileManager.getActiveHidFingerprint(context)
            val hidPrefs = context.getSharedPreferences(hidFingerprint, Context.MODE_PRIVATE)
            this.externalProfile.mappingLY = loadMapping(hidPrefs, "ly", ChannelMapping(-1))
            this.externalProfile.mappingLX = loadMapping(hidPrefs, "lx", ChannelMapping(-1))
            this.externalProfile.mappingRY = loadMapping(hidPrefs, "ry", ChannelMapping(-1))
            this.externalProfile.mappingRX = loadMapping(hidPrefs, "rx", ChannelMapping(-1))
            
            // [v1.5.0] 載入外接輔助開關
            this.externalProfile.mappingHold = loadMapping(hidPrefs, "hold", ChannelMapping(-1))
            this.externalProfile.mappingArm = loadMapping(hidPrefs, "arm", ChannelMapping(-1))
            this.externalProfile.mappingObsHeight = loadMapping(hidPrefs, "obsHeight", ChannelMapping(-1))
            this.externalProfile.mappingObsTilt = loadMapping(hidPrefs, "obsTilt", ChannelMapping(-1))
            this.externalProfile.mappingFpvTilt = loadMapping(hidPrefs, "fpvTilt", ChannelMapping(-1))

            if (inputMode == 1 && !this.isMappingUnlocked && this.mappingLY.axis == -1) {
                this.mappingLY = ChannelMapping(101, false, "油門/俯仰")
                this.mappingLX = ChannelMapping(102, false, "航向/橫滾")
                this.mappingRY = ChannelMapping(103, false, "俯仰/油門")
                this.mappingRX = ChannelMapping(104, false, "橫滾/航向")
                val labels = getLabelsForMode(this.joystickMode)
                this.mappingLY = this.mappingLY.copy(label = labels[0])
                this.mappingLX = this.mappingLX.copy(label = labels[1])
                this.mappingRY = this.mappingRY.copy(label = labels[2])
                this.mappingRX = this.mappingRX.copy(label = labels[3])
            }
            
            this.useGlobalRates = prefs.getBoolean("useGlobalRates", state.useGlobalRates)
            this.showIndividualRates = prefs.getBoolean("showIndividualRates", state.showIndividualRates)
            this.globalRate = prefs.getFloat("globalRate", state.globalRate)
            this.globalExpo = prefs.getFloat("globalExpo", state.globalExpo)
            
            this.rateLY = targetPrefs.getFloat("rateLY", state.rateLY)
            this.expoLY = targetPrefs.getFloat("expoLY", state.expoLY)
            this.rateLX = targetPrefs.getFloat("rateLX", state.rateLX)
            this.expoLX = targetPrefs.getFloat("expoLX", state.expoLX)
            this.rateRY = targetPrefs.getFloat("rateRY", state.rateRY)
            this.expoRY = targetPrefs.getFloat("expoRY", state.expoRY)
            this.rateRX = targetPrefs.getFloat("rateRX", state.rateRX)
            this.expoRX = targetPrefs.getFloat("expoRX", state.expoRX)

            this.isHardwareVerified = prefs.getBoolean("hw_verified_ax12", false)
            this.probeAttempts = prefs.getInt("ax12_probe_attempts", 0)
            this.wasInternalSuccess = prefs.getBoolean("was_internal_success", false)
            this.radarZoomMode = prefs.getInt("radarZoomMode", 0)
            this.baudRate = prefs.getInt("baudRate", 115200)

            this.isSettingsLoaded = true
        }
        return state
    }

    private fun loadMapping(p: SharedPreferences, key: String, default: ChannelMapping): ChannelMapping {
        val axis = p.getInt("${key}_axis", default.axis)
        if (axis == -1) return default
        
        return ChannelMapping(
            axis = axis,
            inverted = p.getBoolean("${key}_inverted", default.inverted),
            label = p.getString("${key}_label", default.label) ?: default.label,
            min = p.getFloat("${key}_min", default.min),
            max = p.getFloat("${key}_max", default.max),
            center = p.getFloat("${key}_center", default.center)
        )
    }

    private fun getLabelsForMode(mode: Int): List<String> {
        return when(mode) {
            1 -> listOf("俯仰 Pitch", "航向 Yaw", "油門 Throttle", "橫滾 Roll")
            2 -> listOf("油門 Throttle", "航向 Yaw", "俯仰 Pitch", "橫滾 Roll")
            3 -> listOf("油門 Throttle", "橫滾 Roll", "俯仰 Pitch", "航向 Yaw")
            4 -> listOf("俯仰 Pitch", "橫滾 Roll", "油門 Throttle", "航向 Yaw")
            else -> listOf("左垂直", "左水平", "右垂直", "右水平")
        }
    }
}
