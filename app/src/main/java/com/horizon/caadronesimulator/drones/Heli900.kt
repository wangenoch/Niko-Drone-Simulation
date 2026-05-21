package com.horizon.caadronesimulator.drones
import com.horizon.caadronesimulator.model.ModelHardwareSpecs
import com.horizon.caadronesimulator.model.DroneType

/**
 * [v1.5.3] 專業 900 級單旋翼直昇機
 */
object Heli900 : DroneModule {
    override val id = "HELI_900"
    override val name = "重型直昇機"
    override val icon = "🚁"
    override val hardwareSpecs = ModelHardwareSpecs(type = DroneType.SRH, wheelbaseMm = 900, propDiameterInch = 22, propPitchInch = 7, motorKv = 280, takeoffWeightKg = 10.0f, payloadKg = 0f, flightTimeMin = 15, description = "SPECS_HELI_900_DESC")
    override val groundOffset = 0.25f
    override val visualOffset = -0.05f
    override val collisionRadius = 0.75f
    override val scale = 0.5625f
    override val shadowSizeBase = 0.52f
    override val maxLandingSpeed = 1.5f

    override val geometry = listOf(
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.35f, tz = 0.4f, w = 0.25f, h = 0.45f, d = 0.6f, color = floatArrayOf(0.1f, 0.1f, 0.1f, 1f)),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.55f, tz = 0.6f, w = 0.2f, h = 0.2f, d = 0.3f, color = floatArrayOf(0f, 0.4f, 0.8f, 0.8f)),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.4f, tz = -0.6f, w = 0.12f, h = 0.12f, d = 1.2f, color = floatArrayOf(0.15f, 0.15f, 0.15f, 1f)),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.65f, tz = 0.2f, w = 0.04f, h = 0.25f, d = 0.04f, color = floatArrayOf(0.2f, 0.2f, 0.2f, 1f)),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.78f, tz = 0.2f, w = 2.5f, h = 0.015f, d = 0.08f, color = floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), isPropeller = true, baseRpm = 2200f),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.78f, tz = 0.2f, w = 0.08f, h = 0.015f, d = 2.5f, color = floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), isPropeller = true, baseRpm = 2200f),
        com.horizon.caadronesimulator.model.DronePart(tx = 0.1f, ty = 0.4f, tz = -1.1f, w = 0.01f, h = 0.6f, d = 0.04f, color = floatArrayOf(0.9f, 0.9f, 0.9f, 0.6f), rz = 90f, isTailPropeller = true, baseRpm = 4000f)
    ) + listOf(-0.3f, 0.3f).flatMap { sx ->
        listOf(
            com.horizon.caadronesimulator.model.DronePart(tx = sx, ty = 0.15f, tz = 0.2f, w = 0.03f, h = 0.35f, d = 0.03f, color = floatArrayOf(0.2f, 0.2f, 0.2f, 1f)),
            com.horizon.caadronesimulator.model.DronePart(tx = sx, ty = 0.0f, tz = 0.2f, w = 0.04f, h = 0.04f, d = 1.2f, color = floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
        )
    }

    override val baseRate = 1.0f
    override val baseExpo = 0.4f

    @androidx.compose.runtime.Composable
    override fun RenderIcon(modifier: androidx.compose.ui.Modifier, isSelected: Boolean) {
        com.horizon.caadronesimulator.ui.settings.HelicopterIcon(modifier)
    }

    override val physicsPower: Float get() = 17.5f
}
