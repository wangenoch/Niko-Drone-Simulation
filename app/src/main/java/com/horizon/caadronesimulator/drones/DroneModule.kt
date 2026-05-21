package com.horizon.caadronesimulator.drones

import com.horizon.caadronesimulator.model.ModelHardwareSpecs
import com.horizon.caadronesimulator.model.DroneType

/**
 * [v1.5.3] 機種插件介面 - 基因驅動版 (Functional Logic 3.0)
 * 職責：整合數據、外型與行為主權。
 */
interface DroneModule {
    val id: String
    val name: String
    val icon: String
    
    // 1. 硬體規格表 (DNA)
    val hardwareSpecs: ModelHardwareSpecs

    // 2. 幾何屬性 (渲染相關)
    val groundOffset: Float
    val visualOffset: Float
    val collisionRadius: Float
    val scale: Float
    val shadowSizeBase: Float
    
    // 3. 幾何外型描述
    val geometry: List<com.horizon.caadronesimulator.model.DronePart>

    // 4. 擴展視覺參數
    val fpvFov: Float get() = 85f
    val cameraVisualOffset: Float get() = 0.4f

    // 5. 基礎手感 (通用預設)
    val baseRate: Float
    val baseExpo: Float

    // 6. 物理與行為標籤
    val isHoldSupported: Boolean get() = hardwareSpecs.type == DroneType.SRH
    val supportsAutorotation: Boolean get() = hardwareSpecs.type == DroneType.SRH
    val maxLandingSpeed: Float get() = 2.0f
    
    // 7. 專屬手感基因庫 (v1.5.3 徹底語義化：按功能定義)
    // 最終輸出 = 使用者設定值 * 基因基礎值
    fun calculateFinalRate(userRate: Float, function: String, stickValue: Float): Float {
        val base = when(function) {
            "T" -> if (stickValue >= 0) baseRateT_Up else baseRateT_Down
            "Y" -> baseRateY
            "P" -> baseRateP
            "R" -> baseRateR
            else -> baseRate
        }
        return userRate * base
    }

    fun calculateFinalExpo(userExpo: Float, function: String): Float {
        val base = when(function) {
            "T" -> baseExpoT; "Y" -> baseExpoY; "P" -> baseExpoP; "R" -> baseExpoR
            else -> baseExpo
        }
        return (userExpo + base).coerceIn(0f, 1f)
    }

    // 基因屬性覆寫位 (預設繼承總體 baseRate)
    val baseRateT_Up: Float get() = baseRate
    val baseRateT_Down: Float get() = baseRate
    val baseRateY: Float get() = baseRate
    val baseRateP: Float get() = baseRate
    val baseRateR: Float get() = baseRate

    val baseExpoT: Float get() = baseExpo
    val baseExpoY: Float get() = baseExpo
    val baseExpoP: Float get() = baseExpo
    val baseExpoR: Float get() = baseExpo

    // 8. UI 繪製主權
    @androidx.compose.runtime.Composable
    fun RenderIcon(modifier: androidx.compose.ui.Modifier, isSelected: Boolean)

    // --- 翻譯層 A：物理參數轉換 ---
    val physicsMass: Float get() = (hardwareSpecs.takeoffWeightKg / 5.3f).coerceAtLeast(0.1f)
    val physicsPower: Float get() = when(hardwareSpecs.type) {
        DroneType.SRH -> (hardwareSpecs.motorKv.toFloat() * 0.06f)
        else -> ((hardwareSpecs.motorKv.toFloat() / 10f) * (hardwareSpecs.propDiameterInch / 20f)).coerceAtLeast(1.0f)
    }
    val physicsDamping: Float get() = when(hardwareSpecs.type) {
        DroneType.SRH -> 0.95f
        else -> (0.90f + (hardwareSpecs.wheelbaseMm / 12000f)).coerceIn(0.85f, 0.99f)
    }

    // --- 翻譯層 B：UI 文字格式化 ---
    fun getFormattedSpecs(): String {
        val h = hardwareSpecs
        val line1 = "DIM: ${h.wheelbaseMm}mm / PROP: ${h.propDiameterInch}inch / PITCH: ${h.propPitchInch}inch / KV: ${h.motorKv}"
        val line2 = "WT: ${h.takeoffWeightKg}kg / LOAD: ${h.payloadKg}kg / TIME: ${h.flightTimeMin}min"
        return "$line1\n$line2"
    }
}
