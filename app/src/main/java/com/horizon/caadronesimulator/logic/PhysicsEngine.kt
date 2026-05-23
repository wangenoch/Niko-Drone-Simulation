package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.DronePhysicsState
import com.horizon.caadronesimulator.model.DroneRegistry
import kotlin.math.*

/**
 * [v1.5.9] 模擬器物理核心 - Git 憲法級 1:1 還原版
 * 修正：完全復刻 b02b6fd 動力公式，保全天氣系統，徹底對齊視覺低頭與物理前進。
 */
object PhysicsEngine {
    var stepResult: PhysicsResult? = null

    /** [v1.7.6] 重置引擎狀態：徹底清除上一次的運算結果，防止無限碰撞遞迴 */
    fun clearState() {
        stepResult = null
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
        val showObstacles: Boolean
    )

    // [v1.7.7] 物理平滑緩衝區
    private var smoothWindAccX = 0f
    private var smoothWindAccZ = 0f
    private var smoothVDraft = 0f

    fun step(dt: Float, state: DronePhysicsState, input: PhysicsEngine.ControlInput, atmos: AtmosConfig, droneType: String): PhysicsResult {
        val spec = DroneRegistry.getSpec(droneType)
        val mass = if (atmos.applyPhysicalSpecs) spec.physicsMass else 1.0f
        
        // --- 1. [1:1 Git] 安全鎖 ---
        if (atmos.isMotorLocked) {
            state.velX = 0f; state.velY = 0f; state.velZ = 0f
            state.visPitch = 0f; state.visRoll = 0f
            if (state.posY != spec.groundOffset) state.posY = spec.groundOffset
            return PhysicsResult(false, sqrt(state.posX.pow(2) + state.posZ.pow(2)), null, 0f)
        }

        simulateBattery(dt, state, atmos.useFlightLimit, droneType)
        WindManager.update(state.flightTime, atmos.windLevel, atmos.windVariation, atmos.useHardcore)

        // --- 2. [v1.7.7] 垂直動力：二階平滑物理模型 ---
        val isAirborne = state.posY > spec.groundOffset + 0.01f
        val tiltAngle = max(abs(state.visPitch), abs(state.visRoll))
        val liftLossFactor = if (atmos.useHardcore && isAirborne) {
            (1.0f - (tiltAngle / 45f) * 0.3f).coerceIn(0.7f, 1.0f)
        } else 1.0f

        val targetVelY = input.throttle * 8.0f * liftLossFactor
        val verticalAcc = (targetVelY - state.velY) * (5.0f / mass)
        state.velY += verticalAcc * dt
        
        // 垂直氣流注入：實施平滑過濾 (Low-Pass Filter) 與 物理斷路器
        val vThreshold = 0.7f
        val currentAltAboveGround = state.posY - spec.groundOffset
        
        if (atmos.enableVerticalDraft && currentAltAboveGround > vThreshold) {
            val rawVDraft = WindManager.calculateVerticalDraft(
                atmos.windLevel, atmos.windVariation, state.flightTime, 
                atmos.useHardcore, droneType, atmos.applyPhysicalSpecs
            )
            // 慣性過濾：消除突發力量導致的卡頓感
            smoothVDraft += (rawVDraft - smoothVDraft) * 8f * dt
            
            // [v1.7.7 校準] 漸進式淡入：從 0.7m 到 1.2m 之間線性增加力量
            val fadeFactor = ((currentAltAboveGround - vThreshold) / 0.5f).coerceIn(0f, 1.0f)
            
            // [v1.7.7 校準] 垂直力矩倍率與硬上限 (G-Force Cap)
            val massComp = if (atmos.applyPhysicalSpecs) sqrt(mass.toDouble()).toFloat() else 1.0f
            val maxAccV = 5.0f // 硬上限 0.5G，防止一飛衝天
            val appliedVDraftAcc = (smoothVDraft * 3.5f * massComp * fadeFactor).coerceIn(-maxAccV, maxAccV)
            
            state.velY += appliedVDraftAcc * dt
        } else {
            // [關鍵修復] 在低於閾值時強制歸零，防止「力量累積陷阱 (Inertial Windup)」
            smoothVDraft = 0f
        }
        
        var nextY = state.posY + state.velY * dt
        val maxAlt = spec.groundOffset + 30.0f
        var systemMsg: String? = null
        if (atmos.useFlightLimit && nextY > maxAlt) { 
            nextY = maxAlt; state.velY = 0f; systemMsg = "ALT_LIMIT"
        }

        // --- 3. [1:1 Git] 姿態與旋轉 ---
        if (isAirborne) {
            // [v1.7.9.2 標準化] 採用工業標準：向右推桿為順時針旋轉 (CW)
            state.yaw -= input.yaw * 120.0f * dt
        }
        
        val rad = Math.toRadians(state.yaw.toDouble()).toFloat()
        val cosY = cos(rad); val sinY = sin(rad)
        val rollInput = input.roll; val pitchInput = input.pitch
        
        if (isAirborne) {
            state.visPitch += (pitchInput * 25f - state.visPitch) * 8f * dt
            state.visRoll += (rollInput * 25f - state.visRoll) * 8f * dt
        } else {
            state.visPitch = 0f; state.visRoll = 0f
        }

        // --- 4. [1:1 Git] 水平位移：平滑加速度模型 ---
        // [v1.7.9.2 最終校準] 採用與 Yaw (CW) 匹配的位移矩陣
        // 在 Yaw 順時針增加的坐標系下，公式為：
        // X' = roll*cosY + pitch*sinY
        // Z' = -roll*sinY + pitch*cosY
        // 當 Yaw=0, cos=1, sin=0 -> accX = rollInput (向右), accZ = pitchInput (向前)
        // [v1.7.9.8 FINAL FIX] 核心座標系對位：
        // 根據 Log 診斷：向右推時，outX 為正，rollInput 為正。
        // 為使飛機在視覺上向右飛行，物理 accX 必須為正。
        // 原公式在 OpenGL 視角下會產生向左位移，故將 roll 項目取反。
        val accX = (-rollInput * cosY + pitchInput * sinY) * (if (atmos.applyPhysicalSpecs) spec.physicsPower else 18.0f)
        val accZ = (rollInput * sinY + pitchInput * cosY) * (if (atmos.applyPhysicalSpecs) spec.physicsPower else 18.0f)
        
        
        if (isAirborne) {
            state.velX += accX * dt
            state.velZ += accZ * dt
        }
        applyWind(dt, state, atmos, mass, spec.groundOffset)

        // --- 6. [1:1 Git] 阻尼與積分 ---
        val damping = if (atmos.applyPhysicalSpecs) spec.physicsDamping else 0.92f
        state.velX *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
        state.velZ *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
        
        val nextX = state.posX + state.velX * dt
        val nextZ = state.posZ + state.velZ * dt
        val preImpactTotalSpeed = sqrt(state.velX.pow(2) + state.velY.pow(2) + state.velZ.pow(2))

        // --- 7. [1:1 Git] 碰撞與地面處理 ---
        val collisionImpact = checkCollision(droneType, nextX, nextY, nextZ, state.visPitch, state.visRoll, atmos.useStrictLanding, atmos.showObstacles)
        
        var isHardLanding = false
        if (nextY <= spec.groundOffset + 0.001f) {
            if (atmos.useStrictLanding && state.velY < 0f) {
                if (preImpactTotalSpeed > 2.2f) isHardLanding = true
            }
            state.posY = spec.groundOffset
            state.velY = 0f; state.velX = 0f; state.velZ = 0f
        } else {
            state.posY = nextY; state.posX = nextX; state.posZ = nextZ
        }

        val isImpact = collisionImpact || isHardLanding
        if (isHardLanding && systemMsg == null) {
            systemMsg = if (preImpactTotalSpeed > 3.5f) "CRASH_EXTREME" else "CRASH_STRUCTURAL"
        }

        val res = PhysicsResult(
            isImpact = isImpact, 
            distanceH = sqrt(state.posX.pow(2) + state.posZ.pow(2)), 
            systemMessage = systemMsg, 
            motorRpm = (input.throttle + 1f) / 2f, 
            impactSpeed = preImpactTotalSpeed,
            currentWindAngle = com.horizon.caadronesimulator.model.DroneState.getInstance().env.currentWindAngle // [v1.7.7] 導出風向角
        )
        stepResult = res
        return res
    }

