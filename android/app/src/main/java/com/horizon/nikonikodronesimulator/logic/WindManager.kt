package com.horizon.nikonikodronesimulator.logic

import com.horizon.nikonikodronesimulator.model.AppConfig
import java.util.Random
import kotlin.math.*

/**
 * [v1.7.7] 專業級分形脈衝大氣引擎 (平衡校準版)
 * 職責：模擬真實大氣的不規則性，並經過溫和化校準，確保起飛穩定與位移合理。
 * 修正：將位移誤差控制在 ±1.0m 以內，並增加地面平靜區。
 */
object WindManager {
    private val random = Random()
    
    // [v1.7.9] 結構化計算結果
    data class WindResult(val forceVector: FloatArray, val visualAngle: Float)

    // --- 脈衝狀態追蹤 ---
    private var nextEventTime = 0f
    private var isEventActive = false
    private var eventStartTime = 0f
    private var eventDuration = 0f
    private var eventIntensity = 0f
    private var eventVerticalBias = 0f 

    fun update(currentTime: Float, level: Int, variation: Int, useHardcore: Boolean) {
        if (level <= 0) { isEventActive = false; return }

        if (isEventActive && currentTime > eventStartTime + eventDuration) {
            isEventActive = false
            nextEventTime = currentTime + 12f + random.nextFloat() * 30f
        }

        if (nextEventTime == 0f && currentTime > 0.1f) {
            nextEventTime = currentTime + 5f + random.nextFloat() * 10f
        }

        if (!isEventActive && currentTime > nextEventTime) {
            isEventActive = true
            eventStartTime = currentTime
            eventDuration = 3.0f + random.nextFloat() * 4.0f
            
            // [v1.7.7 校準] 溫和化脈衝強度：縮小基礎係數
            eventIntensity = level * (0.6f + (variation / 5f) * 0.4f)
            
            val downBias = if (useHardcore) 0.75f else 0.5f
            // 下沉力量下修：下沉 (-1.5) 與上升 (0.8)
            eventVerticalBias = if (random.nextFloat() > downBias) 0.8f else -1.5f 
        }
    }

    fun calculateWindVector(
        level: Int,
        direction: String,
        variation: Int,
        dirVariation: Int,
        flightTime: Float,
        randomWindAngle: Float
    ): WindResult {
        if (level <= 0 || direction == AppConfig.WIND_DIR_NONE) {
            return WindResult(floatArrayOf(0f, 0f), 0f)
        }

        val baseFromAngle = if (direction == AppConfig.WIND_DIR_RANDOM) {
            // [v1.7.7-WIN-STABLE] 統一使用 rendererTime (傳入的 flightTime)
            (randomWindAngle + (flightTime * 0.3f)) % 360f 
        } else {
            getStaticAngle(direction)
        }

        val impulseFactor = if (isEventActive) {
            calculateSmoothPulse(flightTime - eventStartTime, eventDuration) * eventIntensity
        } else 0f

        val jitterScale = (dirVariation / 5f) * 12f 
        val fractalJitter = (sin(flightTime * 1.7f) * 0.6f + sin(flightTime * 3.1f) * 0.3f + sin(flightTime * 7.7f) * 0.1f) * jitterScale
        
        // [v1.7.9.10 TRUTH ANCHOR]
        // 核心邏輯：FlowAngle = BaseAngle + 180 (風吹去的方向)
        // 物理力向量必須與 FlowAngle 保持一致。
        val flowAngle = (baseFromAngle + 180f + fractalJitter) % 360f

        // [v1.7.7-WIN-STABLE] 水平受力溫和化(0.25f)
        val baseStrength = level * 1f
        val totalStrength = baseStrength + (impulseFactor * 0.2f)
        
        val rad = Math.toRadians(flowAngle.toDouble()).toFloat()
        // [v1.7.9.12 FINAL WIND FIX] 物理力對位：
        // 為對應 OpenGL 視覺（X- 為右），物理力向量必須將 sin 項目取反，
        // 確保東風（流向 270 度）產生的物理力為 X+（視覺向左）。
        return WindResult(
            forceVector = floatArrayOf(-sin(rad) * totalStrength, cos(rad) * totalStrength),
            visualAngle = flowAngle
        )
    }

    fun calculateVerticalDraft(
        level: Int,
        variation: Int,
        flightTime: Float,
        useHardcore: Boolean,
        droneType: String,
        applySpecs: Boolean
    ): Float {
        if (level <= 0) return 0f
        
        val vImpulse = if (isEventActive) {
            val t = flightTime - eventStartTime
            calculateSmoothPulse(t, eventDuration) * eventVerticalBias * (level / 4f)
        } else 0f

        val f = flightTime
        val micro = if (useHardcore) {
            (sin(f * 0.8f) * 0.5f + sin(f * 2.3f) * 0.35f + sin(f * 5.1f) * 0.15f) * 0.06f * level
        } else 0f

        return vImpulse + micro
    }

    private fun calculateSmoothPulse(t: Float, duration: Float): Float {
        val progress = (t / duration).coerceIn(0f, 1f)
        return sin(progress * PI.toFloat())
    }

    fun calculateHeightFactor(altitude: Float, groundY: Float): Float {
        val h = (altitude - groundY).coerceAtLeast(0f)
        // [v1.7.7] 擴大起飛平靜區：1.5米以下風力大幅衰減，確保起飛安全
        if (h <= 0.1f) return 0f
        return (ln(max(0.1f, h) / 0.15f) / ln(12f / 0.15f)).coerceIn(0f, 1.0f)
    }

    private fun getStaticAngle(direction: String): Float = when(direction) {
        AppConfig.WIND_DIR_N -> 0f; AppConfig.WIND_DIR_NE -> 45f; AppConfig.WIND_DIR_E -> 90f
        AppConfig.WIND_DIR_SE -> 135f; AppConfig.WIND_DIR_S -> 180f; AppConfig.WIND_DIR_SW -> 225f
        AppConfig.WIND_DIR_W -> 270f; AppConfig.WIND_DIR_NW -> 315f; else -> 0f
    }
}
