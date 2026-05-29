package com.horizon.nikonikodronesimulator.audio

import android.media.AudioAttributes // 音訊屬性設定
import android.media.AudioFormat // 音訊格式定義
import android.media.AudioTrack // 底層音訊串流輸出
import kotlin.math.PI // 圓周率
import kotlin.math.sin // 正弦函數

/**
 * [v1.2.68] 模擬器音效引擎
 * 實作 Project C: 音效引擎池化與運算優化
 */
class DroneSoundManager {
    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    @Volatile private var targetFreq = 100f
    @Volatile private var currentFreq = 100f
    @Volatile private var volume = 0f
    @Volatile private var windVolume = 0f
    
    // [v1.7.17] 實時聲學 Profile 緩衝
    @Volatile private var activeProfile = com.horizon.nikonikodronesimulator.model.SoundProfile()

    private var lastUpdateTime = 0L

    // Project C: 預建立樣本緩衝區，避免在迴圈中重複配置
    private var cachedSamples: ShortArray? = null

    fun start() {
        if (isPlaying) return
        isPlaying = true
        val minBufferSize = AudioTrack.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        cachedSamples = ShortArray(minBufferSize)
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        
        Thread {
            val samples = cachedSamples ?: return@Thread
            var angle = 0f
            var modAngle = 0f // [v1.7.17] 用於直昇機調幅
            
            while (isPlaying) {
                currentFreq = currentFreq * 0.98f + targetFreq * 0.02f
                
                val currentVol = volume
                val currentWind = windVolume
                val profile = activeProfile // 取得 Profile 快照
                
                for (i in samples.indices) {
                    // 1. [馬達合成核心]
                    val f1 = sin(angle)
                    val f2 = sin(angle * 2.01f) * profile.harmonicPower
                    val f3 = sin(angle * 3.98f) * (profile.harmonicPower * 0.4f)
                    
                    val motor = if (profile.isMultiMotor) {
                        val beat = sin(angle * 1.005f) * 0.5f + sin(angle * 0.994f) * 0.3f
                        (f1 + f2 + f3 + beat) / (1.7f + profile.harmonicPower)
                    } else {
                        (f1 + f2 + f3) / (1.0f + profile.harmonicPower)
                    }
                    
                    // 2. [直昇機調幅核心]
                    val modulation = if (profile.modulationHz > 0) {
                        val depth = 0.6f + (currentVol * 0.4f)
                        (1.0f - depth) + (sin(modAngle) + 1.0f) * 0.5f * depth
                    } else 1.0f
                    
                    // 3. [環境風聲核心 - v1.7.17 物理過濾版]
                    // 隨機噪音源 (粉紅噪音傾向)
                    val rawNoise = ((System.nanoTime() % 1000) / 1000f * 2f - 1f)
                    
                    // 動態共振模擬：隨風力與航速改變音調
                    // 透過調整 noiseFactor 的時域變化來模擬帶通濾波效果
                    val windOsc = sin(angle * 0.05f) * 0.2f + 0.8f // 低頻音量起伏 (陣風感)
                    val windSample = rawNoise * profile.noiseFactor * windOsc
                    
                    val finalSample = ((motor * currentVol * modulation + windSample * currentVol + (windSample * currentWind)) * 28000).toInt()
                    samples[i] = finalSample.coerceIn(-32768, 32767).toShort()
                    
                    // 角度累積
                    angle += 2f * PI.toFloat() * currentFreq / 44100f
                    if (angle > 2f * PI.toFloat()) angle -= 2f * PI.toFloat()
                    
                    if (profile.modulationHz > 0) {
                        modAngle += 2f * PI.toFloat() * profile.modulationHz / 44100f
                        if (modAngle > 2f * PI.toFloat()) modAngle -= 2f * PI.toFloat()
                    }
                }
                audioTrack?.write(samples, 0, samples.size)
            }
        }.apply {
            priority = Thread.MAX_PRIORITY // 提升音訊執行緒優先權
            start()
        }
    }

    /**
     * [v1.5.3] 自驅動更新：音效引擎直接觀察 DroneState
     */
    fun updateSelfDriven(state: com.horizon.nikonikodronesimulator.model.DroneState, stickInput: com.horizon.nikonikodronesimulator.model.StickInputState) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 32 && !state.isMotorLocked && !state.isMuted) return 
        lastUpdateTime = now

        if (state.isMuted || state.isMotorLocked) {
            volume = 0f
            targetFreq = 100f
            windVolume = 0f
        } else {
            val module = com.horizon.nikonikodronesimulator.model.DroneRegistry.getModule(state.droneType)
            activeProfile = module.soundProfile // [v1.7.17] 即時獲取機種專屬聲學基因
            
            val throttle = stickInput.stickThrottle(state)
            val throttleFactor = (throttle + 1f) / 2f
            
            // 計算與觀察者的 3D 距離 (用於音量衰減)
            val dx = state.posX.toDouble(); val dy = (state.altitude - 1.6f).toDouble(); val dz = (state.posZ - (-6.0f)).toDouble()
            val distance = kotlin.math.sqrt(dx*dx + dy*dy + dz*dz).toFloat()
            
            val baseVolume = (0.25f + throttleFactor * 0.25f + (state.speed * 0.005f)).coerceAtMost(1.0f)
            val falloff = 1.0f / (1.0f + (distance.coerceAtLeast(2.0f) - 2.0f) * 0.15f)
            
            // [v1.7.17] 套用 AppConfig 飛機馬達主音量增益
            volume = (baseVolume * falloff * com.horizon.nikonikodronesimulator.model.AppConfig.AudioDefaults.MASTER_MOTOR_VOLUME).coerceIn(0.05f, 1.0f)
            
            val freqLoss = (distance * 0.2f).coerceAtMost(20f)
            targetFreq = (activeProfile.baseFreq + throttleFactor * 120f + (state.speed * 2f)) - freqLoss
            
            // [v1.7.17] 套用 AppConfig 環境風聲主音量增益
            windVolume = ((state.windLevel * 0.03f + state.speed * 0.005f).coerceAtMost(0.2f)) * com.horizon.nikonikodronesimulator.model.AppConfig.AudioDefaults.MASTER_ENV_VOLUME
        }
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
        cachedSamples = null
    }
}
