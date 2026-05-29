package com.horizon.nikonikodronesimulator.drones
import com.horizon.nikonikodronesimulator.model.ModelHardwareSpecs
import com.horizon.nikonikodronesimulator.model.DroneType

/**
 * [v1.5.3] 專業 900 級單旋翼直昇機
 */
object Heli900 : DroneModule {
    override val id = "HELI_900"
    override val name = "SPECS_HELI_900_NAME"
    override val icon = "🚁"
    override val hardwareSpecs = ModelHardwareSpecs(type = DroneType.SRH, wheelbaseMm = 900, propDiameterInch = 22, propPitchInch = 7, motorKv = 280, takeoffWeightKg = 10.0f, payloadKg = 0f, flightTimeMin = 15, description = "SPECS_HELI_900_DESC")
    override val groundOffset = 0.25f
    override val visualOffset = -0.05f
    override val collisionRadius = 0.75f
    override val scale = 0.5625f
    override val shadowSizeBase = 0.52f
    override val maxLandingSpeed = 1.5f
    
    override val fpvFov = 75f
    override val cameraVisualOffset = 0.70f
    override val cameraHeightOffset = 0.15f // [v1.7.19] 鏡頭高度

    override val geometry = listOf(
        com.horizon.nikonikodronesimulator.model.DronePart(tx = 0f, ty = 0.35f, tz = 0.4f, w = 0.25f, h = 0.45f, d = 0.6f, color = floatArrayOf(0.1f, 0.1f, 0.1f, 1f)),
        com.horizon.nikonikodronesimulator.model.DronePart(tx = 0f, ty = 0.55f, tz = 0.6f, w = 0.2f, h = 0.2f, d = 0.3f, color = floatArrayOf(0f, 0.4f, 0.8f, 0.8f)),
        com.horizon.nikonikodronesimulator.model.DronePart(tx = 0f, ty = 0.4f, tz = -0.6f, w = 0.12f, h = 0.12f, d = 1.2f, color = floatArrayOf(0.15f, 0.15f, 0.15f, 1f)),
        com.horizon.nikonikodronesimulator.model.DronePart(tx = 0f, ty = 0.65f, tz = 0.2f, w = 0.04f, h = 0.25f, d = 0.04f, color = floatArrayOf(0.2f, 0.2f, 0.2f, 1f)),
        com.horizon.nikonikodronesimulator.model.DronePart(tx = 0f, ty = 0.78f, tz = 0.2f, w = 2.5f, h = 0.015f, d = 0.08f, color = floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), isPropeller = true, baseRpm = 2200f),
        com.horizon.nikonikodronesimulator.model.DronePart(tx = 0f, ty = 0.78f, tz = 0.2f, w = 0.08f, h = 0.015f, d = 2.5f, color = floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), isPropeller = true, baseRpm = 2200f),
        com.horizon.nikonikodronesimulator.model.DronePart(tx = 0.1f, ty = 0.4f, tz = -1.1f, w = 0.01f, h = 0.6f, d = 0.04f, color = floatArrayOf(0.9f, 0.9f, 0.9f, 0.6f), rz = 90f, isTailPropeller = true, baseRpm = 4000f)
    ) + listOf(-0.3f, 0.3f).flatMap { sx ->
        listOf(
            com.horizon.nikonikodronesimulator.model.DronePart(tx = sx, ty = 0.15f, tz = 0.2f, w = 0.03f, h = 0.35f, d = 0.03f, color = floatArrayOf(0.2f, 0.2f, 0.2f, 1f)),
            com.horizon.nikonikodronesimulator.model.DronePart(tx = sx, ty = 0.0f, tz = 0.2f, w = 0.04f, h = 0.04f, d = 1.2f, color = floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
        )
    }

    override val baseRate = 0.9f
    override val baseExpo = 0.5f
    override val baseRateT_Up = 0.5f
    override val baseRateT_Down = 0.4f
    override val baseRateY = 0.7f
    override val baseRateP = 0.7f
    override val baseRateR = 0.7f

    @androidx.compose.runtime.Composable
    override fun RenderIcon(modifier: androidx.compose.ui.Modifier, isSelected: Boolean) {
        com.horizon.nikonikodronesimulator.ui.settings.HelicopterIcon(modifier)
    }

    override val physicsPower: Float get() = 17.5f

    // [v1.7.17] 物理特性重構：低恢復、高慣性的 900 級直昇機
    override val physicsDamping: Float get() = 0.99f // 極高的水平慣性，使其收桿後持續滑行
    override val attitudeRestorationForce: Float get() = 1.8f // 極低的自動水平恢復力，模擬 3D 陀螺儀手感

    // [v1.7.17] 聲學基因：穩定轉速、具有 18Hz 週期性拍擊聲的傳統旋翼機
    override val soundProfile = com.horizon.nikonikodronesimulator.model.SoundProfile(
        baseFreq = 185f,
        harmonicPower = 0.5f,
        noiseFactor = 0.08f,
        isMultiMotor = false,
        modulationHz = 18f
    )
}
