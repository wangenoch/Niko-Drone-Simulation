package com.horizon.caadronesimulator.model

/**
 * [v1.5.0] 機種類型列舉
 */
enum class DroneCategory {
    MULTI_ROTOR, // 多旋翼 (如 T4, 小練習機)
    HELI,        // 直昇機 (支援熄火開關與自轉物理)
    FIXED_WING   // 固定翼 (預留)
}

data class DroneSpecs(
    val id: String,
    val name: String,
    val category: DroneCategory, // [v1.5.0] 類別定義
    val groundOffset: Float,  
    val visualOffset: Float,  
    val collisionRadius: Float,
    val scale: Float,         
    val shadowSizeBase: Float,
    val icon: String,
    
    // 物理特性 (開啟開關時套用)
    val physicsMass: Float,
    val physicsPower: Float,
    val physicsDamping: Float,
    
    // [v1.5.0] 功能支援標記
    val isHoldSupported: Boolean = false 
)

object DroneRegistry {
    private val SPECS = mapOf(
        "QUAD_STANDARD" to DroneSpecs(
            id = "QUAD_STANDARD",
            name = "小型無人機",
            category = DroneCategory.MULTI_ROTOR,
            groundOffset = 0.08f,   
            visualOffset = 0.0f,    
            collisionRadius = 0.6f,
            scale = 0.7f,
            shadowSizeBase = 0.5f,
            icon = "🚁",
            physicsMass = 0.8f,
            physicsPower = 24.0f,
            physicsDamping = 0.90f
        ),
        "HEAVY_LIFT" to DroneSpecs(
            id = "HEAVY_LIFT",
            name = "JoyFlight T4",
            category = DroneCategory.MULTI_ROTOR,
            groundOffset = 0.45f,   
            visualOffset = -0.07f, 
            collisionRadius = 0.83f,
            scale = 0.6875f,       
            shadowSizeBase = 0.55f,
            icon = "🏗️",
            physicsMass = 2.8f,
            physicsPower = 13.5f,
            physicsDamping = 0.98f
        ),
        "HELI_900" to DroneSpecs(
            id = "HELI_900",
            name = "重型直昇機",
            category = DroneCategory.HELI, // 標記為直昇機
            groundOffset = 0.25f,   
            visualOffset = -0.05f, 
            collisionRadius = 0.75f,
            scale = 0.5625f,
            shadowSizeBase = 0.52f,
            icon = "🚁",
            physicsMass = 2.2f,
            physicsPower = 17.5f,
            physicsDamping = 0.95f,
            isHoldSupported = true // 僅直昇機支援熄火開關
        )
    )
    
    private val NEUTRAL_MASS = 1.0f
    private val NEUTRAL_POWER = 18.0f
    private val NEUTRAL_DAMPING = 0.92f

    fun getSpec(id: String) = SPECS[id] ?: SPECS["QUAD_STANDARD"]!!

    fun getActiveMass(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsMass else NEUTRAL_MASS
    fun getActivePower(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsPower else NEUTRAL_POWER
    fun getActiveDamping(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsDamping else NEUTRAL_DAMPING
}
