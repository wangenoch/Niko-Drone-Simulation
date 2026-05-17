package com.horizon.caadronesimulator.model

import com.horizon.caadronesimulator.drones.*

/**
 * [v1.5.1] 機種類型列舉
 */
enum class DroneCategory {
    MULTI_ROTOR, // 多旋翼
    HELI,        // 直昇機
    FIXED_WING   // 固定翼
}

/**
 * [v1.5.1] 專業機型標準分類
 */
enum class DroneType {
    MR,   // Multi-rotor
    SRH,  // Single-rotor Helicopter
    FW,   // Fixed-wing
    VTOL  // Vertical Take-off and Landing
}

/**
 * [v1.5.1] 硬體規格數據結構
 */
data class ModelHardwareSpecs(
    val type: DroneType,
    val wheelbaseMm: Int = 0,         
    val propDiameterInch: Int = 0,    
    val propPitchInch: Int = 0,       
    val motorKv: Int = 0,            
    val takeoffWeightKg: Float = 0f,  
    val payloadKg: Float = 0f,        
    val flightTimeMin: Int = 0,       
    val description: String = ""       
)

/**
 * [v1.5.3] 幾何零件數據結構
 */
data class DronePart(
    val tx: Float = 0f, val ty: Float = 0f, val tz: Float = 0f,
    val w: Float = 0.1f, val h: Float = 0.1f, val d: Float = 0.1f,
    val color: FloatArray = floatArrayOf(0.12f, 0.12f, 0.12f, 1f),
    val ry: Float = 0f, val rx: Float = 0f, val rz: Float = 0f,
    val isPropeller: Boolean = false,
    val isTailPropeller: Boolean = false,
    val rotationDirection: Float = 1.0f,
    val baseRpm: Float = 2000f
)

data class DroneSpecs(
    val id: String,
    val name: String,
    val category: DroneCategory, 
    val groundOffset: Float,  
    val visualOffset: Float,  
    val collisionRadius: Float,
    val scale: Float,         
    val shadowSizeBase: Float,
    val icon: String,
    
    val physicsMass: Float,
    val physicsPower: Float,
    val physicsDamping: Float,
    
    // [v1.5.3] 擴展屬性
    val fpvFov: Float,
    val cameraVisualOffset: Float,
    val isHoldSupported: Boolean = false,
    val maxLandingSpeed: Float,
    val flightTimeMin: Int,
    
    val baseRate: Float = 1.0f,
    val baseExpo: Float = 0.0f
)

object DroneRegistry {
    private val MODELS: List<DroneModule> = listOf(
        StandardDrone,
        T4HeavyLift,
        Heli900
    )

    private val SPECS: Map<String, DroneSpecs> = MODELS.associate { it.id to DroneSpecs(
        id = it.id,
        name = it.name,
        category = when(it.hardwareSpecs.type) {
            DroneType.SRH -> DroneCategory.HELI
            else -> DroneCategory.MULTI_ROTOR
        },
        groundOffset = it.groundOffset,
        visualOffset = it.visualOffset,
        collisionRadius = it.collisionRadius,
        scale = it.scale,
        shadowSizeBase = it.shadowSizeBase,
        icon = it.icon,
        physicsMass = it.physicsMass,
        physicsPower = it.physicsPower,
        physicsDamping = it.physicsDamping,
        fpvFov = it.fpvFov,
        cameraVisualOffset = it.cameraVisualOffset,
        isHoldSupported = it.isHoldSupported,
        maxLandingSpeed = it.maxLandingSpeed,
        flightTimeMin = it.hardwareSpecs.flightTimeMin,
        baseRate = it.baseRate,
        baseExpo = it.baseExpo
    ) }
    
    private val NEUTRAL_MASS = 1.0f
    private val NEUTRAL_POWER = 18.0f
    private val NEUTRAL_DAMPING = 0.92f

    fun getAllSpecs(): List<DroneSpecs> = SPECS.values.toList()
    fun getSpec(id: String): DroneSpecs = SPECS[id] ?: run {
        val it = MODELS.find { m -> m.id == StandardDrone.id } ?: StandardDrone
        DroneSpecs(
            it.id, it.name, DroneCategory.MULTI_ROTOR, it.groundOffset, it.visualOffset, it.collisionRadius, it.scale, it.shadowSizeBase, it.icon, 
            it.physicsMass, it.physicsPower, it.physicsDamping, it.fpvFov, it.cameraVisualOffset, it.isHoldSupported, it.maxLandingSpeed, it.hardwareSpecs.flightTimeMin, it.baseRate, it.baseExpo
        )
    }
    fun getModule(id: String): DroneModule = MODELS.find { it.id == id } ?: StandardDrone

    fun getActiveMass(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsMass else NEUTRAL_MASS
    fun getActivePower(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsPower else NEUTRAL_POWER
    fun getActiveDamping(id: String, applyCustom: Boolean) = if (applyCustom) getSpec(id).physicsDamping else NEUTRAL_DAMPING
}
