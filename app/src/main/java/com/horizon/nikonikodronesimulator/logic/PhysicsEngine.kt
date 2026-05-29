package com.horizon.nikonikodronesimulator.logic

import com.horizon.nikonikodronesimulator.model.DronePhysicsState
import com.horizon.nikonikodronesimulator.model.DroneRegistry
import kotlin.math.*

/**
 * [v1.5.9] 模擬器物理核心 - Git 憲法級 1:1 還原版
 * 修正：完全復刻 b02b6fd 動力公式，保全天氣系統，徹底對齊視覺低頭與物理前進。
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
        val showObstacles: Boolean
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
        // 核心憲法：由「物理向量」預測生死，徹底移除高度進入限制，100% 屏蔽 Rate 干擾。
        val entryPosY = state.posY
        val entryVelY = state.velY
        val entrySpeed = sqrt(state.velX.pow(2) + state.velY.pow(2) + state.velZ.pow(2))
        
        // 1. 純物理位移預測 (無指令干預版)
        val inertialNextY = entryPosY + entryVelY * dt
        
        // 2. 判官裁決：向量穿透地面 && 速度向下 && 速度過快
        var isPredictiveCrash = false
        var predictiveSystemMsg: String? = null
        
        // [安全護衛]：只要垂直速度向下 (<-0.01) 且進入速度超標，且軌跡穿透地面線，即判定為砸地
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

        // --- 2. [v1.7.7] 垂直動力：二階平滑物理模型 ---
        val isAirborne = state.posY > spec.groundOffset + 0.01f
        val tiltAngle = max(abs(state.visPitch), abs(state.visRoll))
        val liftLossFactor = if (atmos.useHardcore && isAirborne) {
            (1.0f - (tiltAngle / 45f) * 0.3f).coerceIn(0.7f, 1.0f)
        } else 1.0f

        // [v1.7.15] 核心修正：起動動力喚醒補丁
        // 目的：解決 Samsung A55 等硬體在 120Hz 下數值下溢導致的起飛延遲 (30s 延遲問題)
        val rawThrottle = input.throttle
        val ds = com.horizon.nikonikodronesimulator.model.DroneState.getInstance()
        
        val baseLift = rawThrottle * 8.0f
        val targetVelY = if (ds.useIdleWakeupPatch && !isAirborne && rawThrottle > -0.98f && baseLift < 0.05f) {
            0.05f // [喚醒脈衝] 僅在地面啟動階段跳過數值黑洞
        } else {
            baseLift // 離地後恢復原本對稱推力，確保降落功能正常
        } * liftLossFactor

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
        val maxAlt = spec.groundOffset + com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.MAX_ALTITUDE
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
            // [v1.7.17] 基因驅動姿態：根據機種決定回正速度
            val restoreForce = if (atmos.applyPhysicalSpecs) spec.attitudeRestorationForce else 8.0f
            state.visPitch += (pitchInput * 25f - state.visPitch) * restoreForce * dt
            state.visRoll += (rollInput * 25f - state.visRoll) * restoreForce * dt
        } else {
            state.visPitch = 0f; state.visRoll = 0f
        }

        // --- 4. [1:1 Git] 水平位移：平滑加速度模型 ---
        val isHeli = spec.category == com.horizon.nikonikodronesimulator.model.DroneCategory.HELI
        val useAdvancedHeli = atmos.applyPhysicalSpecs && isHeli
        
        // [v1.7.17] 專業直昇機動力補丁 A：推力爬升率 (Thrust Ramp-up)
        // 模擬大型旋翼盤改變相位時的滯後感，消除數位開關感
        val smoothedRoll = if (useAdvancedHeli) state.visRoll / 25f else rollInput
        val smoothedPitch = if (useAdvancedHeli) state.visPitch / 25f else pitchInput

        // [v1.7.9.2 最終校準] 採用與 Yaw (CW) 匹配的位移矩陣
        val rawAccX = (-smoothedRoll * cosY + smoothedPitch * sinY) * (if (atmos.applyPhysicalSpecs) spec.physicsPower else 18.0f)
        val rawAccZ = (smoothedRoll * sinY + smoothedPitch * cosY) * (if (atmos.applyPhysicalSpecs) spec.physicsPower else 18.0f)
        
        var accX = rawAccX
        var accZ = rawAccZ

        // [v1.7.21] 基因驅動動能對抗：若開啟真實物理，套用該機種專屬的煞車慣性係數
        if (atmos.applyPhysicalSpecs && isAirborne) {
            val bInertia = spec.brakingInertiaScale
            val isBrakingX = (accX * state.velX) < 0
            val isBrakingZ = (accZ * state.velZ) < 0
            if (isBrakingX) accX /= bInertia
            if (isBrakingZ) accZ /= bInertia

            // [v1.7.17] 專業直昇機動力補丁 C：側向漂移 (Translating Tendency)
            if (spec.category == com.horizon.nikonikodronesimulator.model.DroneCategory.HELI) {
                val driftRad = Math.toRadians(state.yaw.toDouble()).toFloat()
                accX += cos(driftRad) * 0.8f 
                accZ += sin(driftRad) * 0.8f
            }
        }
        
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
        
        // --- 7. [v1.7.9.17] 憲法級審判執行層 ---
        // 核心邏輯：判決驅動位置。判官的「有罪裁決」具有最高優先權，無視後續 nextY 是否逃脫。
        val collisionResult = checkCollisionDetail(droneType, nextX, nextY, nextZ, state.visPitch, state.visRoll, atmos.useStrictLanding, atmos.showObstacles)
        
        var isHardLanding = false
        
        // 判官執行點：一旦預測性判官判定為 Crash，立即執行，不論 nextY 在哪
        if (isPredictiveCrash) {
            isHardLanding = true
            state.posY = spec.groundOffset
            state.velY = 0f; state.velX = 0f; state.velZ = 0f
            if (systemMsg == null) systemMsg = predictiveSystemMsg
        } else if (collisionResult.isImpact) {
            // [v1.7.12] 處理非砸地的其它碰撞 (如出界、撞牆、翻覆)
            state.posY = spec.groundOffset
            state.velY = 0f; state.velX = 0f; state.velZ = 0f
            if (systemMsg == null) systemMsg = collisionResult.reason
        } else if (nextY <= spec.groundOffset + 0.001f) {
            // 常規著陸處理 (未達損毀速度)
            state.posY = spec.groundOffset
            state.velY = 0f; state.velX = 0f; state.velZ = 0f
        } else {
            // 正常飛行位移
            state.posY = nextY; state.posX = nextX; state.posZ = nextZ
        }

        val isImpact = collisionResult.isImpact || isHardLanding
        
        // [v1.7.9.19] 損毀鎖定觸發：一旦判定為 Impact，立即鎖定靜態狀態，防止線程延遲導致狀態被洗白
        if (isImpact) {
            isLatchedImpact = true
            latchedSystemMsg = systemMsg
            latchedImpactSpeed = entrySpeed
        }
        
        val res = PhysicsResult(
            isImpact = isImpact, 
            distanceH = sqrt(state.posX.pow(2) + state.posZ.pow(2)), 
            systemMessage = systemMsg, 
            motorRpm = (input.throttle + 1f) / 2f, 
            impactSpeed = entrySpeed,
            currentWindAngle = com.horizon.nikonikodronesimulator.model.DroneState.getInstance().env.currentWindAngle // [v1.7.7] 導出風向角
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
            com.horizon.nikonikodronesimulator.model.DroneState.getInstance().env.randomWindAngle
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

    data class CollisionDetail(val isImpact: Boolean, val reason: String? = null)

    private fun checkCollisionDetail(type: String, x: Float, y: Float, z: Float, p: Float, r: Float, useStrict: Boolean, showObstacles: Boolean = false): CollisionDetail {
        val spec = DroneRegistry.getSpec(type)
        val mt = max(abs(p), abs(r))

        // 1. [v1.5.9] 實體障礙物碰撞偵測 (僅在開啟時激活)
        if (showObstacles) {
            for (obs in com.horizon.nikonikodronesimulator.model.Constants.OBSTACLES) {
                val obsX = obs[0]; val obsZ = obs[1]; val obsH = obs[2]; val obsR = obs[4]
                val dist = sqrt((x - obsX).toDouble().pow(2) + (z - obsZ).toDouble().pow(2))
                // 圓柱體碰撞：距離小於 (飛機半徑 + 障礙半徑) 且高度低於頂端
                if (dist < (spec.collisionRadius + obsR) && y < obsH) {
                    return CollisionDetail(true, "COLLISION_OBJECT")
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
                return CollisionDetail(true, "CRASH_FLIPPED")
            }
        }

        // --- [v1.1 原始邏輯復刻] 考照角錐 (Cone) 碰撞判定 ---
        for (cone in com.horizon.nikonikodronesimulator.model.Constants.CONE_POSITIONS) {
            val distSq = (x - cone[0]).pow(2) + (z - cone[1]).pow(2)
            val thresholdSq = (spec.collisionRadius * 1.2f).pow(2)
            // 只要在角錐半徑內且高度低於 0.8m 就判定碰撞
            if (distSq < thresholdSq && (y - spec.groundOffset) < 0.8f) {
                return CollisionDetail(true, "COLLISION_CONE")
            }
        }

        // --- [邊界同步] 根據 Constants.kt 設定場地邊界 ---
        val isOutOfBounds = abs(x) > com.horizon.nikonikodronesimulator.model.Constants.FIELD_WIDTH_HALF ||
                            z < com.horizon.nikonikodronesimulator.model.Constants.FIELD_Z_BACK ||
                            z > com.horizon.nikonikodronesimulator.model.Constants.FIELD_Z_FRONT

        if (isOutOfBounds) {
            return CollisionDetail(true, "CRASH_OUT_OF_BOUNDS")
        }

        // --- 極低空翻覆判定 ---
        val isFlippedOnGround = y < spec.groundOffset * 0.5f && mt > 10f
        if (isFlippedOnGround) {
            return CollisionDetail(true, "CRASH_FLIPPED")
        }

        return CollisionDetail(false)
    }

    fun isNearBoundary(x: Float, z: Float): Boolean {
        // 邊界警告範圍：使用 Constants 定義的邊界縮減 5m (WARNING_BUFFER)
        val b = com.horizon.nikonikodronesimulator.model.Constants.WARNING_BUFFER
        return abs(x) > (com.horizon.nikonikodronesimulator.model.Constants.FIELD_WIDTH_HALF - b) ||
               z < (com.horizon.nikonikodronesimulator.model.Constants.FIELD_Z_BACK + b) ||
               z > (com.horizon.nikonikodronesimulator.model.Constants.FIELD_Z_FRONT - b)
    }

    data class ControlInput(val throttle: Float, val yaw: Float, val pitch: Float, val roll: Float)
    data class PhysicsResult(
        val isImpact: Boolean, 
        val distanceH: Float, 
        val systemMessage: String?, 
        val motorRpm: Float, 
        val impactSpeed: Float = 0f,
        val currentWindAngle: Float = 0f // [v1.7.7] 導出風向角
    )
}
