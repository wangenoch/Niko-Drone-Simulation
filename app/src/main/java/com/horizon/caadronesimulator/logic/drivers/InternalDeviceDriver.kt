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

    /**
     * 物理偏移解析：將原始 Buffer 轉為 LSV, LSH, RSV, RSH
     */
    fun parseRaw(buffer: java.nio.ByteBuffer): List<Float>

    /**
     * 獲取該硬體在特定 Mode 下的預設指派
     */
    fun getDefaultMapping(mode: Int, key: String): ChannelMapping
}
