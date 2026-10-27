import Foundation

/// [v1.8.01] 物理運動狀態緩衝區 (Purified Edition)
final class DronePhysicsState {
    var posY: Float = 0.0
    var posX: Float = 0.0
    var posZ: Float = 0.0
    var yaw: Float = 0.0
    var visPitch: Float = 0.0
    var visRoll: Float = 0.0
    var velX: Float = 0.0
    var velY: Float = 0.0
    var velZ: Float = 0.0
    var flightTime: Float = 0.0
    var batteryVoltage: Float = 4.2
    var batteryPercent: Int = 100

    init(posY: Float = 0.08, posX: Float = 0.0, posZ: Float = 0.0) {
        self.posY = posY
        self.posX = posX
        self.posZ = posZ
    }

    func reset(groundY: Float, initialZ: Float = 0.0) {
        posY = groundY
        posX = 0.0
        posZ = initialZ
        yaw = 0.0
        visPitch = 0.0
        visRoll = 0.0
        velX = 0.0
        velY = 0.0
        velZ = 0.0
        flightTime = 0.0
        batteryVoltage = 4.2
        batteryPercent = 100
    }
}
