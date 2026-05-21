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

    fun step(dt: Float, state: DronePhysicsState, input: ControlInput, atmos: AtmosConfig, droneType: String): PhysicsResult {
        val spec = DroneRegistry.getSpec(droneType)
        val mass = if (atmos.applyPhysicalSpecs) spec.physicsMass else 1.0f
        val power = if (atmos.applyPhysicalSpecs) spec.physicsPower else 18.0f
        val damping = if (atmos.applyPhysicalSpecs) spec.physicsDamping else 0.92f

        // --- 1. [1:1 Git] 安全鎖熔斷 ---
        if (atmos.isMotorLocked) {
            state.velX = 0f; state.velY = 0f; state.velZ = 0f
            state.visPitch = 0f; state.visRoll = 0f
            if (state.posY != spec.groundOffset) state.posY = spec.groundOffset
            return PhysicsResult(false, sqrt(state.posX.pow(2) + state.posZ.pow(2)), null, 0f)
        }

        simulateBattery(dt, state, atmos.useFlightLimit, droneType)

        // --- 2. [1:1 Git] 垂直動力：速度追隨算法 ---
        state.velY += ((input.throttle * 8.0f) - state.velY) * (5.0f / mass) * dt
        
        var nextY = state.posY + state.velY * dt
        val maxAlt = spec.groundOffset + 30.0f
        var systemMsg: String? = null
        if (atmos.useFlightLimit && nextY > maxAlt) { 
            nextY = maxAlt; state.velY = 0f; systemMsg = "ALT_LIMIT"
        }

        // --- 3. [1:1 Git] 姿態與旋轉 ---
        val isAirborne = state.posY > spec.groundOffset + 0.01f
        if (isAirborne) {
            state.yaw -= input.yaw * 120.0f * dt
        }
        
        val rad = Math.toRadians(state.yaw.toDouble()).toFloat()
        val cosY = cos(rad); val sinY = sin(rad)
        
        val rollInput = -input.roll
        val pitchInput = -input.pitch
        
        if (isAirborne) {
            state.visPitch += (pitchInput * 25f - state.visPitch) * 8f * dt
            state.visRoll += (rollInput * 25f - state.visRoll) * 8f * dt
        } else {
            state.visPitch = 0f
            state.visRoll = 0f
        }

        // --- 4. [1:1 Git] 水平位移：旋轉矩陣法 ---
        val accX = (cosY * rollInput - sinY * pitchInput) * power
        val accZ = (-sinY * rollInput - cosY * pitchInput) * power
        
        // [v1.5.9] 地面摩擦鎖定：僅在離地後應用水平加速度，防止地面爬行
        if (state.posY > spec.groundOffset + 0.01f) {
            state.velX += accX * dt
            state.velZ += accZ * dt
        }

        // --- 5. [保留] 天氣系統干擾 ---
        applyWind(dt, state, atmos, mass, spec.groundOffset)

        // --- 6. [1:1 Git] 阻尼與積分 ---
        state.velX *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
        state.velZ *= (1.0f - (1.0f - damping) * 60f * dt).coerceIn(0f, 1f)
        
        // [v1.5.9] 正確邏輯順序：先計算預期位置與衝擊速度快照，再執行地面約束
        val nextX = state.posX + state.velX * dt
        val nextZ = state.posZ + state.velZ * dt
        // [v1.5.9] 衝擊動量預存：在地面歸零前計算總合速度
        val preImpactTotalSpeed = sqrt(state.velX.pow(2) + state.velY.pow(2) + state.velZ.pow(2))

        // --- 7. [1:1 Git] 碰撞與地面處理 ---
        // 關鍵：使用預期位置進行碰撞偵測，確保在速度歸零前完成判定
        val collisionImpact = checkCollision(droneType, nextX, nextY, nextZ, state.visPitch, state.visRoll, atmos.useStrictLanding, atmos.showObstacles)
        
        var isHardLanding = false
        if (nextY <= spec.groundOffset + 0.001f) {
            // [v1.5.9] 真正的完全關閉：若關閉專業標準，則徹底跳過硬著陸判定
            if (atmos.useStrictLanding) {
                if (preImpactTotalSpeed > 2.2f) {
                    isHardLanding = true
                }
            }
            
            // 判定後強制執行座標與速度約束
            state.posY = spec.groundOffset
            state.velY = 0f; state.velX = 0f; state.velZ = 0f
        } else {
            state.posY = nextY
            state.posX = nextX
            state.posZ = nextZ
        }

        val isImpact = collisionImpact || isHardLanding
        
        // [v1.5.9] 移除物理層硬編碼的消息生成，改由 ViewModel 統一決定顯示邏輯
        if (isHardLanding && systemMsg == null) {
            systemMsg = if (preImpactTotalSpeed > 3.5f) "CRASH_EXTREME" else "CRASH_STRUCTURAL"
        }

        val res = PhysicsResult(
            isImpact = isImpact, 
            distanceH = sqrt(state.posX.pow(2) + state.posZ.pow(2)), 
            systemMessage = systemMsg, 
            motorRpm = (input.throttle + 1f) / 2f,
            impactSpeed = preImpactTotalSpeed
        )
        stepResult = res
        return res
    }

    private fun applyWind(dt: Float, state: DronePhysicsState, atmos: AtmosConfig, mass: Float, groundOffset: Float) {
        // [v1.5.9] 地面效應與高度風切：未起飛或低空時風力衰減
        // 增加起飛門檻：高度低於 1cm 視為未起飛，完全封鎖風力受力，防止地面爬行
        if (state.posY <= groundOffset + 0.01f) return

        val heightFactor = if (atmos.useHardcore) {
            WindManager.calculateHeightFactor(state.posY, groundOffset)
        } else 1.0f

        val wVec = WindManager.calculateWindVector(atmos.windLevel, atmos.windDirection, atmos.windVariation, atmos.windDirVariation, state.flightTime, com.horizon.caadronesimulator.model.DroneState.getInstance())
        val gust = WindManager.calculateGust(atmos.windVariation, atmos.randomWindPhase, state.flightTime, atmos.windLevel, atmos.useHardcore)
        
        // [v1.6.1] 憲法級風力受力：移除所有手動負號補丁，直接使用物理流向向量
        val windAccX = (wVec[0] * atmos.windLevel * 0.85f * gust * heightFactor) / mass
        val windAccZ = (wVec[1] * atmos.windLevel * 0.85f * gust * heightFactor) / mass
        state.velX += windAccX * dt
        state.velZ += windAccZ * dt
    }

    private fun simulateBattery(dt: Float, state: DronePhysicsState, useLimit: Boolean, droneType: String) {
        if (!useLimit) { state.batteryVoltage = 4.2f; state.batteryPercent = 100; return }
        
        val spec = DroneRegistry.getSpec(droneType)
        state.flightTime += dt
        
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
    data class PhysicsResult(val isImpact: Boolean, val distanceH: Float, val systemMessage: String?, val motorRpm: Float, val impactSpeed: Float = 0f)
}
