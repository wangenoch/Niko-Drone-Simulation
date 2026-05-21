package com.horizon.caadronesimulator.logic

import android.os.Build
import com.horizon.caadronesimulator.logic.drivers.InternalDeviceDriver

/**
 * [v1.7.6] 硬件裝置描述檔 (通用化識別版)
 * 職責：僅保留資料結構與基礎匹配，驅動程式實例化延遲至 Flavor 層注入。
 */
data class HardwareProfile(
    val id: String,
    val brandName: String,
    val identificationTags: List<String> = emptyList(),
    val probeSignature: Byte? = null,
    val defaultBaudRate: Int = 115200,
    val minPacketSize: Int = 0,
    val defaultInternalPort: String = "/dev/ttyS0",
    val isProfessionalRemote: Boolean = false,
    val factoryAppPackage: String? = null,
    val driver: InternalDeviceDriver? = null,
)

object HardwareRegistry {
    // [v1.5.2] 全域主控開關：開發者除錯用 (設為 true 則全量解鎖所有保護)
    var debugForceUnlockAll = false

    // 預留介面供 Flavor 注入驅動實體，解決 src/main 找不到 src/pro 驅動的問題
    var driverProvider: ((String) -> InternalDeviceDriver?)? = null

    private val profiles = listOf(
        HardwareProfile(
            id = "RM_AX12_V1",
            brandName = "RadioMaster AX12 (UMBUS-V1)",
            identificationTags = listOf("ax12", "tb8788"),
            probeSignature = 0xA6.toByte(),
            defaultBaudRate = 921600,
            minPacketSize = 87,
            defaultInternalPort = "/dev/ttyS0",
            isProfessionalRemote = true,
            factoryAppPackage = "com.Flyshark.RadioMasterAX"
        ),
        HardwareProfile(
            id = "RM_AX12_V2",
            brandName = "RadioMaster AX12 (UMBUS-V2)",
            identificationTags = listOf("ax-enhanced", "umbus-v2"),
            probeSignature = 0xA6.toByte(),
            defaultBaudRate = 921600,
            minPacketSize = 87,
            defaultInternalPort = "/dev/ttyS0",
            isProfessionalRemote = true,
            factoryAppPackage = "com.Flyshark.RadioMasterAX"
        ),
        HardwareProfile(
            id = "QUALCOMM MK15",
            brandName = "SIYI MK15",
            identificationTags = listOf("mk15", "siyi"),
            defaultBaudRate = 115200,
            defaultInternalPort = "/dev/ttyUSB",
            isProfessionalRemote = true,
            factoryAppPackage = "com.siyi.transmitter"
        )
    )

    fun detectHardwareHint(): HardwareProfile? {
        val sysInfo = "${Build.PRODUCT} ${Build.MODEL} ${Build.MANUFACTURER}".lowercase()
        val base = profiles.find { prof ->
            prof.identificationTags.any { tag -> sysInfo.contains(tag.lowercase()) }
        }
        return base?.let { it.copy(driver = driverProvider?.invoke(it.id)) }
    }

    fun getGenericProfile() = HardwareProfile(
        id = "GENERIC_MOBILE",
        brandName = "GENERIC_MOBILE",
        isProfessionalRemote = false
    )

    fun getProfileById(id: String): HardwareProfile {
        val base = profiles.find { it.id == id } ?: getGenericProfile()
        return base.copy(driver = driverProvider?.invoke(base.id))
    }
    
    fun detectHardware(): HardwareProfile {
        return detectHardwareHint() ?: getGenericProfile()
    }
}
