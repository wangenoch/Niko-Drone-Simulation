package com.horizon.caadronesimulator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class DroneSoundManager {
    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    @Volatile private var targetFreq = 100f
    @Volatile private var currentFreq = 100f
    @Volatile private var volume = 0f
    @Volatile private var windVolume = 0f

    fun start() {
        if (isPlaying) return
        isPlaying = true
        val minBufferSize = AudioTrack.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
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
            val samples = ShortArray(minBufferSize)
            var angle = 0f
            while (isPlaying) {
                // 平滑頻率過渡
                currentFreq = currentFreq * 0.92f + targetFreq * 0.08f
                for (i in samples.indices) {
                    // 混合多個諧波以模擬馬達聲 (鋸齒波+正弦波)
                    val motor = (sin(angle) + 0.4f * sin(angle * 2.05f) + 0.3f * (angle % (2*PI.toFloat()) / PI.toFloat() - 1f)) / 1.7f
                    // 模擬風聲 (隨機噪音)
                    val wind = (Math.random().toFloat() * 2f - 1f) * windVolume
                    
                    samples[i] = ((motor * volume + wind) * 32767).toInt()
                        .coerceIn(-32768, 32767).toShort()
                    
                    angle += 2f * PI.toFloat() * currentFreq / 44100f
                    if (angle > 2f * PI.toFloat()) angle -= 2f * PI.toFloat()
                }
                audioTrack?.write(samples, 0, samples.size)
            }
        }.start()
    }

    fun update(isLocked: Boolean, throttle: Float, speed: Float, windLevel: Int, isMuted: Boolean) {
        if (isMuted || isLocked) {
            volume = 0f
            targetFreq = 100f
            windVolume = 0f // 靜音時同時關閉風聲
        } else {
            // 馬達音量基礎值為 2.0f，隨動力增加，最高上限 3.0f
            volume = (2.0f + (throttle + 1f) * 0.4f + (speed * 0.05f)).coerceAtMost(3.0f)
            // 頻率隨動力增加
            targetFreq = 120f + (throttle + 1f) * 80f + (speed * 5f)
            // 風聲隨環境風力與飛行速度增加
            windVolume = (windLevel * 0.04f + speed * 0.01f).coerceAtMost(0.3f)
        }
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
    }
}
