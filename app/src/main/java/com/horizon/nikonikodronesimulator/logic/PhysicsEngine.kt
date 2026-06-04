package com.horizon.nikonikodronesimulator.logic

import com.horizon.nikonikodronesimulator.model.DronePhysicsState
import com.horizon.nikonikodronesimulator.model.DroneRegistry
import kotlin.math.*

/**
 * [v1.7.31] 模擬器物理核心 - 穩定版 (Force-based Stabilization)
 */
object PhysicsEngine {
    var stepResult: PhysicsResult? = null
    
    // [v1.7.9.19] 損毀狀態自旋鎖：一旦判定撞擊，強制保持損毀狀態直到重置
    private var isLatchedImpact = false
    private var latchedSystemMsg: String? = null
    private var latchedImpactSpeed = 0f

    /** [v1.7.6] 重置引擎狀態：徹底清除上一次的運算結果，防止無限碰撞遞迴 */
    fun clearState() {
        stepResult = null
        isLatchedImpact = false
        latchedSystemMsg = null
        latchedImpactSpeed = 0f
    }

    data class AtmosConfig(
        val windLevel: Int,
        val windDirection: String,
        val windVariation: Int,
        val windDirVariation: Int,
        val enableVerticalDraft: Boolean,
        val useFlightLimit: Boolean,
        val randomWindPhase: Float,
        val turbulencePhase: Float,
        val randomDirAngle: Float,
        val applyPhysicalSpecs: Boolean,
        val isMotorLocked: Boolean,
        val useHardcore: Boolean,
        val useStrictLanding: Boolean,
        val showObstacles: Boolean,
        val enableGroundEffect: Boolean = false,
        val enableNonCenterForce: Boolean = false,
        val isTetherModeEnabled: Boolean = false
    )

    // [v1.7.7] 物理平滑緩衝區
    private var smoothWindAccX = 0f
    private var smoothWindAccZ = 0f
    private var smoothVDraft = 0f

