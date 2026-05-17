package com.horizon.caadronesimulator.drones
import com.horizon.caadronesimulator.model.ModelHardwareSpecs
import com.horizon.caadronesimulator.model.DroneType

/**
 * [v1.5.8] 專業考照負重機 (JoyFlight T4) - 航規校準版
 * 修正：將腳架配色修正為國際航規「左紅右綠」。
 */
object T4HeavyLift : DroneModule {
    override val id = "HEAVY_LIFT"
    override val name = "JoyFlight T4"
    override val icon = "🏗️"
    override val hardwareSpecs = ModelHardwareSpecs(type = DroneType.MR, wheelbaseMm = 1100, propDiameterInch = 24, propPitchInch = 8, motorKv = 150, takeoffWeightKg = 14.9f, payloadKg = 1.0f, flightTimeMin = 15, description = "說明：大型負重機型具有更長的力臂與獨特的起落架設計。")
    override val groundOffset = 0.45f
    override val visualOffset = -0.07f
    override val collisionRadius = 0.83f
    override val scale = 0.6875f
    override val shadowSizeBase = 0.55f
    override val maxLandingSpeed = 1.8f
    
    override val fpvFov = 110f
    override val cameraVisualOffset = 0.38f

    override val geometry = listOf(
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.1f, tz = 0f, w = 0.6f, h = 0.05f, d = 0.6f, color = floatArrayOf(0f, 0.3f, 0.8f, 1f)),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.15f, tz = 0f, w = 0.4f, h = 0.12f, d = 0.4f, color = floatArrayOf(0.1f, 0.1f, 0.1f, 1f)),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.1f, tz = -0.3f, w = 0.05f, h = 0.05f, d = 0.05f, color = floatArrayOf(0f, 1f, 0f, 1f)),
        com.horizon.caadronesimulator.model.DronePart(tx = 0f, ty = 0.25f, tz = 0f, w = 0.25f, h = 0.12f, d = 0.25f, color = floatArrayOf(0f, 0.3f, 0.8f, 1f))
    ) + listOf(-0.12f, 0.12f).map { ax ->
        com.horizon.caadronesimulator.model.DronePart(tx = ax, ty = 0.35f, tz = -0.18f, w = 0.012f, h = 0.3f, d = 0.012f, color = floatArrayOf(0.15f, 0.15f, 0.15f, 1f), rx = -15f)
    } + run {
        val armLen = 0.8f
        listOf(floatArrayOf(1f,1f), floatArrayOf(-1f,1f), floatArrayOf(1f,-1f), floatArrayOf(-1f,-1f)).flatMap { p ->
            val side = p[0] * p[1]
            listOf(
                com.horizon.caadronesimulator.model.DronePart(tx = p[0]*armLen/2*0.707f, ty = 0.15f, tz = p[1]*armLen/2*0.707f, w = armLen, h = 0.04f, d = 0.04f, color = floatArrayOf(0.05f, 0.05f, 0.05f, 1f), ry = -45f * side),
                com.horizon.caadronesimulator.model.DronePart(tx = p[0]*armLen*0.707f, ty = 0.15f, tz = p[1]*armLen*0.707f, w = 0.12f, h = 0.08f, d = 0.12f, color = floatArrayOf(0.1f, 0.1f, 0.1f, 1f)),
                com.horizon.caadronesimulator.model.DronePart(tx = p[0]*armLen*0.707f, ty = 0.11f, tz = p[1]*armLen*0.707f, w = 0.14f, h = 0.03f, d = 0.14f, color = if(p[1] > 0) floatArrayOf(0f, 1f, 0f, 1f) else floatArrayOf(1f, 0f, 0f, 1f)),
                com.horizon.caadronesimulator.model.DronePart(tx = p[0]*armLen*0.707f, ty = 0.2f, tz = p[1]*armLen*0.707f, w = 0.7f, h = 0.01f, d = 0.04f, color = floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f), isPropeller = true, rotationDirection = side, baseRpm = 2500f)
            )
        }
    } + listOf(floatArrayOf(0.25f, 0.2f), floatArrayOf(-0.25f, 0.2f), floatArrayOf(0.25f, -0.2f), floatArrayOf(-0.25f, -0.2f)).map { l ->
        com.horizon.caadronesimulator.model.DronePart(tx = l[0], ty = -0.2f, tz = l[1], w = 0.03f, h = 0.5f, d = 0.03f, color = floatArrayOf(0.1f, 0.1f, 0.1f, 1f))
    } + listOf(-0.25f, 0.25f).flatMap { lx ->
        // [重要修正] 依照國際航規修正配色：左紅 / 右綠 (根據視覺對應調整 X 軸判定)
        val skidColor = if(lx > 0) floatArrayOf(1f, 0f, 0f, 1f) else floatArrayOf(0f, 1f, 0f, 1f)
        listOf(
            com.horizon.caadronesimulator.model.DronePart(tx = lx, ty = -0.45f, tz = 0f, w = 0.04f, h = 0.04f, d = 0.9f, color = skidColor),
            com.horizon.caadronesimulator.model.DronePart(tx = lx, ty = -0.45f, tz = 0.5f, w = 0.05f, h = 0.05f, d = 0.15f, color = skidColor),
            com.horizon.caadronesimulator.model.DronePart(tx = lx, ty = -0.45f, tz = -0.5f, w = 0.05f, h = 0.05f, d = 0.15f, color = skidColor)
        )
    }

    override val baseRate = 0.8f
    override val baseExpo = 0.5f
    override val baseRateT_Up = 0.9f
    override val baseRateT_Down = 0.6f
    override val baseRateY = 0.7f

    @androidx.compose.runtime.Composable
    override fun RenderIcon(modifier: androidx.compose.ui.Modifier, isSelected: Boolean) {
        com.horizon.caadronesimulator.ui.settings.HeavyLiftIcon(modifier)
    }

    override val physicsDamping: Float get() = 0.98f
}
