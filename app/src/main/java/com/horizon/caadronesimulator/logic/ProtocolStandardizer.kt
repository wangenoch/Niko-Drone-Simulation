package com.horizon.caadronesimulator.logic

/**
 * [v1.2.71] 協議標準化組件
 * 核心職責：將不同硬體協議的亂序通道標準化為系統內部的 T-R-E-A (Throttle-Yaw-Pitch-Roll) 序列。
 */
object ProtocolStandardizer {

    /**
     * 針對 UMBUS 協議的智慧對齊邏輯，因其預設為 TREA (T:CH1, R:CH2, E:CH3, A:CH4)
     * 此函數在 UsbSerialManager 接收數據後、傳給 UI 前調用。
     */
    fun standardize(rawChannels: List<Float>, protocol: String, isExpertMode: Boolean): List<Float> {
        // 如果是專家模式 (已解鎖映射)，則不進行自動轉譯，交給使用者手動定義
        if (isExpertMode) return rawChannels
        
        // 偵測協議類型
        val isUimbus = protocol.contains("UMBUS", ignoreCase = true)
        val isSbus = protocol.contains("SBUS", ignoreCase = true)

        if (isUimbus) {
            // [v1.2.71] 針對 UMBUS 協議的智慧對齊邏輯，因其預設為 TREA
            // 硬體原始順序 (AX12Handler 解析結果): CH1:Pitch, CH2:Throttle, CH3:Yaw, CH4:Roll
            // 系統標準順序: CH1:Throttle, CH2:Yaw, CH3:Pitch, CH4:Roll (TREA)
            if (rawChannels.size >= 4) {
                val standardized = rawChannels.toMutableList()
                standardized[0] = rawChannels[1] // Throttle (原本在 CH2)
                standardized[1] = rawChannels[2] // Yaw (原本在 CH3)
                standardized[2] = rawChannels[0] // Pitch (原本在 CH1)
                standardized[3] = rawChannels[3] // Roll (原本在 CH4)
                return standardized
            }
        }

        if (isSbus) {
            // 若偵測到標準 SBUS (通常為 AETR)，可在此進行轉譯為 TREA
            // AETR (CH1:A, CH2:E, CH3:T, CH4:R) -> TREA
            if (rawChannels.size >= 4) {
                val standardized = rawChannels.toMutableList()
                standardized[0] = rawChannels[2] // T
                standardized[1] = rawChannels[3] // R (Yaw)
                standardized[2] = rawChannels[1] // E (Pitch)
                standardized[3] = rawChannels[0] // A (Roll)
                return standardized
            }
        }
        
        return rawChannels
    }
}
