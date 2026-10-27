import Foundation

/// [v1.8.01] 模擬器物理核心 - 穩定版 (1:1 對齊 PhysicsEngine.kt 與 AI_CONTEXT_SPECS.md 守護補丁)
enum PhysicsEngine {
    struct ControlInput {
        let throttle: Float
        let yaw: Float
        let pitch: Float
        let roll: Float
    }

    struct PhysicsResult {
        let isImpact: Bool
        let distanceH: Float
        let systemMessage: String?
        let motorRpm: Float
        let impactSpeed: Float
        let currentWindAngle: Float
    }

    struct AtmosConfig {
        let windLevel: Int
        let windDirection: String
        let windVariation: Int
        let windDirVariation: Int
        let enableVerticalDraft: Bool
        let useFlightLimit: Bool
        let randomWindPhase: Float
        let turbulencePhase: Float
        let randomDirAngle: Float
        let applyPhysicalSpecs: Bool
        let isMotorLocked: Bool
        let useHardcore: Bool
        let useStrictLanding: Bool
        let showObstacles: Bool
        let enableGroundEffect: Bool
        let enableNonCenterForce: Bool
        let isTetherModeEnabled: Bool
    }

    struct CollisionDetail {
        let isImpact: Bool
        let reason: String?
    }

    static var stepResult: PhysicsResult? = nil
    private static var isLatchedImpact: Bool = false
    private static var latchedSystemMsg: String? = nil
    private static var latchedImpactSpeed: Float = 0.0

    private static var smoothWindAccX: Float = 0.0
    private static var smoothWindAccZ: Float = 0.0
    private static var smoothVDraft: Float = 0.0

    static func clearState() {
        stepResult = nil
        isLatchedImpact = false
        latchedSystemMsg = nil
        latchedImpactSpeed = 0.0
        smoothWindAccX = 0.0
        smoothWindAccZ = 0.0
        smoothVDraft = 0.0
    }

