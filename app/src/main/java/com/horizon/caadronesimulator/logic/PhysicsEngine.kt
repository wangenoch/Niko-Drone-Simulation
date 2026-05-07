package com.horizon.caadronesimulator.logic

// 內部數據模型
import com.horizon.caadronesimulator.model.Constants
import com.horizon.caadronesimulator.model.DroneRegistry

// 數學工具
import kotlin.math.*

/**
 * 模擬器物理判定引擎
 * 負責處理碰撞、邊界檢查等空間邏輯。
 */
object PhysicsEngine {

    /**
     * 檢查無人機是否發生碰撞或超出邊界
     */
    fun checkCollision(
        droneType: String,
        alt: Float,
        x: Float,
        z: Float,
        pitch: Float,
        roll: Float
    ): Boolean {
        val spec = DroneRegistry.getSpec(droneType)
        val mt = max(abs(pitch), abs(roll))
        
        // 1. 地面傾斜碰觸判定 (防止起飛/降落時因傾角過大損毀)
        if (alt - spec.collisionRadius * sin(mt * PI.toFloat() / 180f) < 0.05f && mt > 15f) {
            return true
        }
        
        // 2. 考照角錐 (Cone) 碰撞判定
        for (cone in Constants.CONE_POSITIONS) {
            val distSq = (x - cone[0]).pow(2) + (z - cone[1]).pow(2)
            val thresholdSq = (spec.collisionRadius * 1.2f).pow(2)
            if (distSq < thresholdSq && (alt - spec.groundOffset) < 0.8f) {
                return true
            }
        }
        
        // 3. 場地邊界判定 [v1.3.9] 使用擴大後的規格
        val isOutOfBounds = abs(x) > Constants.FIELD_WIDTH_HALF || z < Constants.FIELD_Z_BACK || z > Constants.FIELD_Z_FRONT
        
        // 4. 極低空翻覆判定
        val isFlippedOnGround = alt < spec.groundOffset * 0.5f && mt > 10f
        
        return isOutOfBounds || isFlippedOnGround
    }

    /**
     * [v1.3.9] 檢查是否接近空域邊界
     */
    fun isNearBoundary(x: Float, z: Float): Boolean {
        val b = Constants.WARNING_BUFFER
        return abs(x) > (Constants.FIELD_WIDTH_HALF - b) || 
               z < (Constants.FIELD_Z_BACK + b) || 
               z > (Constants.FIELD_Z_FRONT - b)
    }
}
