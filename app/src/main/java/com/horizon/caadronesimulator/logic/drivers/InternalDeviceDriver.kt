package com.horizon.caadronesimulator.logic.drivers

import com.horizon.caadronesimulator.model.ChannelMapping

/**
 * [v1.2.81] 內置設備驅動介面
 * 定義所有專業遙控器硬體必須提供的通訊與規格參數。
 */
interface InternalDeviceDriver {
    val brandName: String
    val defaultBaudRate: Int
    val minPacketSize: Int
    val defaultPort: String
    val factoryAppPackage: String?

    // [v1.5.2] Industrial Logic 2.0: 驅動主權政策
    val isMappingProtected: Boolean get() = false      // 是否鎖定映射表
    val isAutoPromptEnabled: Boolean get() = true       // 偵測到時是否跳出詢問
    val recommendedBaudRate: Int get() = 115200         // 建議波特率
    val protectionWarning: String get() = "專業協議保護中" // 警告顯示文字

    /**
     * 物理偏移解析：將原始 Buffer 轉為 LSV, LSH, RSV, RSH
     */
    fun parseRaw(buffer: java.nio.ByteBuffer): List<Float>

    /**
     * 獲取該硬體在特定 Mode 下的預設指派
     */
    fun getDefaultMapping(mode: Int, key: String): ChannelMapping
}