    fun step(dt: Float, state: DronePhysicsState, input: PhysicsEngine.ControlInput, atmos: AtmosConfig, droneType: String): PhysicsResult {
        val spec = DroneRegistry.getSpec(droneType)
        
        // [v1.7.9.19] 鎖定保護：若已處於損毀鎖定狀態，直接回傳鎖定結果，拒絕任何新計算
        if (isLatchedImpact) {
            return PhysicsResult(true, sqrt(state.posX.pow(2) + state.posZ.pow(2)), latchedSystemMsg, 0f, latchedImpactSpeed)
        }

        val mass = if (atmos.applyPhysicalSpecs) spec.physicsMass else 1.0f
        
        // [v1.7.9.18] 路跡判官系統 (Trajectory Judge)
        val entryPosY = state.posY
        val entryVelY = state.velY
        val entrySpeed = sqrt(state.velX.pow(2) + state.velY.pow(2) + state.velZ.pow(2))
        
        val inertialNextY = entryPosY + entryVelY * dt
        
        var isPredictiveCrash = false
        var predictiveSystemMsg: String? = null
        
        if (entryVelY < -0.01f && inertialNextY <= spec.groundOffset + 0.001f) {
            if (atmos.useStrictLanding) {
                val thresholdSafe = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.IMPACT_THRESHOLD_SAFE
                if (entrySpeed > thresholdSafe) {
                    isPredictiveCrash = true
                    val thresholdCrit = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.IMPACT_THRESHOLD_CRITICAL
                    predictiveSystemMsg = if (entrySpeed > thresholdCrit) "CRASH_EXTREME" else "CRASH_STRUCTURAL"
                }
            }
        }

        // --- 1. [1:1 Git] 安全鎖 ---
        if (atmos.isMotorLocked) {
            state.velX = 0f; state.velY = 0f; state.velZ = 0f
            state.visPitch = 0f; state.visRoll = 0f
            if (state.posY != spec.groundOffset) state.posY = spec.groundOffset
            return PhysicsResult(false, sqrt(state.posX.pow(2) + state.posZ.pow(2)), null, 0f)
        }

        simulateBattery(dt, state, atmos.useFlightLimit, droneType)
        WindManager.update(state.flightTime, atmos.windLevel, atmos.windVariation, atmos.useHardcore)

        // --- 2. [v1.7.31] 垂直動力：中位基準穩定版 ---
        val isAirborne = state.posY > spec.groundOffset + 0.005f
        val rawThrottle = input.throttle
        val ds = com.horizon.nikonikodronesimulator.model.DroneState.getInstance()
        
        // A. 基礎動力公式 (線性回歸)
        val baseLift = rawThrottle * 8.0f
        
        // B. [Patch Guard] 起飛喚醒補丁：適配中位 (0.01)
        // [v1.7.35] 改用 max() 融合邏輯：確保 A55 保底升力的同時，不抑制重型機的大推力
        val targetVelY = if (!isAirborne && rawThrottle > 0.01f && ds.useIdleWakeupPatch) {
            max(baseLift, 0.12f)
        } else {
            baseLift 
        }

        // C. [A55 專修] 打破下溢死鎖：適配中位 (0.05)
        val breakawayAcc = if (!isAirborne && rawThrottle > 0.05f) 0.15f else 0f

        // D. 加速度計算與安全限幅
        val verticalAcc = (targetVelY - state.velY) * (5.0f / mass)
        state.velY += (verticalAcc + breakawayAcc) * dt
        
        // [v1.7.34 Hotfix] 起飛瞬時脈衝 (Escape Impulse)
        // 核心邏輯：在地面推桿起飛的瞬間，直接給予一個初始向上速度，徹底打破 A55 數值下溢與地面黏滯感。
        if (!isAirborne && rawThrottle > 0.05f && state.velY < 0.1f) {
            state.velY = 0.25f // 給予 0.25 m/s 的起始「脫離力」
        }
        
        // [憲法級限速] 徹底杜絕 58m/s 數值爆炸
        state.velY = state.velY.coerceIn(-15f, 15f)
        
        // E. [v1.7.25] 繫留練習模式：高度天花板 (3.5m)
        if (atmos.isTetherModeEnabled) {
            val tetherMaxAlt = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.TETHER_MAX_ALTITUDE
            if (state.posY > spec.groundOffset + tetherMaxAlt) {
                if (state.velY > 0) state.velY = 0f
            }
        }
        
        // 垂直氣流注入
        val vThreshold = 0.7f
        val currentAltAboveGround = state.posY - spec.groundOffset
        if (atmos.enableVerticalDraft && currentAltAboveGround > vThreshold) {
            val rawVDraft = WindManager.calculateVerticalDraft(atmos.windLevel, atmos.windVariation, state.flightTime, atmos.useHardcore, droneType, atmos.applyPhysicalSpecs)
            smoothVDraft += (rawVDraft - smoothVDraft) * 8f * dt
            val fadeFactor = ((currentAltAboveGround - vThreshold) / 0.5f).coerceIn(0f, 1.0f)
            val massComp = if (atmos.applyPhysicalSpecs) sqrt(mass.toDouble()).toFloat() else 1.0f
            val appliedVDraftAcc = (smoothVDraft * 3.5f * massComp * fadeFactor).coerceIn(-5.0f, 5.0f)
            state.velY += appliedVDraftAcc * dt
        } else {
            smoothVDraft = 0f
        }
        
        // [v1.7.24] 注入地面效應 (Ground Effect)
        if (atmos.enableGroundEffect && !atmos.isMotorLocked) {
            val h = (state.posY - spec.groundOffset).coerceAtLeast(0.01f)
            val d = (5f * 0.0254f).coerceAtLeast(0.1f) 
            val hoD = h / d
            if (hoD < 1.0f) {
                val geFactor = 1.0f + 0.15f * (1.0f - hoD).pow(2)
                state.velY += (geFactor - 1.0f) * 5.0f * dt
            }
        }

        var nextY = state.posY + state.velY * dt
        val maxAlt = spec.groundOffset + com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.MAX_ALTITUDE
        var systemMsg: String? = null

        // [v1.7.31] 繫留練習模式：高度天花板 (3.5m) 警告注入
        if (atmos.isTetherModeEnabled) {
            val tetherMaxAlt = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.TETHER_MAX_ALTITUDE
            if (state.posY > spec.groundOffset + tetherMaxAlt - 0.05f) {
                if (systemMsg == null) systemMsg = "TETHER_ALT_LIMIT"
            }
        }

        if (atmos.useFlightLimit && nextY > maxAlt) { 
            nextY = maxAlt; state.velY = 0f; systemMsg = "ALT_LIMIT"
        }

        // --- 3. [1:1 Git] 姿態與旋轉 ---
        if (isAirborne) {
            state.yaw -= input.yaw * 120.0f * dt
            if (atmos.enableNonCenterForce) {
                state.yaw += input.roll * 15.0f * dt
            }
        }
        
        val rad = Math.toRadians(state.yaw.toDouble()).toFloat()
        val cosY = cos(rad); val sinY = sin(rad)
        val rollInput = input.roll; val pitchInput = input.pitch
        
        if (isAirborne) {
            val restoreForce = if (atmos.applyPhysicalSpecs) spec.attitudeRestorationForce else 8.0f
            state.visPitch += (pitchInput * 25f - state.visPitch) * restoreForce * dt
            state.visRoll += (rollInput * 25f - state.visRoll) * restoreForce * dt
            if (atmos.enableNonCenterForce) {
                state.visRoll += pitchInput * 5.0f * dt
            }
        } else {
            state.visPitch = 0f; state.visRoll = 0f
        }

        // --- 4. [1:1 Git] 水平位移 ---
        val isHeli = spec.category == com.horizon.nikonikodronesimulator.model.DroneCategory.HELI
        val useAdvancedHeli = atmos.applyPhysicalSpecs && isHeli
        val smoothedRoll = if (useAdvancedHeli) state.visRoll / 25f else rollInput
        val smoothedPitch = if (useAdvancedHeli) state.visPitch / 25f else pitchInput

        val rawAccX = (-smoothedRoll * cosY + smoothedPitch * sinY) * (if (atmos.applyPhysicalSpecs) spec.physicsPower else 18.0f)
        val rawAccZ = (smoothedRoll * sinY + smoothedPitch * cosY) * (if (atmos.applyPhysicalSpecs) spec.physicsPower else 18.0f)
        
        var accX = rawAccX
        var accZ = rawAccZ

        if (atmos.applyPhysicalSpecs && isAirborne) {
            val bInertia = spec.brakingInertiaScale
            if ((accX * state.velX) < 0) accX /= bInertia
            if ((accZ * state.velZ) < 0) accZ /= bInertia
            if (isHeli) {
                val driftRad = Math.toRadians(state.yaw.toDouble()).toFloat()
                accX += cos(driftRad) * 0.8f 
                accZ += sin(driftRad) * 0.8f
            }
        }
        
        if (isAirborne) {
            state.velX += accX * dt
            state.velZ += accZ * dt
        }
        
        // 優先施加風力
        applyWind(dt, state, atmos, mass, spec.groundOffset)

        // [v1.7.31] 繫留練習模式：水平邊界判定
        if (isAirborne && atmos.isTetherModeEnabled) {
            val centerX = 0f
            val centerZ = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.TETHER_CENTER_Z
            val radiusLimit = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.TETHER_RADIUS_LIMIT
            
            val dx = state.posX - centerX
            val dz = state.posZ - centerZ
            val dist = sqrt(dx * dx + dz * dz)
            
            if (dist > radiusLimit) {
                val springConst = 8.0f
                val overDist = dist - radiusLimit
                state.velX -= (dx / dist) * overDist * springConst * dt
                state.velZ -= (dz / dist) * overDist * springConst * dt
                
                val dot = (state.velX * dx + state.velZ * dz)
                if (dot > 0) {
                    state.velX *= 0.5f
                    state.velZ *= 0.5f
                }
            }
        }

        // 地面鎖定 (Ground Lock)
        val isTrulyAirborne = state.posY > spec.groundOffset + 0.05f
        val isTakeoffAttempt = rawThrottle > -0.8f
        if (!isTrulyAirborne && !isTakeoffAttempt) {
            state.velX = 0f; state.velZ = 0f
        }

        // --- 6. [1:1 Git] 阻尼與積分 ---
        val damping = if (atmos.applyPhysicalSpecs) spec.physicsDamping else 0.92f
        state.velX *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
        state.velZ *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)

