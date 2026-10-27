import Foundation
import Observation

/// [v1.8.01] 搖桿數據狀態 - 指令中樞層 (1:1 對齊 StickInputState.kt & INPUT_SYSTEM_ARCHITECTURE.md)
@Observable
final class StickInputState {
    static let shared = StickInputState()

    private var channelBuffer = [Float](repeating: 0.0, count: 125)
    private var lastRawBuffer = [Float](repeating: 0.0, count: 125)

    var visualBuffer = [Float](repeating: 0.0, count: 125)
    private var lastUiUpdateTime: TimeInterval = 0
    private let uiUpdateInterval: TimeInterval = 0.016 // ~60Hz

    // 觸控數據
    var touchLY: Float = 0.0
    var touchLX: Float = 0.0
    var touchRY: Float = 0.0
    var touchRX: Float = 0.0
    var isTouchingLeft: Bool = false
    var isTouchingRight: Bool = false

    var packetsPerSecond: Int = 0
    var isSignalActive: Bool = false

    func setChannel(index: Int, value: Float) {
        guard index >= 0 && index < 125 else { return }
        channelBuffer[index] = value
        let now = Date().timeIntervalSince1970
        if now - lastUiUpdateTime >= uiUpdateInterval {
            visualBuffer = channelBuffer
            lastUiUpdateTime = now
        }
    }

    func updateRawHid(lx: Float, ly: Float, ry: Float, rx: Float) {
        setChannel(index: 0, value: lx)
        setChannel(index: 1, value: ly)
        setChannel(index: 2, value: ry)
        setChannel(index: 3, value: rx)
    }

    func resetAll() {
        channelBuffer = [Float](repeating: 0.0, count: 125)
        visualBuffer = channelBuffer
        touchLY = 0.0
        touchLX = 0.0
        touchRY = 0.0
        touchRX = 0.0
        isTouchingLeft = false
        isTouchingRight = false
    }

    private func resolveValue(
        state: DroneState,
        mapping: ChannelMapping,
        isLeft: Bool,
        funcKey: String,
        isVisual: Bool,
        ignoreRate: Bool = false
    ) -> Float {
        let axis = mapping.axis
        let rawValue = (axis >= 0 && axis < 125) ? channelBuffer[axis] : 0.0

        // 1. 智慧仲裁：實體優先與意圖偵測
        var isMoving = false
        if state.controllerConnected && axis >= 0 && axis < 125 {
            let delta = abs(rawValue - lastRawBuffer[axis])
            if delta > 0.01 {
                lastRawBuffer[axis] = rawValue
                isMoving = true
            }
        }

        let isTouching = isLeft ? isTouchingLeft : isTouchingRight

        // 若正在觸控（或未接實體手把），優先走觸控路徑
        if (!isMoving && isTouching) || !state.controllerConnected {
            let touchVal: Float
            switch funcKey {
            case "T": touchVal = isLeft ? touchLY : touchRY
            case "Y": touchVal = isLeft ? touchLX : touchRX
            case "P": touchVal = isLeft ? touchLY : touchRY
            case "R": touchVal = isLeft ? touchLX : touchRX
            default: touchVal = 0.0
            }
            if ignoreRate { return touchVal }
            let finalCmd = InputProcessor.processVirtual(touch: touchVal, expo: state.getExpo(funcKey), rate: state.getRate(funcKey, stickValue: touchVal))
            let rate = max(state.getRate(funcKey, stickValue: touchVal), 0.01)
            return isVisual ? (finalCmd / rate) : finalCmd
        }

        // 2. 實體 USB-C HID 路徑
        let buffer = isVisual ? visualBuffer : channelBuffer
        let currentRaw = (axis >= 0 && axis < 125) ? buffer[axis] : 0.0

        let normalized: Float
        if mapping.max != mapping.min {
            let denom = max(currentRaw > mapping.center ? (mapping.max - mapping.center) : (mapping.center - mapping.min), 0.01)
            normalized = (currentRaw - mapping.center) / denom
        } else {
            normalized = currentRaw - mapping.center
        }

        let processed = mapping.inverted ? -normalized : normalized
        if ignoreRate { return processed }

        let finalCmd = InputProcessor.process(
            raw: processed,
            deadzone: state.joystickDeadzone,
            expo: state.getExpo(funcKey),
            rate: state.getRate(funcKey, stickValue: processed),
            mapping: mapping
        )
        let rate = max(state.getRate(funcKey, stickValue: processed), 0.01)
        return isVisual ? (finalCmd / rate) : finalCmd
    }