    static func step(
        dt: Float,
        state: DronePhysicsState,
        input: ControlInput,
        atmos: AtmosConfig,
        droneType: String
    ) -> PhysicsResult {
        let spec = DroneRegistry.getSpec(droneType)
        let ds = DroneState.shared

        // 損毀鎖定保護
        if isLatchedImpact {
            let distH = sqrt(state.posX * state.posX + state.posZ * state.posZ)
            return PhysicsResult(
                isImpact: true,
                distanceH: distH,
                systemMessage: latchedSystemMsg,
                motorRpm: 0.0,
                impactSpeed: latchedImpactSpeed,
                currentWindAngle: ds.currentWindAngle
            )
        }

        let mass: Float = atmos.applyPhysicalSpecs ? spec.physicsMass : 1.0

        // 路跡判官系統 (Trajectory Judge)
        let entryPosY = state.posY
        let entryVelY = state.velY
        let entrySpeed = sqrt(state.velX * state.velX + state.velY * state.velY + state.velZ * state.velZ)
        let inertialNextY = entryPosY + entryVelY * dt

        var isPredictiveCrash = false
        var predictiveSystemMsg: String? = nil

        if entryVelY < -0.01 && inertialNextY <= spec.groundOffset + 0.001 {
            if atmos.useStrictLanding {
                let thresholdSafe = AppConfig.SystemDefaults.IMPACT_THRESHOLD_SAFE
                if entrySpeed > thresholdSafe {
                    isPredictiveCrash = true
                    let thresholdCrit = AppConfig.SystemDefaults.IMPACT_THRESHOLD_CRITICAL
                    predictiveSystemMsg = entrySpeed > thresholdCrit ? "CRASH_EXTREME" : "CRASH_STRUCTURAL"
                }
            }
        }

        // --- 1. 安全鎖 ---
        if atmos.isMotorLocked {
            state.velX = 0.0
            state.velY = 0.0
            state.velZ = 0.0
            state.visPitch = 0.0
            state.visRoll = 0.0
            if state.posY != spec.groundOffset { state.posY = spec.groundOffset }
            return PhysicsResult(
                isImpact: false,
                distanceH: sqrt(state.posX * state.posX + state.posZ * state.posZ),
                systemMessage: nil,
                motorRpm: 0.0,
                impactSpeed: 0.0,
                currentWindAngle: ds.currentWindAngle
            )
        }

        simulateBattery(dt: dt, state: state, useLimit: atmos.useFlightLimit, droneType: droneType)
        WindManager.update(currentTime: state.flightTime, level: atmos.windLevel, variation: atmos.windVariation, useHardcore: atmos.useHardcore)

        // --- 2. [v1.7.35] 垂直動力：中位基準穩定版 (Immutable Patches) ---
        let isAirborne = state.posY > spec.groundOffset + 0.005
        let rawThrottle = input.throttle

        // A. 基礎動力公式
        let baseLift = rawThrottle * 8.0

        // B. [Patch Guard 1] Idle Wakeup Patch (起飛喚醒補丁)
        let targetVelY: Float
        if !isAirborne && rawThrottle > 0.01 && ds.useIdleWakeupPatch {
            targetVelY = max(baseLift, 0.12)
        } else {
            targetVelY = baseLift
        }

        // C. [Patch Guard 2] Breakaway Guarantee (打破下溢死鎖)
        let breakawayAcc: Float = (!isAirborne && rawThrottle > 0.05) ? 0.15 : 0.0

        // D. 加速度計算與安全限幅
        let verticalAcc = (targetVelY - state.velY) * (5.0 / mass)
        state.velY += (verticalAcc + breakawayAcc) * dt

        // [v1.7.34 Hotfix] 起飛瞬時脈衝 (Escape Impulse)
        if !isAirborne && rawThrottle > 0.05 && state.velY < 0.1 {
            state.velY = 0.25
        }

        // [憲法級限速] 徹底杜絕數值爆炸
        state.velY = min(max(state.velY, -15.0), 15.0)

        // E. 繫留練習模式：高度天花板 (3.5m)
        if atmos.isTetherModeEnabled {
            let tetherMaxAlt = AppConfig.SystemDefaults.TETHER_MAX_ALTITUDE
            if state.posY > spec.groundOffset + tetherMaxAlt {
                if state.velY > 0 { state.velY = 0.0 }
            }
        }

        // 垂直氣流注入
        let vThreshold: Float = 0.7
        let currentAltAboveGround = state.posY - spec.groundOffset
        if atmos.enableVerticalDraft && currentAltAboveGround > vThreshold {
            let rawVDraft = WindManager.calculateVerticalDraft(
                level: atmos.windLevel,
                variation: atmos.windVariation,
                flightTime: state.flightTime,
                useHardcore: atmos.useHardcore,
                droneType: droneType,
                applySpecs: atmos.applyPhysicalSpecs
            )
            smoothVDraft += (rawVDraft - smoothVDraft) * 8.0 * dt
            let fadeFactor = min(max((currentAltAboveGround - vThreshold) / 0.5, 0.0), 1.0)
            let massComp: Float = atmos.applyPhysicalSpecs ? sqrt(mass) : 1.0
            let appliedVDraftAcc = min(max(smoothVDraft * 3.5 * massComp * fadeFactor, -5.0), 5.0)
            state.velY += appliedVDraftAcc * dt
        } else {
            smoothVDraft = 0.0
        }

        // 注入地面效應 (Ground Effect)
        if atmos.enableGroundEffect && !atmos.isMotorLocked {
            let h = max(state.posY - spec.groundOffset, 0.01)
            let d = max(5.0 * 0.0254, 0.1)
            let hoD = h / Float(d)
            if hoD < 1.0 {
                let geFactor = 1.0 + 0.15 * pow(1.0 - hoD, 2.0)
                state.velY += (geFactor - 1.0) * 5.0 * dt
            }
        }

        var nextY = state.posY + state.velY * dt
        let maxAlt = spec.groundOffset + AppConfig.SystemDefaults.MAX_ALTITUDE
        var systemMsg: String? = nil

        if atmos.isTetherModeEnabled {
            let tetherMaxAlt = AppConfig.SystemDefaults.TETHER_MAX_ALTITUDE
            if state.posY > spec.groundOffset + tetherMaxAlt - 0.05 {
                if systemMsg == nil { systemMsg = "TETHER_ALT_LIMIT" }
            }
        }

        if atmos.useFlightLimit && nextY > maxAlt {
            nextY = maxAlt
            state.velY = 0.0
            systemMsg = "ALT_LIMIT"
        }

        // --- 3. 姿態與旋轉 (CONTROL_SYSTEM_SPEC.md 憲法極性) ---
        if isAirborne {
            state.yaw -= input.yaw * 120.0 * dt
            if atmos.enableNonCenterForce {
                state.yaw += input.roll * 15.0 * dt
            }
        }

        let rad = state.yaw * .pi / 180.0
        let cosY = cos(rad)
        let sinY = sin(rad)
        let rollInput = input.roll
        let pitchInput = input.pitch

        if isAirborne {
            let restoreForce: Float = atmos.applyPhysicalSpecs ? spec.attitudeRestorationForce : 8.0
            state.visPitch += (pitchInput * 25.0 - state.visPitch) * restoreForce * dt
            state.visRoll += (rollInput * 25.0 - state.visRoll) * restoreForce * dt
            if atmos.enableNonCenterForce {
                state.visRoll += pitchInput * 5.0 * dt
            }
        } else {
            state.visPitch = 0.0
            state.visRoll = 0.0
        }

        // --- 4. 水平位移 ---
        let isHeli = (spec.category == .heli)
        let useAdvancedHeli = atmos.applyPhysicalSpecs && isHeli
        let smoothedRoll = useAdvancedHeli ? (state.visRoll / 25.0) : rollInput
        let smoothedPitch = useAdvancedHeli ? (state.visPitch / 25.0) : pitchInput

        let power: Float = atmos.applyPhysicalSpecs ? spec.physicsPower : 18.0
        let rawAccX = (-smoothedRoll * cosY + smoothedPitch * sinY) * power
        let rawAccZ = (smoothedRoll * sinY + smoothedPitch * cosY) * power

        var accX = rawAccX
        var accZ = rawAccZ

        if atmos.applyPhysicalSpecs && isAirborne {
            let bInertia = spec.brakingInertiaScale
            if (accX * state.velX) < 0 { accX /= bInertia }
            if (accZ * state.velZ) < 0 { accZ /= bInertia }
            if isHeli {
                accX += cos(rad) * 0.8
                accZ += sin(rad) * 0.8
            }
        }

        if isAirborne {
            state.velX += accX * dt
            state.velZ += accZ * dt
        }

        // 施加風力
        applyWind(dt: dt, state: state, atmos: atmos, mass: mass, groundOffset: spec.groundOffset)

        // 繫留練習模式：水平邊界判定 (彈簧拉力 + 速度截斷)
        if isAirborne && atmos.isTetherModeEnabled {
            let centerX: Float = 0.0
            let centerZ = AppConfig.SystemDefaults.TETHER_CENTER_Z
            let radiusLimit = AppConfig.SystemDefaults.TETHER_RADIUS_LIMIT

            let dx = state.posX - centerX
            let dz = state.posZ - centerZ
            let dist = sqrt(dx * dx + dz * dz)

            if dist > radiusLimit {
                let springConst: Float = 8.0
                let overDist = dist - radiusLimit
                state.velX -= (dx / dist) * overDist * springConst * dt
                state.velZ -= (dz / dist) * overDist * springConst * dt

                let dot = (state.velX * dx + state.velZ * dz)
                if dot > 0 {
                    state.velX *= 0.5
                    state.velZ *= 0.5
                }
            }
        }

        // [Patch Guard 3] Ground Lock (L5 風偏修復)
        let isTrulyAirborne = state.posY > spec.groundOffset + 0.05
        let isTakeoffAttempt = rawThrottle > -0.8
        if !isTrulyAirborne && !isTakeoffAttempt {
            state.velX = 0.0
            state.velZ = 0.0
        }

        // --- 6. 阻尼與積分 ---
        let damping: Float = atmos.applyPhysicalSpecs ? spec.physicsDamping : 0.92
        let dampFactor = min(max(1.0 - (1.0 - damping) * 60.0 * dt, 0.0), 1.0)
        state.velX *= dampFactor
        state.velZ *= dampFactor

        let nextX = state.posX + state.velX * dt
        let nextZ = state.posZ + state.velZ * dt

        // --- 7. 碰撞與警告 ---
        let collisionResult = checkCollisionDetail(
            type: droneType,
            x: nextX,
            y: nextY,
            z: nextZ,
            p: state.visPitch,
            r: state.visRoll,
            useStrict: atmos.useStrictLanding,
            showObstacles: atmos.showObstacles
        )
        var isHardLanding = false

        if atmos.isTetherModeEnabled {
            let radiusLimit = AppConfig.SystemDefaults.TETHER_RADIUS_LIMIT
            if sqrt(nextX * nextX + nextZ * nextZ) >= radiusLimit - 0.05 {
                if systemMsg == nil { systemMsg = "TETHER_DIST_LIMIT" }
            }
            if nextY <= spec.groundOffset + 0.01 {
                if systemMsg == nil { systemMsg = "TETHER_GROUND_LIMIT" }
            }
        }

        if isPredictiveCrash {
            isHardLanding = true
            state.posY = spec.groundOffset
            state.velY = 0.0; state.velX = 0.0; state.velZ = 0.0
            if systemMsg == nil { systemMsg = predictiveSystemMsg }
        } else if collisionResult.isImpact {
            state.posY = spec.groundOffset
            state.velY = 0.0; state.velX = 0.0; state.velZ = 0.0
            if systemMsg == nil { systemMsg = collisionResult.reason }
        } else if nextY <= spec.groundOffset + 0.001 {
            state.posY = spec.groundOffset
            state.velY = 0.0; state.velX = 0.0; state.velZ = 0.0
        } else {
            state.posY = nextY
            state.posX = nextX
            state.posZ = nextZ
        }

        let isImpact = collisionResult.isImpact || isHardLanding
        if isImpact {
            isLatchedImpact = true
            latchedSystemMsg = systemMsg
            latchedImpactSpeed = entrySpeed
        }

        let res = PhysicsResult(
            isImpact: isImpact,
            distanceH: sqrt(state.posX * state.posX + state.posZ * state.posZ),
            systemMessage: systemMsg,
            motorRpm: max(0.0, (input.throttle + 1.0) / 2.0),
            impactSpeed: entrySpeed,
            currentWindAngle: ds.currentWindAngle
        )
        stepResult = res
        return res
    }

