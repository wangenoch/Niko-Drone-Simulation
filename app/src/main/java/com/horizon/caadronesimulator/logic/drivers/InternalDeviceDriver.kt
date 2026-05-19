package com.horizon.caadronesimulator.logic.drivers

import java.nio.ByteBuffer

/**
 * [v1.2.81] 內置設備驅動介面
 * 職責：定義各類遙控協議的解析規範。
 */
interface InternalDeviceDriver {
    val brandName: String
    var defaultBaudRate: Int
    val minPacketSize: Int
    val defaultPort: String
    val factoryAppPackage: String?

    // Industrial Logic 2.0: 驅動主權政策
    val isMappingProtected: Boolean get() = false
    val isAutoPromptEnabled: Boolean get() = false
    val recommendedBaudRate: Int get() = 115200
    val protectionWarning: String get() = ""

    fun parseRaw(buffer: ByteBuffer): List<Float>?
    
    // [v1.7.6] 預設映射策略
    fun getDefaultMapping(mode: Int, key: String): com.horizon.caadronesimulator.model.ChannelMapping = com.horizon.caadronesimulator.model.ChannelMapping()
}
