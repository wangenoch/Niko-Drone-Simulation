package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.ChannelMapping
import kotlin.math.*

/**
 * 搖桿輸入處理核心 - 無狀態純函數版
 * 負責將原始硬體數據根據校準值映射至標準空間，並套用死區、曲線與靈敏度。
 */
object InputProcessor {

    /**
     * 核心處理函數
     * @param raw 原始硬體輸入 (如 AX12 的 -1.0 ~ 1.0)
     * @param deadzone 死區設定
     * @param expo 指數曲線 (0.0 ~ 1.0)
     * @param rate 靈敏度放大倍率
     * @param mapping 該通道的校準映射數據 (min, max, center)
     * @param ignoreSettings 是否跳過死區/曲線/Rate (用於校準 UI)
     */
    fun process(
        raw: Float, 
        deadzone: Float, 
        expo: Float, 
        rate: Float, 
        mapping: ChannelMapping,
        ignoreSettings: Boolean = false
    ): Float {
        // 1. 校準映射 (Calibration Mapping)
        // 將原始數據根據校準紀錄的 min, max, center 映射至標準的 -1.0 ~ 1.0 空間
        val calibrated = when {
            raw >= mapping.center -> {
                val range = mapping.max - mapping.center
                if (range > 0.01f) (raw - mapping.center) / range else 0f
            }
            else -> {
                val range = mapping.center - mapping.min
                if (range > 0.01f) (raw - mapping.center) / range else 0f
            }
        }.coerceIn(-1f, 1f)

        if (ignoreSettings) return calibrated

        // 2. 死區處理 (Deadzone)
        val afterDz = when {
            calibrated > deadzone -> (calibrated - deadzone) / (1f - deadzone)
            calibrated < -deadzone -> (calibrated + deadzone) / (1f - deadzone)
            else -> 0f
        }

        if (afterDz == 0f) return 0f

        // 3. 指數曲線 (Expo)
        val absX = abs(afterDz)
        val afterExpo = (1f - expo) * absX + expo * (absX.pow(3))
        
        // 4. 靈敏度 (Rate) 與 反向 (Inverted 已在外部處理，此處不重複)
        val finalValue = sign(afterDz) * afterExpo * rate

        return finalValue.coerceIn(-1f, 1f)
    }

    /**
     * [v1.2.95] 虛擬搖桿專用處理程序
     * 職責：完全解耦實體映射，套用固定死區與獨立 Expo 運算。
     */
    /**
     * [v1.2.95] 虛擬搖桿專用 Expo 轉換程序
     * 職責：處理已過濾死區的線性數值，套用 Expo 曲線。
     */
    fun processVirtual(linearVal: Float, expo: Float, rate: Float): Float {
        // 1. Expo 曲線運算 (Output = linear_val * (1 - expo) + linear_val^3 * expo)
        val absL = abs(linearVal)
        val afterExpo = (1f - expo) * absL + expo * (absL.pow(3))
        
        return (sign(linearVal) * afterExpo * rate).coerceIn(-1f, 1f)
    }
}
