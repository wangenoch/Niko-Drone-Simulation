package com.horizon.caadronesimulator.audio

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
            while (isPlaying) {
                // 平滑頻率過渡
                currentFreq = currentFreq * 0.98f + targetFreq * 0.02f // 調低係數使音頻變化更絲滑
                
                val currentVol = volume // 取得快照避免頻繁讀取 Volatile
                val currentWind = windVolume
                
                for (i in samples.indices) {
                    // Additive Synthesis 模擬馬達
                    val f1 = sin(angle)
                    val f2 = sin(angle * 2.01f) * 0.5f
                    val f3 = sin(angle * 3.98f) * 0.2f
                    val motor = (f1 + f2 + f3) / 1.7f
                    
                    // 隨機噪音優化：使用更快的 LCG 隨機算法替代 Math.random() (選做)
                    val airNoise = ((System.nanoTime() % 1000) / 1000f * 2f - 1f) * 0.05f
                    
                    val finalSample = ((motor * currentVol + airNoise * currentVol + (airNoise * currentWind)) * 28000).toInt()
                    
                    samples[i] = finalSample.coerceIn(-32768, 32767).toShort()
                    
                    angle += 2f * PI.toFloat() * currentFreq / 44100f
                    if (angle > 2f * PI.toFloat()) angle -= 2f * PI.toFloat()
                }
                audioTrack?.write(samples, 0, samples.size)
            }
        }.apply {
            priority = Thread.MAX_PRIORITY // 提升音訊執行緒優先權
            start()
        }
    }

    /**
     * 更新音效參數 (Project C: 內部頻率節流)
     */
    private var lastUpdateTime = 0L
    fun update(isLocked: Boolean, throttle: Float, speed: Float, windLevel: Int, isMuted: Boolean, distance: Float = 0f) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 32 && !isLocked && !isMuted) return // 約 30fps 更新率，節省 CPU
        lastUpdateTime = now

        if (isMuted || isLocked) {
            volume = 0f
            targetFreq = 100f
            windVolume = 0f
        } else {
            val throttleFactor = (throttle + 1f) / 2f
            val baseVolume = (0.25f + throttleFactor * 0.25f + (speed * 0.005f)).coerceAtMost(0.6f)
            val falloff = 1.0f / (1.0f + (distance.coerceAtLeast(2.0f) - 2.0f) * 0.15f)
            volume = (baseVolume * falloff).coerceIn(0.05f, 0.6f)
            
            val freqLoss = (distance * 0.2f).coerceAtMost(20f)
            targetFreq = (150f + throttleFactor * 120f + (speed * 2f)) - freqLoss
            windVolume = (windLevel * 0.03f + speed * 0.005f).coerceAtMost(0.2f)
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
