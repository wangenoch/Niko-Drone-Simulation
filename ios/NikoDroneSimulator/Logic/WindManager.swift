import Foundation

/// [v1.8.01] 專業級分形脈衝大氣引擎 (1:1 對齊 WindManager.kt & CONTROL_SYSTEM_SPEC.md)
enum WindManager {
    struct WindResult {
        let forceVector: [Float]
        let visualAngle: Float
    }

    private static var nextEventTime: Float = 0.0
    private static var isEventActive: Bool = false
    private static var eventStartTime: Float = 0.0
    private static var eventDuration: Float = 0.0
    private static var eventIntensity: Float = 0.0
    private static var eventVerticalBias: Float = 0.0

    static func update(currentTime: Float, level: Int, variation: Int, useHardcore: Bool) {
        if level <= 0 {
            isEventActive = false
            return
        }

        if isEventActive && currentTime > eventStartTime + eventDuration {
            isEventActive = false
            nextEventTime = currentTime + 12.0 + Float.random(in: 0..<30.0)
        }

        if nextEventTime == 0.0 && currentTime > 0.1 {
            nextEventTime = currentTime + 5.0 + Float.random(in: 0..<10.0)
        }

        if !isEventActive && currentTime > nextEventTime {
            isEventActive = true
            eventStartTime = currentTime
            eventDuration = 3.0 + Float.random(in: 0..<4.0)
            eventIntensity = Float(level) * (0.6 + (Float(variation) / 5.0) * 0.4)
            let downBias: Float = useHardcore ? 0.75 : 0.5
            eventVerticalBias = Float.random(in: 0..<1.0) > downBias ? 0.8 : -1.5
        }
    }

    static func calculateWindVector(
        level: Int,
        direction: String,
        variation: Int,
        dirVariation: Int,
        flightTime: Float,
        randomWindAngle: Float
    ) -> WindResult {
        if level <= 0 || direction == AppConfig.WIND_DIR_NONE {
            return WindResult(forceVector: [0.0, 0.0], visualAngle: 0.0)
        }

        let baseFromAngle: Float
        if direction == AppConfig.WIND_DIR_RANDOM {
            baseFromAngle = (randomWindAngle + (flightTime * 0.3)).truncatingRemainder(dividingBy: 360.0)
        } else {
            baseFromAngle = getStaticAngle(direction)
        }

        let impulseFactor: Float = isEventActive ? (calculateSmoothPulse(t: flightTime - eventStartTime, duration: eventDuration) * eventIntensity) : 0.0
        let jitterScale = (Float(dirVariation) / 5.0) * 12.0
        let fractalJitter = (sin(flightTime * 1.7) * 0.6 + sin(flightTime * 3.1) * 0.3 + sin(flightTime * 7.7) * 0.1) * jitterScale

        // [TRUTH ANCHOR] FlowAngle = BaseAngle + 180 (風吹去的方向)
        var flowAngle = (baseFromAngle + 180.0 + fractalJitter).truncatingRemainder(dividingBy: 360.0)
        if flowAngle < 0 { flowAngle += 360.0 }

        let baseStrength = Float(level) * 1.0
        let totalStrength = baseStrength + (impulseFactor * 0.2)

        let rad = flowAngle * .pi / 180.0
        return WindResult(
            forceVector: [-sin(rad) * totalStrength, cos(rad) * totalStrength],
            visualAngle: flowAngle
        )
    }

    static func calculateVerticalDraft(
        level: Int,
        variation: Int,
        flightTime: Float,
        useHardcore: Bool,
        droneType: String,
        applySpecs: Bool
    ) -> Float {
        if level <= 0 { return 0.0 }

        let vImpulse: Float
        if isEventActive {
            let t = flightTime - eventStartTime
            vImpulse = calculateSmoothPulse(t: t, duration: eventDuration) * eventVerticalBias * (Float(level) / 4.0)
        } else {
            vImpulse = 0.0
        }

        let f = flightTime
        let micro: Float = useHardcore ? ((sin(f * 0.8) * 0.5 + sin(f * 2.3) * 0.35 + sin(f * 5.1) * 0.15) * 0.06 * Float(level)) : 0.0
        return vImpulse + micro
    }

    private static func calculateSmoothPulse(t: Float, duration: Float) -> Float {
        let progress = min(max(t / max(duration, 0.001), 0.0), 1.0)
        return sin(progress * .pi)
    }

    static func calculateHeightFactor(altitude: Float, groundY: Float) -> Float {
        let h = max(altitude - groundY, 0.0)
        if h <= 0.1 { return 0.0 }
        let valFactor = log(max(0.1, h) / 0.15) / log(12.0 / 0.15)
        return min(max(valFactor, 0.0), 1.0)
    }

    private static func getStaticAngle(_ direction: String) -> Float {
        switch direction {
        case AppConfig.WIND_DIR_N: return 0.0
        case AppConfig.WIND_DIR_NE: return 45.0
        case AppConfig.WIND_DIR_E: return 90.0
        case AppConfig.WIND_DIR_SE: return 135.0
        case AppConfig.WIND_DIR_S: return 180.0
        case AppConfig.WIND_DIR_SW: return 225.0
        case AppConfig.WIND_DIR_W: return 270.0
        case AppConfig.WIND_DIR_NW: return 315.0
        default: return 0.0
        }
    }
}