    private fun applyWind(dt: Float, state: DronePhysicsState, atmos: AtmosConfig, mass: Float, groundOffset: Float) {
        if (state.posY <= groundOffset + 0.01f) return
        val heightFactor = if (atmos.useHardcore) WindManager.calculateHeightFactor(state.posY, groundOffset) else 1.0f
        val wResult = WindManager.calculateWindVector(
            atmos.windLevel, atmos.windDirection, atmos.windVariation, 
            atmos.windDirVariation, state.flightTime, 
            com.horizon.caadronesimulator.model.DroneState.getInstance().env.randomWindAngle
        )
        val wVec = wResult.forceVector
        
        // [v1.7.7 建議標註]：
        // 目前水平風力直接作用於加速度，尚未實施終端速度上限 (Terminal Velocity)。
        // 建議未來加入 (velX.abs > 8.0) 斷路器，防止極端亂流下飛機被水平拋飛。
        smoothWindAccX += (wVec[0] * 1.5f * heightFactor - smoothWindAccX) * 8f * dt
        smoothWindAccZ += (wVec[1] * 1.5f * heightFactor - smoothWindAccZ) * 8f * dt
        
        state.velX += (smoothWindAccX / mass) * dt
        state.velZ += (smoothWindAccZ / mass) * dt

    }

