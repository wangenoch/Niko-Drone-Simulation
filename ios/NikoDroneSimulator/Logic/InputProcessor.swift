import Foundation

/// [v1.8.01] 標準手感演算引擎 (1:1 對齊 InputProcessor.kt)
enum InputProcessor {
    static func process(raw: Float, deadzone: Float, expo: Float, rate: Float, mapping: ChannelMapping) -> Float {
        var normalized: Float
        if raw > mapping.center {
            let denom = max(mapping.max - mapping.center, 0.01)
            normalized = (raw - mapping.center) / denom
        } else {
            let denom = max(mapping.center - mapping.min, 0.01)
            normalized = (raw - mapping.center) / denom
        }
        normalized = min(max(normalized, -1.0), 1.0)

        if abs(normalized) < deadzone { return 0.0 }

        let sign: Float = normalized >= 0 ? 1.0 : -1.0
        let absVal = abs(normalized)
        let afterExpo = pow(absVal, 1.0 + expo * 2.0) * sign
        return afterExpo * rate
    }

    static func processVirtual(touch: Float, expo: Float, rate: Float) -> Float {
        let clamped = min(max(touch, -1.0), 1.0)
        let sign: Float = clamped >= 0 ? 1.0 : -1.0
        let absVal = abs(clamped)
        let afterExpo = pow(absVal, 1.0 + expo * 2.0) * sign
        return afterExpo * rate
    }
}
