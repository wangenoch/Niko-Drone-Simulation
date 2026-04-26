package com.horizon.caadronesimulator.model

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("drone_settings", Context.MODE_PRIVATE)

    fun saveSettings(state: DroneState) {
        prefs.edit().apply {
            putInt("joystickMode", state.joystickMode)
            putString("droneType", state.droneType)
            putBoolean("halfThrottle", state.halfThrottle)
            putFloat("joystickDeadzone", state.joystickDeadzone)
            // 移除天氣、風向、光照的儲存，使其重啟後恢復預設值
            putBoolean("isMuted", state.isMuted)
            putBoolean("showShadow", state.showShadow)
            putBoolean("showTutorial", state.showTutorial)
            putFloat("zoomFactor", state.zoomFactor)
            // 移除視角模式、虛擬搖桿與狀態欄儲存，使其重啟後恢復預設值
            
            // Save Channel Mappings
            saveMapping("ly", state.mappingLY)
            saveMapping("lx", state.mappingLX)
            saveMapping("ry", state.mappingRY)
            saveMapping("rx", state.mappingRX)
            
            apply()
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

    fun loadSettings(defaultState: DroneState): DroneState {
        return defaultState.copy(
            joystickMode = prefs.getInt("joystickMode", defaultState.joystickMode),
            droneType = prefs.getString("droneType", defaultState.droneType) ?: defaultState.droneType,
            halfThrottle = prefs.getBoolean("halfThrottle", defaultState.halfThrottle),
            joystickDeadzone = prefs.getFloat("joystickDeadzone", defaultState.joystickDeadzone),
            // 不載入天氣與環境設定，確保使用 DroneState 定義的預設值
            isMuted = prefs.getBoolean("isMuted", defaultState.isMuted),
            showShadow = prefs.getBoolean("showShadow", defaultState.showShadow),
            showTutorial = prefs.getBoolean("showTutorial", defaultState.showTutorial),
            zoomFactor = prefs.getFloat("zoomFactor", defaultState.zoomFactor),
            // 不載入視角模式、虛擬搖桿與狀態欄，確保每次重啟都是預設隱藏且使用全螢幕
            mappingLY = loadMapping("ly", defaultState.mappingLY),
            mappingLX = loadMapping("lx", defaultState.mappingLX),
            mappingRY = loadMapping("ry", defaultState.mappingRY),
            mappingRX = loadMapping("rx", defaultState.mappingRX)
        )
    }

    private fun loadMapping(key: String, default: ChannelMapping): ChannelMapping {
        val axis = prefs.getInt("${key}_axis", default.axis)
        if (axis == -1) return default
        
        return ChannelMapping(
            axis = axis,
            inverted = prefs.getBoolean("${key}_inverted", default.inverted),
            label = prefs.getString("${key}_label", default.label) ?: default.label,
            min = prefs.getFloat("${key}_min", default.min),
            max = prefs.getFloat("${key}_max", default.max),
            center = prefs.getFloat("${key}_center", default.center)
        )
    }
}
