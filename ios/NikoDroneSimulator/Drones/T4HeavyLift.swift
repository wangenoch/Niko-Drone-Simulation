import Foundation

/// [v1.8.01] 專業考照負重機 (JoyFlight T4) - 航規校準版 (左紅右綠)
struct T4HeavyLift: DroneModule {
    static let shared = T4HeavyLift()

    let id = "HEAVY_LIFT"
    let name = "JoyFlight T4"
    let icon = "🏗️"
    let hardwareSpecs = ModelHardwareSpecs(
        type: .mr,
        wheelbaseMm: 1100,
        propDiameterInch: 24,
        propPitchInch: 8,
        motorKv: 150,
        takeoffWeightKg: 14.9,
        payloadKg: 1.0,
        flightTimeMin: 15,
        description: "說明：大型負重機型具有更長的力臂與獨特的起落架設計。"
    )
    let groundOffset: Float = 0.45
    let visualOffset: Float = -0.07
    let collisionRadius: Float = 0.83
    let scale: Float = 0.6875
    let shadowSizeBase: Float = 0.55
    let maxLandingSpeed: Float = 1.8

    let fpvFov: Float = 90.0
    let cameraVisualOffset: Float = 0.55
    let cameraHeightOffset: Float = -0.28

    let geometry: [DronePart] = {
        var parts: [DronePart] = [
            DronePart(tx: 0.0, ty: 0.1, tz: 0.0, w: 0.6, h: 0.05, d: 0.6, color: [0.0, 0.3, 0.8, 1.0]),
            DronePart(tx: 0.0, ty: 0.15, tz: 0.0, w: 0.4, h: 0.12, d: 0.4, color: [0.1, 0.1, 0.1, 1.0]),
            DronePart(tx: 0.0, ty: 0.1, tz: -0.3, w: 0.05, h: 0.05, d: 0.05, color: [0.0, 1.0, 0.0, 1.0]),
            DronePart(tx: 0.0, ty: 0.25, tz: 0.0, w: 0.25, h: 0.12, d: 0.25, color: [0.0, 0.3, 0.8, 1.0])
        ]
        for ax: Float in [-0.12, 0.12] {
            parts.append(DronePart(tx: ax, ty: 0.35, tz: -0.18, w: 0.012, h: 0.3, d: 0.012, color: [0.15, 0.15, 0.15, 1.0], rx: -15.0))
        }
        let armLen: Float = 0.8
        let corners: [[Float]] = [[1.0, 1.0], [-1.0, 1.0], [1.0, -1.0], [-1.0, -1.0]]
        for p in corners {
            let side = p[0] * p[1]
            parts.append(DronePart(tx: p[0] * armLen / 2.0 * 0.707, ty: 0.15, tz: p[1] * armLen / 2.0 * 0.707, w: armLen, h: 0.04, d: 0.04, color: [0.05, 0.05, 0.05, 1.0], ry: -45.0 * side))
            parts.append(DronePart(tx: p[0] * armLen * 0.707, ty: 0.15, tz: p[1] * armLen * 0.707, w: 0.12, h: 0.08, d: 0.12, color: [0.1, 0.1, 0.1, 1.0]))
            let ledColor: [Float] = p[1] > 0 ? [0.0, 1.0, 0.0, 1.0] : [1.0, 0.0, 0.0, 1.0]
            parts.append(DronePart(tx: p[0] * armLen * 0.707, ty: 0.11, tz: p[1] * armLen * 0.707, w: 0.14, h: 0.03, d: 0.14, color: ledColor))
            parts.append(DronePart(tx: p[0] * armLen * 0.707, ty: 0.2, tz: p[1] * armLen * 0.707, w: 0.7, h: 0.01, d: 0.04, color: [0.9, 0.9, 0.9, 0.5], isPropeller: true, rotationDirection: side, baseRpm: 2500.0))
        }
        let legs: [[Float]] = [[0.25, 0.2], [-0.25, 0.2], [0.25, -0.2], [-0.25, -0.2]]
        for l in legs {
            parts.append(DronePart(tx: l[0], ty: -0.2, tz: l[1], w: 0.03, h: 0.5, d: 0.03, color: [0.1, 0.1, 0.1, 1.0]))
        }
        for lx: Float in [-0.25, 0.25] {
            let skidColor: [Float] = lx > 0 ? [1.0, 0.0, 0.0, 1.0] : [0.0, 1.0, 0.0, 1.0]
            parts.append(DronePart(tx: lx, ty: -0.45, tz: 0.0, w: 0.04, h: 0.04, d: 0.9, color: skidColor))
            parts.append(DronePart(tx: lx, ty: -0.45, tz: 0.5, w: 0.05, h: 0.05, d: 0.15, color: skidColor))
            parts.append(DronePart(tx: lx, ty: -0.45, tz: -0.5, w: 0.05, h: 0.05, d: 0.15, color: skidColor))
        }
        return parts
    }()

    let baseRate: Float = 0.8
    let baseExpo: Float = 0.5
    let baseRateT_Up: Float = 0.3
    let baseRateT_Down: Float = 0.2
    let baseRateY: Float = 0.4
    let baseRateP: Float = 0.4
    let baseRateR: Float = 0.4

    let physicsDamping: Float = 0.98
    let brakingInertiaScale: Float = 3.4
    let physicsPower: Float = 48.0
    let physicsMass: Float = 1.5

    let soundProfile = SoundProfile(
        baseFreq: 110.0,
        harmonicPower: 0.75,
        noiseFactor: 0.15,
        isMultiMotor: true
    )
}
