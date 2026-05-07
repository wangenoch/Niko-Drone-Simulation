package com.horizon.caadronesimulator.logic

import android.os.Build

/**
 * [v1.2.82] 硬件裝置描述檔 (通用化識別版)
 */
data class HardwareProfile(
    val id: String,
    val brandName: String,
    val identificationTags: List<String> = emptyList(), // 靜態指紋標籤 (模糊匹配)
    val probeSignature: Byte? = null,                   // 協議暗號 (如 UMBUS: 0xA6)
    val defaultBaudRate: Int = 115200,
    val minPacketSize: Int = 0,
    val defaultInternalPort: String = "/dev/ttyS0",
    val isProfessionalRemote: Boolean = false,
    val factoryAppPackage: String? = null,
    val driver: com.horizon.caadronesimulator.logic.drivers.InternalDeviceDriver? = null
)

object HardwareRegistry {
    private val profiles = listOf(
        HardwareProfile(
            id = "RM_AX12",
            brandName = "RadioMaster AX12",
            identificationTags = listOf("ax12", "tb8788"), // 支援 alps tb8788p1_64_bsp
            probeSignature = 0xA6.toByte(),               // UMBUS Sync Byte
            defaultBaudRate = 921600,
            minPacketSize = 87,
            defaultInternalPort = "/dev/ttyS0",
            isProfessionalRemote = true,
            factoryAppPackage = "com.Flyshark.RadioMasterAX",
            driver = com.horizon.caadronesimulator.logic.drivers.AX12Driver()
        ),
        HardwareProfile(
            id = "QUALCOMM MK15",
            brandName = "SIYI MK15",
            identificationTags = listOf("mk15", "siyi"),
            defaultBaudRate = 115200,
            defaultInternalPort = "/dev/ttyUSB",
            isProfessionalRemote = true,
            factoryAppPackage = "com.siyi.transmitter",
            driver = com.horizon.caadronesimulator.logic.drivers.MK15Driver()
        )
    )

    /**
     * 第一階段：靜態特徵匹配
     * 返回一個「疑似」的 Profile，用於啟動 10 秒驗證流程。
     */
    fun detectHardwareHint(): HardwareProfile? {
        val sysInfo = "${Build.PRODUCT} ${Build.MODEL} ${Build.MANUFACTURER}".lowercase()
        return profiles.find { prof ->
            prof.identificationTags.any { tag -> sysInfo.contains(tag.lowercase()) }
        }
    }

    /**
     * 獲取通用手機 Profile (Fallback)
     */
    fun getGenericProfile() = HardwareProfile(
        id = "GENERIC_MOBILE",
        brandName = "標準行動裝置",
        isProfessionalRemote = false
    )

    /**
     * 根據 ID 獲取完整 Profile
     */
    fun getProfileById(id: String): HardwareProfile {
        return profiles.find { it.id == id } ?: getGenericProfile()
    }
    
    /**
     * [向下相容] 舊有的同步偵測接口
     */
    fun detectHardware(): HardwareProfile {
        return detectHardwareHint() ?: getGenericProfile()
    }
}
