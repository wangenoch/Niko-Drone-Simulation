package com.horizon.caadronesimulator.model

/**
 * [v1.5.9] 物理運動狀態緩衝區 (Purified Edition)
 * 修正：移除殭屍變數 motorRpmFactor，數據真源已移至渲染器與 DroneState。
 */
class DronePhysicsState(
    var posY: Float = 0f,
    var posX: Float = 0f,
    var posZ: Float = 0f,
    var yaw: Float = 0f,
    var visPitch: Float = 0f,
    var visRoll: Float = 0f,
    var velX: Float = 0f,
    var velY: Float = 0f,
    var velZ: Float = 0f,
    var flightTime: Float = 0f,
    var batteryVoltage: Float = 4.2f,
    var batteryPercent: Int = 100
) {
    fun reset(groundY: Float, initialZ: Float = 0f) {
        posY = groundY
        posX = 0f
        posZ = initialZ
        yaw = 0f
        visPitch = 0f
        visRoll = 0f
        velX = 0f
        velY = 0f
        velZ = 0f
        flightTime = 0f
        batteryVoltage = 4.2f
        batteryPercent = 100
    }
}
