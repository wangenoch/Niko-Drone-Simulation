package com.horizon.caadronesimulator.model

import android.content.Context
import android.content.SharedPreferences
import com.horizon.caadronesimulator.logic.DeviceProfileManager

/**
 * v1.2.95 虛擬搖桿開關模組實作
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
            putBoolean("enableAirPressure", state.enableAirPressure)
            putBoolean("showShadow", state.showShadow)
            putBoolean("showTutorial", state.showTutorial)
            putBoolean("hasShownJoystickTutorial", state.hasShownJoystickTutorial)
            putBoolean("hasShownClimateTutorial", state.hasShownClimateTutorial)
            putBoolean("isMappingUnlocked", state.isMappingUnlocked)
            putFloat("zoomFactor", state.zoomFactor)
            putString("lockedProtocol", state.lockedProtocol)
            putString("lastSeenVersion", AppConfig.CURRENT_VERSION)
            putBoolean("reverseSliderSides", state.reverseSliderSides)
            putFloat("observerHeight", state.observerHeight)
            
            putBoolean("useGlobalRates", state.useGlobalRates)
            putFloat("globalRate", state.globalRate)
            putFloat("globalExpo", state.globalExpo)

            putBoolean("hw_verified_ax12", state.isHardwareVerified)
            putInt("ax12_probe_attempts", state.probeAttempts)
            putBoolean("was_internal_success", state.wasInternalSuccess)
            
            putInt("radarZoomMode", state.radarZoomMode)
            
            // [v1.2.95] 虛擬搖桿開關狀態
            putBoolean("showVirtualJoysticks", state.showVirtualJoysticks)

            commit() // 使用 commit() 確保同步寫入磁碟
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
                    listOf("ly", "lx", "ry", "rx").forEach { key ->
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
            this.enableAirPressure = prefs.getBoolean("enableAirPressure", state.enableAirPressure)
            this.showShadow = prefs.getBoolean("showShadow", state.showShadow)
            this.showTutorial = prefs.getBoolean("showTutorial", state.showTutorial)
            this.hasShownJoystickTutorial = prefs.getBoolean("hasShownJoystickTutorial", false)
            this.hasShownClimateTutorial = prefs.getBoolean("hasShownClimateTutorial", false)
            this.isMappingUnlocked = prefs.getBoolean("isMappingUnlocked", state.isMappingUnlocked)
            this.zoomFactor = prefs.getFloat("zoomFactor", state.zoomFactor)
            this.lockedProtocol = loadedProtocol
            this.showVirtualJoysticks = prefs.getBoolean("showVirtualJoysticks", this.showVirtualJoysticks)
            this.reverseSliderSides = prefs.getBoolean("reverseSliderSides", state.reverseSliderSides)
            this.observerHeight = prefs.getFloat("observerHeight", state.observerHeight)
            this.activeHidName = if (inputMode == 0) DeviceProfileManager.getActiveHidName(context) else "內置系統"
            
            val lastSeen = prefs.getString("lastSeenVersion", "")
            if (lastSeen != AppConfig.CURRENT_VERSION) {
                this.showUpdateNotice = true
            }
            
            this.internalProfile.mappingLY = loadMapping(ax12Prefs, "ly", ChannelMapping(104, false, "油門 Throttle"))
            this.internalProfile.mappingLX = loadMapping(ax12Prefs, "lx", ChannelMapping(101, false, "航向 Yaw"))
            this.internalProfile.mappingRY = loadMapping(ax12Prefs, "ry", ChannelMapping(102, false, "俯仰 Pitch"))
            this.internalProfile.mappingRX = loadMapping(ax12Prefs, "rx", ChannelMapping(103, false, "橫滾 Roll"))

            val hidFingerprint = DeviceProfileManager.getActiveHidFingerprint(context)
            val hidPrefs = context.getSharedPreferences(hidFingerprint, Context.MODE_PRIVATE)
            this.externalProfile.mappingLY = loadMapping(hidPrefs, "ly", ChannelMapping(-1))
            this.externalProfile.mappingLX = loadMapping(hidPrefs, "lx", ChannelMapping(-1))
            this.externalProfile.mappingRY = loadMapping(hidPrefs, "ry", ChannelMapping(-1))
            this.externalProfile.mappingRX = loadMapping(hidPrefs, "rx", ChannelMapping(-1))

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