    private fun simulateBattery(dt: Float, state: DronePhysicsState, useLimit: Boolean, droneType: String) {
        state.flightTime += dt // [關鍵修復] 移出判斷區，確保全域計時永不停止，從而驅動隨機脈衝事件
        
        if (!useLimit) { state.batteryVoltage = 4.2f; state.batteryPercent = 100; return }
        
        val spec = DroneRegistry.getSpec(droneType)
        
        // 動態計算每秒耗電：(滿電 4.2V - 沒電 3.2V) / (分鐘數 * 60秒)
        val totalSeconds = (spec.flightTimeMin.toFloat() * 60f).coerceAtLeast(60f)
        val drain = (1.0f / totalSeconds) * dt

        state.batteryVoltage = (state.batteryVoltage - drain).coerceAtLeast(3.2f)
        state.batteryPercent = ((state.batteryVoltage - 3.2f) / (4.2f - 3.2f) * 100).toInt()
    }

    private fun checkCollision(type: String, x: Float, y: Float, z: Float, p: Float, r: Float, useStrict: Boolean, showObstacles: Boolean = false): Boolean {
        val spec = DroneRegistry.getSpec(type)
        val mt = max(abs(p), abs(r))

        // 1. [v1.5.9] 實體障礙物碰撞偵測 (僅在開啟時激活)
        if (showObstacles) {
            for (obs in com.horizon.caadronesimulator.model.Constants.OBSTACLES) {
                val obsX = obs[0]; val obsZ = obs[1]; val obsH = obs[2]; val obsR = obs[4]
                val dist = sqrt((x - obsX).toDouble().pow(2) + (z - obsZ).toDouble().pow(2))
                // 圓柱體碰撞：距離小於 (飛機半徑 + 障礙半徑) 且高度低於頂端
                if (dist < (spec.collisionRadius + obsR) && y < obsH) {
                    return true
                }
            }
        }

        // --- [v1.5.9] 姿態感應碰撞判定：若關閉專業標準，則忽略地面傾角損毀 ---
        if (useStrict) {
            val tiltRad = mt * (PI.toFloat() / 180f)
            val tiltOffset = spec.collisionRadius * sin(tiltRad)
            val effectiveBottom = y - tiltOffset

            // 嚴格模式下 15° 損毀
            if (effectiveBottom < 0.05f && mt > 15f) {
                return true
            }
        }

        // --- [v1.1 原始邏輯復刻] 考照角錐 (Cone) 碰撞判定 ---
        for (cone in com.horizon.caadronesimulator.model.Constants.CONE_POSITIONS) {
            val distSq = (x - cone[0]).pow(2) + (z - cone[1]).pow(2)
            val thresholdSq = (spec.collisionRadius * 1.2f).pow(2)
            // 只要在角錐半徑內且高度低於 0.8m 就判定碰撞
            if (distSq < thresholdSq && (y - spec.groundOffset) < 0.8f) {
                return true
            }
        }

        // --- [邊界同步] 根據 Constants.kt 設定場地邊界 ---
        val isOutOfBounds = abs(x) > com.horizon.caadronesimulator.model.Constants.FIELD_WIDTH_HALF || 
                            z < com.horizon.caadronesimulator.model.Constants.FIELD_Z_BACK || 
                            z > com.horizon.caadronesimulator.model.Constants.FIELD_Z_FRONT

        // --- 極低空翻覆判定 ---
        val isFlippedOnGround = y < spec.groundOffset * 0.5f && mt > 10f

        return isOutOfBounds || isFlippedOnGround
    }

    fun isNearBoundary(x: Float, z: Float): Boolean {
        // 邊界警告範圍：使用 Constants 定義的邊界縮減 5m (WARNING_BUFFER)
        val b = com.horizon.caadronesimulator.model.Constants.WARNING_BUFFER
        return abs(x) > (com.horizon.caadronesimulator.model.Constants.FIELD_WIDTH_HALF - b) || 
               z < (com.horizon.caadronesimulator.model.Constants.FIELD_Z_BACK + b) || 
               z > (com.horizon.caadronesimulator.model.Constants.FIELD_Z_FRONT - b)
    }

    data class ControlInput(val throttle: Float, val yaw: Float, val pitch: Float, val roll: Float)
    data class PhysicsResult(
        val isImpact: Boolean, 
        val distanceH: Float, 
        val systemMessage: String?, 
        val motorRpm: Float, 
        val impactSpeed: Float = 0f,
        val currentWindAngle: Float = 0f // [v1.7.7] 導出實時風向角，供渲染器同步雲層與 HUD
    )
}
