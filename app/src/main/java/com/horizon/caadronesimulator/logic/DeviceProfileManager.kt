package com.horizon.caadronesimulator.logic

import android.view.InputDevice
import android.hardware.input.InputManager
import android.content.Context

/**
 * [v1.2.71] 外接裝置配置管理員
 * 負責識別 HID 裝置指紋，實現「一機一檔」自動切換功能。
 */
object DeviceProfileManager {
    
    /**
     * 獲取當前連線的所有手把中，最活躍或首位的硬體指紋
     */
    fun getActiveHidFingerprint(context: Context): String {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val deviceIds = inputManager.inputDeviceIds
        
        for (id in deviceIds) {
            val device = inputManager.getInputDevice(id)
            if (device != null && (device.sources and InputDevice.SOURCE_JOYSTICK) != 0) {
                // 生成格式: VID_[VendorID]_PID_[ProductID]
                val vid = String.format("%04X", device.vendorId)
                val pid = String.format("%04X", device.productId)
                // 排除系統內置或其他無效 ID
                if (vid == "0000" && pid == "0000") continue
                return "hid_${vid}_${pid}"
            }
        }
        return "external_settings" // 預設回退名稱
    }

    /**
     * 獲取裝置顯示名稱
     */
    fun getActiveHidName(context: Context): String {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val deviceIds = inputManager.inputDeviceIds
        for (id in deviceIds) {
            val device = inputManager.getInputDevice(id)
            if (device != null && (device.sources and InputDevice.SOURCE_JOYSTICK) != 0) {
                return device.name
            }
        }
        return "通用手把"
    }
}