        val nextX = state.posX + state.velX * dt
        val nextZ = state.posZ + state.velZ * dt
        
        // --- 7. 碰撞與警告 ---
        val collisionResult = checkCollisionDetail(droneType, nextX, nextY, nextZ, state.visPitch, state.visRoll, atmos.useStrictLanding, atmos.showObstacles)
        var isHardLanding = false
        
        if (atmos.isTetherModeEnabled) {
            val radiusLimit = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.TETHER_RADIUS_LIMIT
            if (sqrt(nextX * nextX + nextZ * nextZ) >= radiusLimit - 0.05f) {
                if (systemMsg == null) systemMsg = "TETHER_DIST_LIMIT"
            }
            if (nextY <= spec.groundOffset + 0.01f) {
                if (systemMsg == null) systemMsg = "TETHER_GROUND_LIMIT"
            }
        }

        if (isPredictiveCrash) {
            isHardLanding = true
            state.posY = spec.groundOffset; state.velY = 0f; state.velX = 0f; state.velZ = 0f
            if (systemMsg == null) systemMsg = predictiveSystemMsg
        } else if (collisionResult.isImpact) {
            state.posY = spec.groundOffset; state.velY = 0f; state.velX = 0f; state.velZ = 0f
            if (systemMsg == null) systemMsg = collisionResult.reason
        } else if (nextY <= spec.groundOffset + 0.001f) {
            state.posY = spec.groundOffset; state.velY = 0f; state.velX = 0f; state.velZ = 0f
        } else {
            state.posY = nextY; state.posX = nextX; state.posZ = nextZ
        }