    private static func applyWind(dt: Float, state: DronePhysicsState, atmos: AtmosConfig, mass: Float, groundOffset: Float) {
        if state.posY <= groundOffset + 0.01 { return }
        let heightFactor: Float = atmos.useHardcore ? WindManager.calculateHeightFactor(altitude: state.posY, groundY: groundOffset) : 1.0
        let wResult = WindManager.calculateWindVector(
            level: atmos.windLevel,
            direction: atmos.windDirection,
            variation: atmos.windVariation,
            dirVariation: atmos.windDirVariation,
            flightTime: state.flightTime,
            randomWindAngle: DroneState.shared.randomWindAngle
        )
        let wVec = wResult.forceVector
        smoothWindAccX += (wVec[0] * 1.5 * heightFactor - smoothWindAccX) * 8.0 * dt
        smoothWindAccZ += (wVec[1] * 1.5 * heightFactor - smoothWindAccZ) * 8.0 * dt
        state.velX += (smoothWindAccX / mass) * dt
        state.velZ += (smoothWindAccZ / mass) * dt
    }

    private static func simulateBattery(dt: Float, state: DronePhysicsState, useLimit: Bool, droneType: String) {
        state.flightTime += dt
        if !useLimit {
            state.batteryVoltage = 4.2
            state.batteryPercent = 100
            return
        }
        let spec = DroneRegistry.getSpec(droneType)
        let totalSeconds = max(Float(spec.flightTimeMin) * 60.0, 60.0)
        let drain = (1.0 / totalSeconds) * dt
        state.batteryVoltage = max(state.batteryVoltage - drain, 3.2)
        state.batteryPercent = Int(((state.batteryVoltage - 3.2) / (4.2 - 3.2)) * 100.0)
    }