    // 物理指令集 (供 PhysicsEngine 調用)
    func stickThrottle(_ state: DroneState) -> Float {
        let idx = getIdx("T", state)
        return resolveValue(state: state, mapping: state.getMappingSnapshot()[idx], isLeft: getSide("T", state), funcKey: "T", isVisual: false)
    }

    func stickYaw(_ state: DroneState) -> Float {
        let idx = getIdx("Y", state)
        return resolveValue(state: state, mapping: state.getMappingSnapshot()[idx], isLeft: getSide("Y", state), funcKey: "Y", isVisual: false)
    }

    func stickPitch(_ state: DroneState) -> Float {
        let idx = getIdx("P", state)
        return resolveValue(state: state, mapping: state.getMappingSnapshot()[idx], isLeft: getSide("P", state), funcKey: "P", isVisual: false)
    }

    func stickRoll(_ state: DroneState) -> Float {
        let idx = getIdx("R", state)
        return resolveValue(state: state, mapping: state.getMappingSnapshot()[idx], isLeft: getSide("R", state), funcKey: "R", isVisual: false)
    }

    // 原始行程指令集 (專供 CSC 內八解鎖判定)
    func rawThrottle(_ state: DroneState) -> Float {
        let idx = getIdx("T", state)
        return resolveValue(state: state, mapping: state.getMappingSnapshot()[idx], isLeft: getSide("T", state), funcKey: "T", isVisual: false, ignoreRate: true)
    }

    func rawYaw(_ state: DroneState) -> Float {
        let idx = getIdx("Y", state)
        return resolveValue(state: state, mapping: state.getMappingSnapshot()[idx], isLeft: getSide("Y", state), funcKey: "Y", isVisual: false, ignoreRate: true)
    }

    func rawPitch(_ state: DroneState) -> Float {
        let idx = getIdx("P", state)
        return resolveValue(state: state, mapping: state.getMappingSnapshot()[idx], isLeft: getSide("P", state), funcKey: "P", isVisual: false, ignoreRate: true)
    }

    func rawRoll(_ state: DroneState) -> Float {
        let idx = getIdx("R", state)
        return resolveValue(state: state, mapping: state.getMappingSnapshot()[idx], isLeft: getSide("R", state), funcKey: "R", isVisual: false, ignoreRate: true)
    }

    // 視覺同步集 (供 HUD 虛擬搖桿顯示)
    func stickLX(_ state: DroneState) -> Float {
        resolveValue(state: state, mapping: state.getMappingSnapshot()[1], isLeft: true, funcKey: getFunc(1, state), isVisual: true)
    }

    func stickLY(_ state: DroneState) -> Float {
        resolveValue(state: state, mapping: state.getMappingSnapshot()[0], isLeft: true, funcKey: getFunc(0, state), isVisual: true)
    }

    func stickRX(_ state: DroneState) -> Float {
        resolveValue(state: state, mapping: state.getMappingSnapshot()[3], isLeft: false, funcKey: getFunc(3, state), isVisual: true)
    }

    func stickRY(_ state: DroneState) -> Float {
        resolveValue(state: state, mapping: state.getMappingSnapshot()[2], isLeft: false, funcKey: getFunc(2, state), isVisual: true)
    }

    // Mode 1-4 路由
    private func getIdx(_ funcKey: String, _ s: DroneState) -> Int {
        switch funcKey {
        case "T": return (s.joystickMode == 1 || s.joystickMode == 4) ? 2 : 0
        case "Y": return (s.joystickMode == 3 || s.joystickMode == 4) ? 3 : 1
        case "P": return (s.joystickMode == 1 || s.joystickMode == 4) ? 0 : 2
        case "R": return (s.joystickMode == 3 || s.joystickMode == 4) ? 1 : 3
        default: return 0
        }
    }

    private func getSide(_ funcKey: String, _ s: DroneState) -> Bool {
        getIdx(funcKey, s) < 2
    }

    private func getFunc(_ idx: Int, _ s: DroneState) -> String {
        switch idx {
        case 0: return (s.joystickMode == 1 || s.joystickMode == 4) ? "P" : "T"
        case 1: return (s.joystickMode == 3 || s.joystickMode == 4) ? "R" : "Y"
        case 2: return (s.joystickMode == 1 || s.joystickMode == 4) ? "T" : "P"
        case 3: return (s.joystickMode == 3 || s.joystickMode == 4) ? "Y" : "R"
        default: return "T"
        }
    }
}
