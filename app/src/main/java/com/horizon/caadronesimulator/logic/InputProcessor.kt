package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.ChannelMapping
import kotlin.math.*

/**
 * [v1.5.8] 標準手感演算引擎 (Handfeel Logic Core)
 * 職責：執行 Expo 指數曲線與 Rate 靈敏度混合運算，已移除舊版註解。
 */
object InputProcessor {

    /** 處理實體搖桿輸入 */
    fun process(raw: Float, deadzone: Float, expo: Float, rate: Float, mapping: ChannelMapping): Float {
        // 1. 校準量程 (Normalization)
        var normalized = if (raw > mapping.center) {
            (raw - mapping.center) / (mapping.max - mapping.center)
        } else {
            (raw - mapping.center) / (mapping.center - mapping.min)
        }
        normalized = normalized.coerceIn(-1.0f, 1.0f)

        // 2. 應用死區 (Deadzone)
        if (abs(normalized) < deadzone) return 0f
        
        // 3. 實施 Expo 指數曲線
        val sign = if (normalized >= 0) 1f else -1f
        val absVal = abs(normalized)
        val afterExpo = absVal.pow(1.0f + expo * 2.0f) * sign
        
        // 4. 乘上 Rate 總靈敏度
        return afterExpo * rate
    }

    /** 處理虛擬觸控輸入 */
    fun processVirtual(touch: Float, expo: Float, rate: Float): Float {
        val sign = if (touch >= 0) 1f else -1f
        val absVal = abs(touch)
        val afterExpo = absVal.pow(1.0f + expo * 2.0f) * sign
        return afterExpo * rate
    }
}