        val isImpact = collisionResult.isImpact || isHardLanding
        if (isImpact) { isLatchedImpact = true; latchedSystemMsg = systemMsg; latchedImpactSpeed = entrySpeed }
        
        val res = PhysicsResult(isImpact = isImpact, distanceH = sqrt(state.posX.pow(2) + state.posZ.pow(2)), systemMessage = systemMsg, motorRpm = (input.throttle + 1f) / 2f, impactSpeed = entrySpeed, currentWindAngle = ds.env.currentWindAngle)
        stepResult = res
        return res
    }

    private fun applyWind(dt: Float, state: DronePhysicsState, atmos: AtmosConfig, mass: Float, groundOffset: Float) {
        if (state.posY <= groundOffset + 0.01f) return
        val heightFactor = if (atmos.useHardcore) WindManager.calculateHeightFactor(state.posY, groundOffset) else 1.0f
        val wResult = WindManager.calculateWindVector(atmos.windLevel, atmos.windDirection, atmos.windVariation, atmos.windDirVariation, state.flightTime, com.horizon.nikonikodronesimulator.model.DroneState.getInstance().env.randomWindAngle)
        val wVec = wResult.forceVector
        smoothWindAccX += (wVec[0] * 1.5f * heightFactor - smoothWindAccX) * 8f * dt
        smoothWindAccZ += (wVec[1] * 1.5f * heightFactor - smoothWindAccZ) * 8f * dt
        state.velX += (smoothWindAccX / mass) * dt
        state.velZ += (smoothWindAccZ / mass) * dt
    }

    private fun simulateBattery(dt: Float, state: DronePhysicsState, useLimit: Boolean, droneType: String) {
        state.flightTime += dt 
        if (!useLimit) { state.batteryVoltage = 4.2f; state.batteryPercent = 100; return }
        val spec = DroneRegistry.getSpec(droneType)
        val totalSeconds = (spec.flightTimeMin.toFloat() * 60f).coerceAtLeast(60f)
        val drain = (1.0f / totalSeconds) * dt
        state.batteryVoltage = (state.batteryVoltage - drain).coerceAtLeast(3.2f)
        state.batteryPercent = ((state.batteryVoltage - 3.2f) / (4.2f - 3.2f) * 100).toInt()
    }

    data class CollisionDetail(val isImpact: Boolean, val reason: String? = null)

    private fun checkCollisionDetail(type: String, x: Float, y: Float, z: Float, p: Float, r: Float, useStrict: Boolean, showObstacles: Boolean = false): CollisionDetail {
        val spec = DroneRegistry.getSpec(type)
        val mt = max(abs(p), abs(r))
        if (showObstacles) {
            for (obs in com.horizon.nikonikodronesimulator.model.Constants.OBSTACLES) {
                val obsX = obs[0]; val obsZ = obs[1]; val obsH = obs[2]; val obsR = obs[4]
                val dist = sqrt((x - obsX).toDouble().pow(2) + (z - obsZ).toDouble().pow(2))
                if (dist < (spec.collisionRadius + obsR) && y < obsH) return CollisionDetail(true, "COLLISION_OBJECT")
            }
        }
        if (useStrict) {
            val tiltRad = mt * (PI.toFloat() / 180f)
            val tiltOffset = spec.collisionRadius * sin(tiltRad)
            val effectiveBottom = y - tiltOffset
            if (effectiveBottom < 0.05f && mt > 15f) return CollisionDetail(true, "CRASH_FLIPPED")
        }
        for (cone in com.horizon.nikonikodronesimulator.model.Constants.CONE_POSITIONS) {
            val distSq = (x - cone[0]).pow(2) + (z - cone[1]).pow(2)
            val thresholdSq = (spec.collisionRadius * 1.2f).pow(2)
            if (distSq < thresholdSq && (y - spec.groundOffset) < 0.8f) return CollisionDetail(true, "COLLISION_CONE")
        }
        val isOutOfBounds = abs(x) > com.horizon.nikonikodronesimulator.model.Constants.FIELD_WIDTH_HALF || z < com.horizon.nikonikodronesimulator.model.Constants.FIELD_Z_BACK || z > com.horizon.nikonikodronesimulator.model.Constants.FIELD_Z_FRONT
        if (isOutOfBounds) return CollisionDetail(true, "CRASH_OUT_OF_BOUNDS")
        val isFlippedOnGround = y < spec.groundOffset * 0.5f && mt > 10f
        if (isFlippedOnGround) return CollisionDetail(true, "CRASH_FLIPPED")
        return CollisionDetail(false)
    }

    fun isNearBoundary(x: Float, z: Float): Boolean {
        val b = com.horizon.nikonikodronesimulator.model.Constants.WARNING_BUFFER
        return abs(x) > (com.horizon.nikonikodronesimulator.model.Constants.FIELD_WIDTH_HALF - b) || z < (com.horizon.nikonikodronesimulator.model.Constants.FIELD_Z_BACK + b) || z > (com.horizon.nikonikodronesimulator.model.Constants.FIELD_Z_FRONT - b)
    }

    data class ControlInput(val throttle: Float, val yaw: Float, val pitch: Float, val roll: Float)
    data class PhysicsResult(val isImpact: Boolean, val distanceH: Float, val systemMessage: String?, val motorRpm: Float, val impactSpeed: Float = 0f, val currentWindAngle: Float = 0f)
}
