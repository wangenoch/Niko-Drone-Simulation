import Foundation

enum DroneCategory: String, CaseIterable {
    case multiRotor = "MULTI_ROTOR"
    case heli = "HELI"
    case fixedWing = "FIXED_WING"
}

enum DroneType: String {
    case mr = "MR"
    case srh = "SRH"
    case fw = "FW"
    case vtol = "VTOL"
}

struct SoundProfile: Equatable {
    var baseFreq: Float = 160.0
    var harmonicPower: Float = 0.4
    var noiseFactor: Float = 0.05
    var isMultiMotor: Bool = true
    var modulationHz: Float = 0.0
}

struct ModelHardwareSpecs {
    var type: DroneType
    var wheelbaseMm: Int = 0
    var propDiameterInch: Int = 0
    var propPitchInch: Int = 0
    var motorKv: Int = 0
    var takeoffWeightKg: Float = 0.0
    var payloadKg: Float = 0.0
    var flightTimeMin: Int = 0
    var description: String = ""
}

struct DronePart: Identifiable {
    let id = UUID()
    var tx: Float = 0.0
    var ty: Float = 0.0
    var tz: Float = 0.0
    var w: Float = 0.1
    var h: Float = 0.1
    var d: Float = 0.1
    var color: [Float] = [0.12, 0.12, 0.12, 1.0]
    var ry: Float = 0.0
    var rx: Float = 0.0
    var rz: Float = 0.0
    var isPropeller: Bool = false
    var isTailPropeller: Bool = false
    var rotationDirection: Float = 1.0
    var baseRpm: Float = 2000.0
}

protocol DroneModule {
    var id: String { get }
    var name: String { get }
    var icon: String { get }
    var hardwareSpecs: ModelHardwareSpecs { get }
    var groundOffset: Float { get }
    var visualOffset: Float { get }
    var collisionRadius: Float { get }
    var scale: Float { get }
    var shadowSizeBase: Float { get }
    var maxLandingSpeed: Float { get }
    var fpvFov: Float { get }
    var cameraVisualOffset: Float { get }
    var cameraHeightOffset: Float { get }
    var isHoldSupported: Bool { get }
    var geometry: [DronePart] { get }
    var baseRate: Float { get }
    var baseExpo: Float { get }
    var baseRateT_Up: Float { get }
    var baseRateT_Down: Float { get }
    var baseRateY: Float { get }
    var baseRateP: Float { get }
    var baseRateR: Float { get }
    var physicsMass: Float { get }
    var physicsPower: Float { get }
    var physicsDamping: Float { get }
    var attitudeRestorationForce: Float { get }
    var brakingInertiaScale: Float { get }
    var soundProfile: SoundProfile { get }
}

extension DroneModule {
    var isHoldSupported: Bool { false }
    var baseRateT_Up: Float { 1.0 }
    var baseRateT_Down: Float { 1.0 }
    var baseRateY: Float { 1.0 }
    var baseRateP: Float { 1.0 }
    var baseRateR: Float { 1.0 }
    var physicsMass: Float { 1.0 }
    var physicsDamping: Float { 0.92 }
    var attitudeRestorationForce: Float { 8.0 }
    var brakingInertiaScale: Float { 1.0 }
}

struct DroneSpecs: Identifiable {
    let id: String
    let name: String
    let category: DroneCategory
    let groundOffset: Float
    let visualOffset: Float
    let collisionRadius: Float
    let scale: Float
    let shadowSizeBase: Float
    let icon: String
    let physicsMass: Float
    let physicsPower: Float
    let physicsDamping: Float
    let fpvFov: Float
    let cameraVisualOffset: Float
    let cameraHeightOffset: Float
    let isHoldSupported: Bool
    let maxLandingSpeed: Float
    let flightTimeMin: Int
    let baseRate: Float
    let baseExpo: Float
    let attitudeRestorationForce: Float
    let brakingInertiaScale: Float
}

enum DroneRegistry {
    static let models: [DroneModule] = [
        StandardDrone.shared,
        T4HeavyLift.shared,
        Heli900.shared
    ]

    static let specsMap: [String: DroneSpecs] = {
        var map: [String: DroneSpecs] = [:]
        for m in models {
            map[m.id] = DroneSpecs(
                id: m.id,
                name: m.name,
                category: m.hardwareSpecs.type == .srh ? .heli : .multiRotor,
                groundOffset: m.groundOffset,
                visualOffset: m.visualOffset,
                collisionRadius: m.collisionRadius,
                scale: m.scale,
                shadowSizeBase: m.shadowSizeBase,
                icon: m.icon,
                physicsMass: m.physicsMass,
                physicsPower: m.physicsPower,
                physicsDamping: m.physicsDamping,
                fpvFov: m.fpvFov,
                cameraVisualOffset: m.cameraVisualOffset,
                cameraHeightOffset: m.cameraHeightOffset,
                isHoldSupported: m.isHoldSupported,
                maxLandingSpeed: m.maxLandingSpeed,
                flightTimeMin: m.hardwareSpecs.flightTimeMin,
                baseRate: m.baseRate,
                baseExpo: m.baseExpo,
                attitudeRestorationForce: m.attitudeRestorationForce,
                brakingInertiaScale: m.brakingInertiaScale
            )
        }
        return map
    }()

    static func getAllSpecs() -> [DroneSpecs] {
        models.compactMap { specsMap[$0.id] }
    }

    static func getSpec(_ id: String) -> DroneSpecs {
        specsMap[id] ?? specsMap[StandardDrone.shared.id]!
    }

    static func getModule(_ id: String) -> DroneModule {
        models.first(where: { $0.id == id }) ?? StandardDrone.shared
    }
}
