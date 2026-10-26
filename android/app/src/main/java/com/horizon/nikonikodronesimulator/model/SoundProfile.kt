package com.horizon.nikonikodronesimulator.model

/**
 * [v1.7.17] 聲學基因模型 (Acoustic Gene Model)
 * 職責：定義不同機型的物理聲音特徵。
 */
data class SoundProfile(
    val baseFreq: Float = 180f,      // 基礎音調頻率
    val harmonicPower: Float = 0.4f, // 諧波佔比 (影響聲音的「厚實度」)
    val noiseFactor: Float = 0.05f,  // 氣流噪音比例
    val isMultiMotor: Boolean = true, // 是否模擬多馬達拍頻 (Beating)
    val modulationHz: Float = 0f     // 調幅頻率 (用於直昇機拍打聲，0為不啟用)
)
