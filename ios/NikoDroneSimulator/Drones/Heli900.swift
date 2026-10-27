import Foundation

/// [v1.8.01] 專業 900 級單旋翼直昇機 (1:1 對齊 Heli900.kt)
struct Heli900: DroneModule {
    static let shared = Heli900()

    let id = "HELI_900"
    let name = "SPECS_HELI_900_NAME"
    let icon = "🚁"
    let hardwareSpecs = ModelHardwareSpecs(
        type: .srh,
        wheelbaseMm: 900,
        propDiameterInch: 22,
        propPitchInch: 7,
        motorKv: 280,
        takeoffWeightKg: 10.0,
        payloadKg: 0.0,
        flightTimeMin: 15,
        description: "SPECS_HELI_900_DESC"
    )
    let groundOffset: Float = 0.25
    let visualOffset: Float = -0.05
    let collisionRadius: Float = 0.75
    let scale: Float = 0.5625
    let shadowSizeBase: Float = 0.52
    let maxLandingSpeed: Float = 1.5

    let fpvFov: Float = 75.0
    let cameraVisualOffset: Float = 0.70
    let cameraHeightOffset: Float = 0.15

    let geometry: [DronePart] = {
        var parts: [DronePart] = [
            DronePart(tx: 0.0, ty: 0.35, tz: 0.4, w: 0.25, h: 0.45, d: 0.6, color: [0.1, 0.1, 0.1, 1.0]),
            DronePart(tx: 0.0, ty: 0.55, tz: 0.6, w: 0.2, h: 0.2, d: 0.3, color: [0.0, 0.4, 0.8, 0.8]),
            DronePart(tx: 0.0, ty: 0.4, tz: -0.6, w: 0.12, h: 0.12, d: 1.2, color: [0.15, 0.15, 0.15, 1.0]),
            DronePart(tx: 0.0, ty: 0.65, tz: 0.2, w: 0.04, h: 0.25, d: 0.04, color: [0.2, 0.2, 0.2, 1.0]),
            DronePart(tx: 0.0, ty: 0.78, tz: 0.2, w: 2.5, h: 0.015, d: 0.08, color: [0.9, 0.9, 0.9, 0.5], isPropeller: true, baseRpm: 2200.0),
            DronePart(tx: 0.0, ty: 0.78, tz: 0.2, w: 0.08, h: 0.015, d: 2.5, color: [0.9, 0.9, 0.9, 0.5], isPropeller: true, baseRpm: 2200.0),
            DronePart(tx: 0.1, ty: 0.4, tz: -1.1, w: 0.01, h: 0.6, d: 0.04, color: [0.9, 0.9, 0.9, 0.6], rz: 90.0, isTailPropeller: true, baseRpm: 4000.0)
        ]
        for sx: Float in [-0.3, 0.3] {
            parts.append(DronePart(tx: sx, ty: 0.15, tz: 0.2, w: 0.03, h: 0.35, d: 0.03, color: [0.2, 0.2, 0.2, 1.0]))
            parts.append(DronePart(tx: sx, ty: 0.0, tz: 0.2, w: 0.04, h: 0.04, d: 1.2, color: [0.1, 0.1, 0.1, 1.0]))
        }
        return parts
    }()

    let baseRate: Float = 0.9
    let baseExpo: Float = 0.5
    let baseRateT_Up: Float = 0.5
    let baseRateT_Down: Float = 0.4
    let baseRateY: Float = 0.7
    let baseRateP: Float = 0.7
    let baseRateR: Float = 0.7

    let physicsPower: Float = 32.0
    let physicsMass: Float = 1.2
    let physicsDamping: Float = 0.99
    let attitudeRestorationForce: Float = 1.8
    let brakingInertiaScale: Float = 2.5

    let soundProfile = SoundProfile(
        baseFreq: 185.0,
        harmonicPower: 0.5,
        noiseFactor: 0.08,
        isMultiMotor: false,
        modulationHz: 18.0
    )
}
