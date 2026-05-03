package com.horizon.caadronesimulator.model

data class DroneSpecs(
    val id: String,
    val name: String,
    val groundOffset: Float,  
    val visualOffset: Float,  
    val collisionRadius: Float,
    val scale: Float,         
    val shadowSizeBase: Float,
    val icon: String,
    // 物理特性 (開啟開關時套用)
    val physicsMass: Float,
    val physicsPower: Float,
    val physicsDamping: Float
)

object DroneRegistry {
    private val SPECS = mapOf(
        "QUAD_STANDARD" to DroneSpecs(
            id = "QUAD_STANDARD",
            name = "小型無人機",
            groundOffset = 0.08f,   
            visualOffset = 0.0f,    
            collisionRadius = 0.6f,
            scale = 0.7f,
            shadowSizeBase = 0.5f,
            icon = "🚁",
            physicsMass = 0.8f,      // 0.6kg 輕量化
            physicsPower = 24.0f,    // 高轉速爆發力
            physicsDamping = 0.90f   // 低慣性，反應極快
        ),
        "HEAVY_LIFT" to DroneSpecs(
            id = "HEAVY_LIFT",
            name = "JoyFlight T4",
            groundOffset = 0.45f,   
            visualOffset = -0.07f, 
            collisionRadius = 0.83f,
            scale = 0.6875f,       
            shadowSizeBase = 0.55f,
            icon = "🏗️",
            physicsMass = 2.8f,      // 14.9kg 重型機感
            physicsPower = 13.5f,    // 低 KV 沉穩推力
            physicsDamping = 0.98f   // 高慣性，煞車距離長
        ),
        "HELI_900" to DroneSpecs(
            id = "HELI_900",
            name = "重型直昇機",
            groundOffset = 0.25f,   
            visualOffset = -0.05f, 
            collisionRadius = 0.75f,
            scale = 0.5625f,       // 1.6 * 0.5625 = 0.9 (900mm)
            shadowSizeBase = 0.52f,
            icon = "🚁",
            physicsMass = 2.2f,      // 稍微增加重量感
            physicsPower = 17.5f,    // 提升動力儲備
            physicsDamping = 0.95f   // 調整阻尼，使其轉向更有慣性
        )
    )
    
    // 初始平衡數值 (開關關閉時使用)
    private val NEUTRAL_MASS = 1.0f
    private val NEUTRAL_POWER = 18.0f
    private val NEUTRAL_DAMPING = 0.92f

    fun getSpec(id: String) = SPECS[id] ?: SPECS["QUAD_STANDARD"]!!

    fun getActiveMass(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsMass else NEUTRAL_MASS
    fun getActivePower(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsPower else NEUTRAL_POWER
    fun getActiveDamping(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsDamping else NEUTRAL_DAMPING
}
