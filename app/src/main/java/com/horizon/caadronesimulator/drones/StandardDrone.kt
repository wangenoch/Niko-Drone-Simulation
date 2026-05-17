package com.horizon.caadronesimulator.drones
import com.horizon.caadronesimulator.model.ModelHardwareSpecs
import com.horizon.caadronesimulator.model.DroneType

/**
 * [v1.5.3] 經典 210mm 穿越練習機
 */
object StandardDrone : DroneModule {
    override val id = "QUAD_STANDARD"
    override val name = "小型無人機"
    override val icon = "🚁"
    override val hardwareSpecs = ModelHardwareSpecs(type = DroneType.MR, wheelbaseMm = 210, propDiameterInch = 5, propPitchInch = 4, motorKv = 2450, takeoffWeightKg = 0.6f, payloadKg = 0f, flightTimeMin = 8, description = "說明：高機動性機型，反應極其靈敏，適合練習精準穿越與反應速度。")
    override val groundOffset = 0.08f
    override val visualOffset = 0.0f
    override val collisionRadius = 0.6f
    override val scale = 0.7f
    override val shadowSizeBase = 0.5f
    override val maxLandingSpeed = 2.5f

    override val geometry = listOf(
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0f, tz = 0f, w = 0.4f, h = 0.15f, d = 0.8f, color = floatArrayOf(0.12f, 0.12f, 0.12f, 1f)),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.05f, tz = 0.4f, w = 0.25f, h = 0.06f, d = 0.12f, color = floatArrayOf(1f, 0.4f, 0.0f, 1f))
    ) + listOf(
        floatArrayOf(-0.45f, 0.45f, 0f, 1f, 0f), floatArrayOf(0.45f, 0.45f, 1f, 0f, 0f),
        floatArrayOf(-0.45f, -0.45f, 0f, 1f, 0f), floatArrayOf(0.45f, -0.45f, 1f, 0f, 0f)
    ).flatMap { p ->
        val side = if (p[0] * p[1] > 0) 1f else -1f
        listOf(
            com.horizon.caadronesimulator.model.DronePart(tx = p[0]/2, ty = 0f, tz = p[1]/2, w = 0.08f, h = 0.04f, d = 0.7f, color = floatArrayOf(0.18f, 0.18f, 0.18f, 1f), ry = 45f * side),
            com.horizon.caadronesimulator.model.DronePart(tx = p[0], ty = 0.05f, tz = p[1], w = 0.16f, h = 0.16f, d = 0.16f, color = floatArrayOf(p[2], p[3], p[4], 1f)),
            com.horizon.caadronesimulator.model.DronePart(tx = p[0], ty = 0.12f, tz = p[1], w = 0.65f, h = 0.01f, d = 0.05f, color = floatArrayOf(0.95f, 0.95f, 0.95f, 0.6f), isPropeller = true, rotationDirection = side, baseRpm = 2000f),
            com.horizon.caadronesimulator.model.DronePart(tx = p[0], ty = 0.12f, tz = p[1], w = 0.05f, h = 0.01f, d = 0.65f, color = floatArrayOf(0.95f, 0.95f, 0.95f, 0.6f), isPropeller = true, rotationDirection = side, baseRpm = 2000f)
        )
    }

    // [v1.5.3] 專屬手感基因：練習機維持靈敏反應
    override val baseRate = 1.2f
    override val baseExpo = 0.2f
    
    @androidx.compose.runtime.Composable
    override fun RenderIcon(modifier: androidx.compose.ui.Modifier, isSelected: Boolean) {
        com.horizon.caadronesimulator.ui.settings.SmallDroneIcon(modifier)
    }

    override val physicsPower: Float get() = 24.0f
}
