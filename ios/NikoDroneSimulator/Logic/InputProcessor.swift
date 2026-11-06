import Foundation

/// [v1.8.01] 標準手感演算引擎 (1:1 對齊 InputProcessor.kt)
enum InputProcessor {
    static func process(raw: Float, deadzone: Float, expo: Float, rate: Float, mapping: ChannelMapping) -> Float {
        // 1. 安全限幅 (校準量程與極性反轉已於 StickInputState.resolveValue 單一中樞完成)
        let normalized = min(max(raw, -1.0), 1.0)

        if abs(normalized) < deadzone { return 0.0 }

        let sign: Float = normalized >= 0 ? 1.0 : -1.0
        let absVal = abs(normalized)
        let afterExpo = pow(absVal, 1.0 + expo * 2.0) * sign
        return afterExpo * rate
    }

    static func processVirtual(touch: Float, expo: Float, rate: Float, deadzone: Float = 0.05) -> Float {
        let clamped = min(max(touch, -1.0), 1.0)
        let absRaw = abs(clamped)
        if absRaw < deadzone { return 0.0 }
        let sign: Float = clamped >= 0 ? 1.0 : -1.0
        let rescaled = (absRaw - deadzone) / max(1.0 - deadzone, 0.01)
        let afterExpo = pow(rescaled, 1.0 + expo * 2.0) * sign
        return afterExpo * rate
    }
}