    private static func checkCollisionDetail(
        type: String,
        x: Float,
        y: Float,
        z: Float,
        p: Float,
        r: Float,
        useStrict: Bool,
        showObstacles: Bool
    ) -> CollisionDetail {
        let spec = DroneRegistry.getSpec(type)
        let mt = max(abs(p), abs(r))

        if showObstacles {
            for obs in Constants.OBSTACLES {
                let obsX = obs[0]; let obsZ = obs[1]; let obsH = obs[2]; let obsR = obs[4]
                let dist = sqrt((x - obsX) * (x - obsX) + (z - obsZ) * (z - obsZ))
                if dist < (spec.collisionRadius + obsR) && y < obsH {
                    return CollisionDetail(isImpact: true, reason: "COLLISION_OBJECT")
                }
            }
        }

        if useStrict {
            let tiltRad = mt * (.pi / 180.0)
            let tiltOffset = spec.collisionRadius * sin(tiltRad)
            let effectiveBottom = y - tiltOffset
            if effectiveBottom < 0.05 && mt > 15.0 {
                return CollisionDetail(isImpact: true, reason: "CRASH_FLIPPED")
            }
        }

        for cone in Constants.CONE_POSITIONS {
            let distSq = (x - cone[0]) * (x - cone[0]) + (z - cone[1]) * (z - cone[1])
            let thresholdSq = (spec.collisionRadius * 1.2) * (spec.collisionRadius * 1.2)
            if distSq < thresholdSq && (y - spec.groundOffset) < 0.8 {
                return CollisionDetail(isImpact: true, reason: "COLLISION_CONE")
            }
        }

        let isOutOfBounds = abs(x) > Constants.FIELD_WIDTH_HALF || z < Constants.FIELD_Z_BACK || z > Constants.FIELD_Z_FRONT
        if isOutOfBounds {
            return CollisionDetail(isImpact: true, reason: "CRASH_OUT_OF_BOUNDS")
        }

        let isFlippedOnGround = y < spec.groundOffset * 0.5 && mt > 10.0
        if isFlippedOnGround {
            return CollisionDetail(isImpact: true, reason: "CRASH_FLIPPED")
        }

        return CollisionDetail(isImpact: false, reason: nil)
    }

    static func isNearBoundary(x: Float, z: Float) -> Bool {
        let b = Constants.WARNING_BUFFER
        return abs(x) > (Constants.FIELD_WIDTH_HALF - b) || z < (Constants.FIELD_Z_BACK + b) || z > (Constants.FIELD_Z_FRONT - b)
    }
}
