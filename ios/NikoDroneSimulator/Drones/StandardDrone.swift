import Foundation

/// [v1.8.01] 經典 210mm 穿越練習機 (1:1 對齊 StandardDrone.kt)
struct StandardDrone: DroneModule {
    static let shared = StandardDrone()

    let id = "QUAD_STANDARD"
    let name = "SPECS_QUAD_NAME"
    let icon = "🚁"
    let hardwareSpecs = ModelHardwareSpecs(
        type: .mr,
        wheelbaseMm: 210,
        propDiameterInch: 5,
        propPitchInch: 4,
        motorKv: 2450,
        takeoffWeightKg: 0.6,
        payloadKg: 0.0,
        flightTimeMin: 8,
        description: "說明：高機動性機型，反應極其靈敏，適合練習精準穿越與反應速度。"
    )
    let groundOffset: Float = 0.08
    let visualOffset: Float = 0.0
    let collisionRadius: Float = 0.6
    let scale: Float = 0.7
    let shadowSizeBase: Float = 0.5
    let maxLandingSpeed: Float = 2.5

    let fpvFov: Float = 85.0
    let cameraVisualOffset: Float = 0.45
    let cameraHeightOffset: Float = 0.00

    let geometry: [DronePart] = {
        var parts: [DronePart] = [
            DronePart(tx: 0.0, ty: 0.0, tz: 0.0, w: 0.4, h: 0.15, d: 0.8, color: [0.12, 0.12, 0.12, 1.0]),
            DronePart(tx: 0.0, ty: 0.05, tz: 0.4, w: 0.25, h: 0.06, d: 0.12, color: [1.0, 0.4, 0.0, 1.0])
        ]
        let armPositions: [[Float]] = [
            [-0.45, 0.45, 0.0, 1.0, 0.0],
            [0.45, 0.45, 1.0, 0.0, 0.0],
            [-0.45, -0.45, 0.0, 1.0, 0.0],
            [0.45, -0.45, 1.0, 0.0, 0.0]
        ]
        for p in armPositions {
            let side: Float = (p[0] * p[1] > 0) ? 1.0 : -1.0
            parts.append(DronePart(tx: p[0] / 2.0, ty: 0.0, tz: p[1] / 2.0, w: 0.08, h: 0.04, d: 0.7, color: [0.18, 0.18, 0.18, 1.0], ry: 45.0 * side))
            parts.append(DronePart(tx: p[0], ty: 0.05, tz: p[1], w: 0.16, h: 0.16, d: 0.16, color: [p[2], p[3], p[4], 1.0]))
            parts.append(DronePart(tx: p[0], ty: 0.12, tz: p[1], w: 0.65, h: 0.01, d: 0.05, color: [0.95, 0.95, 0.95, 0.6], isPropeller: true, rotationDirection: side, baseRpm: 2000.0))
            parts.append(DronePart(tx: p[0], ty: 0.12, tz: p[1], w: 0.05, h: 0.01, d: 0.65, color: [0.95, 0.95, 0.95, 0.6], isPropeller: true, rotationDirection: side, baseRpm: 2000.0))
        }
        return parts
    }()

    let baseRate: Float = 1.2
    let baseExpo: Float = 0.2
    let physicsPower: Float = 42.0

    let soundProfile = SoundProfile(
        baseFreq: 220.0,
        harmonicPower: 0.3,
        isMultiMotor: true
    )
}
