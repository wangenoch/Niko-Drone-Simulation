import Foundation

/// [v1.8.01] 定點計時評測器 (1:1 對齊 SpotTimerEvaluator.kt)
enum SpotTimerEvaluator {
    static func update(state: DroneState, dt: Float, spec: DroneSpecs) {
        guard state.isSpotTimerEnabled, dt > 0 else { return }

        let isAirborne = state.altitude > spec.groundOffset + 0.1
        if state.isMotorLocked || state.isCollision || !isAirborne {
            resetTimer(state)
            state.spotTimerMessage = "IDLE"
            return
        }

        // 1. 旋轉偵測
        var yawDiff = abs(state.yaw - state.spotTimerLastYaw)
        state.spotTimerLastYaw = state.yaw
        if yawDiff > 180.0 { yawDiff = 360.0 - yawDiff }
        let yawRate = yawDiff / dt
        let isRotating = state.spotTimerStable ? (yawRate > 25.0) : (yawRate > 15.0)

        // 2. 高度範圍檢查 (1.0m ~ 3.5m)
        let relAlt = state.altitude - spec.groundOffset
        let isHeightOk = (1.0...3.5).contains(relAlt)

        // 3. 區域偵測 (7 角錐 + H 坪)
        let coneTargets = Constants.CONE_POSITIONS + [[0.0, 0.0]]
        var inZoneId = -1

        for idx in coneTargets.indices {
            let tx = coneTargets[idx][0]
            let tz = coneTargets[idx][1]
            let dist = sqrt((state.posX - tx) * (state.posX - tx) + (state.posZ - tz) * (state.posZ - tz))
            let isHPad = (idx == coneTargets.count - 1)
            let threshold: Float = isHPad ? 1.0 : 1.3
            if dist < threshold {
                inZoneId = idx
                break
            }
        }

        if inZoneId == -1 {
            state.spotTimerSeconds = 5.0
            state.spotTimerSuccess = false
            state.spotTimerMessageTimer = 0.0
            state.spotTimerMessage = "SEARCHING"
        } else if !isHeightOk {
            state.spotTimerSeconds = 5.0
            state.spotTimerSuccess = false
            state.spotTimerMessageTimer = 0.0
            state.spotTimerMessage = relAlt < 1.0
                ? String(format: "TOO_LOW|%.1f", relAlt)
                : String(format: "TOO_HIGH|%.1f", relAlt)
        } else {
            let isHPad = (inZoneId == coneTargets.count - 1)
            let currentYaw = (state.yaw.truncatingRemainder(dividingBy: 360.0) + 360.0).truncatingRemainder(dividingBy: 360.0)
            let targets: [Float] = [0.0, 90.0, 180.0, 270.0, 360.0]
            let yawThreshold: Float = isHPad ? 8.0 : 15.0

            var minDiff: Float = 360.0
            for target in targets {
                var diff = abs(currentYaw - target)
                if diff > 180.0 { diff = 360.0 - diff }
                if diff < minDiff { minDiff = diff }
            }
            let isAligned = minDiff <= yawThreshold

            if isRotating {
                state.spotTimerSeconds = 5.0
                state.spotTimerSuccess = false
                state.spotTimerMessageTimer = 0.0
                state.spotTimerMessage = "ROTATING"
            } else if !isAligned {
                state.spotTimerSeconds = 5.0
                state.spotTimerSuccess = false
                state.spotTimerMessageTimer = 0.0
                state.spotTimerMessage = isHPad
                    ? String(format: "YAW_ERROR_H|%.0f", minDiff)
                    : String(format: "YAW_ERROR_G|%.0f", minDiff)
            } else {
                if !state.spotTimerSuccess {
                    if state.spotTimerMessageTimer <= 0.0 {
                        state.spotTimerSeconds = max(state.spotTimerSeconds - dt, 0.0)
                        if state.spotTimerSeconds <= 0.0 {
                            state.spotTimerSuccess = true
                            state.spotTimerMessage = isHPad ? "PERFECT_H" : "SUCCESS_G"
                        } else {
                            state.spotTimerMessage = isHPad
                                ? String(format: "COUNTING_H|%.1f", state.spotTimerSeconds)
                                : String(format: "COUNTING_G|%.1f", state.spotTimerSeconds)
                        }
                    }
                } else {
                    state.spotTimerMessage = isHPad ? "PERFECT_H" : "SUCCESS_G"
                }
            }
            state.spotTimerStable = !isRotating && isAligned
        }

        state.spotTimerTargetId = inZoneId
        state.spotTimerInZone = (inZoneId != -1)
        state.spotTimerMessageTimer = max(state.spotTimerMessageTimer - dt, 0.0)
    }

    private static func resetTimer(_ state: DroneState) {
        state.spotTimerTargetId = -1
        state.spotTimerInZone = false
        state.spotTimerStable = false
        state.spotTimerSeconds = 5.0
        state.spotTimerMessageTimer = 0.0
    }
}
