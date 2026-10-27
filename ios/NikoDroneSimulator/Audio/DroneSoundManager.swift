import Foundation
import AVFoundation

/// [v1.8.01] 模擬器即時合成音效引擎 (1:1 對齊 DroneSoundManager.kt 谐波與直昇機調幅邏輯)
final class DroneSoundManager {
    static let shared = DroneSoundManager()

    private var engine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private var isPlaying = false

    private var targetFreq: Float = 100.0
    private var currentFreq: Float = 100.0
    private var volume: Float = 0.0
    private var windVolume: Float = 0.0
    private var activeProfile = SoundProfile()

    private var angle: Float = 0.0
    private var modAngle: Float = 0.0

    private init() {}

    func start() {
        guard !isPlaying else { return }
        let audioEngine = AVAudioEngine()
        let mainMixer = audioEngine.mainMixerNode
        let outputFormat = mainMixer.outputFormat(forBus: 0)
        let sampleRate = Float(outputFormat.sampleRate > 0 ? outputFormat.sampleRate : 44100.0)

        guard let format = AVAudioFormat(standardFormatWithSampleRate: Double(sampleRate), channels: 1) else { return }

        let node = AVAudioSourceNode(format: format) { [weak self] _, _, frameCount, audioBufferList -> OSStatus in
            guard let self = self else { return noErr }
            let ablPointer = UnsafeMutableAudioBufferListPointer(audioBufferList)

            for frame in 0..<Int(frameCount) {
                self.currentFreq = self.currentFreq * 0.9995 + self.targetFreq * 0.0005
                let currentVol = self.volume
                let currentWind = self.windVolume
                let profile = self.activeProfile

                // 1. 馬達合成核心 (f1, f2, f3 諧波 + 雙馬達拍頻)
                let f1 = sin(self.angle)
                let f2 = sin(self.angle * 2.01) * profile.harmonicPower
                let f3 = sin(self.angle * 3.98) * (profile.harmonicPower * 0.4)

                let motor: Float
                if profile.isMultiMotor {
                    let beat = sin(self.angle * 1.005) * 0.5 + sin(self.angle * 0.994) * 0.3
                    motor = (f1 + f2 + f3 + beat) / (1.7 + profile.harmonicPower)
                } else {
                    motor = (f1 + f2 + f3) / (1.0 + profile.harmonicPower)
                }

                // 2. 直昇機 18Hz 週期性槳葉拍擊調幅
                let modFactor: Float
                if profile.modulationHz > 0.0 {
                    modFactor = 0.75 + 0.25 * sin(self.modAngle)
                } else {
                    modFactor = 1.0
                }

                // 3. 氣流與環境風聲白噪音
                let whiteNoise = Float.random(in: -1.0...1.0)
                let propNoise = whiteNoise * profile.noiseFactor
                let envWind = whiteNoise * currentWind * AppConfig.AudioDefaults.MASTER_ENV_VOLUME * 0.18

                let finalMotor = (motor * modFactor + propNoise) * currentVol * AppConfig.AudioDefaults.MASTER_MOTOR_VOLUME * 0.25
                let sample = min(max(finalMotor + envWind, -0.95), 0.95)

                self.angle += 2.0 * .pi * self.currentFreq / sampleRate
                if self.angle > 2.0 * .pi * 100.0 { self.angle -= 2.0 * .pi * 100.0 }

                if profile.modulationHz > 0.0 {
                    self.modAngle += 2.0 * .pi * profile.modulationHz / sampleRate
                    if self.modAngle > 2.0 * .pi * 100.0 { self.modAngle -= 2.0 * .pi * 100.0 }
                }

                for buffer in ablPointer {
                    let buf = buffer.mData?.assumingMemoryBound(to: Float.self)
                    buf?[frame] = sample
                }
            }
            return noErr
        }

        audioEngine.attach(node)
        audioEngine.connect(node, to: mainMixer, format: format)

        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
            try AVAudioSession.sharedInstance().setActive(true)
            try audioEngine.start()
            self.engine = audioEngine
            self.sourceNode = node
            self.isPlaying = true
        } catch {
            self.isPlaying = false
        }
    }

    func update(
        rpmFactor: Float,
        isLocked: Bool,
        isMuted: Bool,
        windLevel: Int,
        droneSpeed: Float,
        droneType: String
    ) {
        if isMuted {
            volume = 0.0
            windVolume = 0.0
            return
        }
        if !isPlaying {
            start()
        }

        let mod = DroneRegistry.getModule(droneType)
        activeProfile = mod.soundProfile

        if isLocked {
            volume = 0.0
            targetFreq = activeProfile.baseFreq * 0.5
        } else {
            let clampedRpm = min(max(rpmFactor, 0.0), 1.5)
            targetFreq = activeProfile.baseFreq * (0.65 + clampedRpm * 0.85) + (droneSpeed * 4.5)
            volume = min(max(0.15 + clampedRpm * 0.65, 0.0), 1.0)
        }

        windVolume = min(max(Float(windLevel) / 6.0 + (droneSpeed / 25.0), 0.0), 1.0)
    }

    func stop() {
        engine?.stop()
        isPlaying = false
    }
}
