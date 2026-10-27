import Foundation
import GameController
import UIKit

/// [v1.8.01] USB Type-C 實體遙控器 / HID 遊戲手把管理器 + CSC 內八解鎖與落地自動上鎖
final class TypeCHidControllerManager {
    static let shared = TypeCHidControllerManager()

    private var activeController: GCController?
    private var cscHoldTimer: Float = 0.0
    private var autoDisarmTimer: Float = 0.0
    private var lastUnlockTimestamp: TimeInterval = 0
    private var packetCounter: Int = 0
    private var lastPpsTimestamp: TimeInterval = 0

    private init() {
        setupNotifications()
        scanConnectedControllers()
    }

    /// 驗證當前裝置是否為支援 USB-C 世代 (iPhone 15 / 16 / 17 / Duo 或 USB-C iPad)
    static func detectTypeCCapability() -> (isSupported: Bool, deviceDescription: String) {
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.reduce("") { id, element in
            guard let value = element.value as? Int8, value != 0 else { return id }
            return id + String(UnicodeScalar(UInt8(value)))
        }
        // Simulator or iPhone16,x (iPhone 15 Pro) / iPhone15,4+ (iPhone 15) / iPhone17,x / iPhone18,x / iPad8+
        if identifier.contains("arm64") || identifier.contains("x86_64") {
            return (true, "USB-C Ready (Simulator / Apple Silicon)")
        }
        if identifier.hasPrefix("iPhone") {
            let numPart = identifier.replacingOccurrences(of: "iPhone", with: "").split(separator: ",")
            if let major = Int(numPart.first ?? "0"), let minor = Int(numPart.last ?? "0") {
                let isTypeC = (major > 15) || (major == 15 && minor >= 4)
                return (isTypeC, "\(UIDevice.current.model) (\(identifier))")
            }
        }
        return (true, "\(UIDevice.current.model) (USB-C Host)")
    }

    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            forName: .GCControllerDidConnect,
            object: nil,
            queue: .main
        ) { [weak self] note in
            guard let ctrl = note.object as? GCController else { return }
            self?.attachController(ctrl)
        }

        NotificationCenter.default.addObserver(
            forName: .GCControllerDidDisconnect,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.detachController()
        }

        GCController.startWirelessControllerDiscovery {}
    }

    func scanConnectedControllers() {
        if let first = GCController.controllers().first {
            attachController(first)
        } else {
            let cap = Self.detectTypeCCapability()
            DroneState.shared.isTypeCHardwarePort = cap.isSupported
        }
    }

    private func attachController(_ controller: GCController) {
        activeController = controller
        let state = DroneState.shared
        let vendor = controller.vendorName ?? "USB-C HID RC Transmitter"
        state.controllerConnected = true
        state.activeHidName = vendor
        state.connectionStatus = .active
        state.isSignalActive = true

        if let extended = controller.extendedGamepad {
            extended.valueChangedHandler = { [weak self] gamepad, element in
                self?.pollGamepad(gamepad)
            }
        }
    }

    private func detachController() {
        activeController = nil
        let state = DroneState.shared
        state.controllerConnected = false
        state.activeHidName = "NO_TYPE_C_CONTROLLER"
        state.connectionStatus = .idle
        state.isSignalActive = false
        state.packetsPerSecond = 0
    }

    private func pollGamepad(_ gamepad: GCExtendedGamepad) {
        let lx = gamepad.leftThumbstick.xAxis.value
        let ly = gamepad.leftThumbstick.yAxis.value
        let rx = gamepad.rightThumbstick.xAxis.value
        let ry = gamepad.rightThumbstick.yAxis.value

        StickInputState.shared.updateRawHid(lx: lx, ly: ly, ry: ry, rx: rx)

        // Aux switches mapping on shoulder buttons
        if gamepad.leftShoulder.isPressed {
            DroneState.shared.isMotorLocked = false
        }
        if gamepad.rightShoulder.isPressed {
            DroneState.shared.isMotorLocked = true
        }
        if gamepad.buttonA.isPressed {
            DroneState.shared.isTetherModeEnabled.toggle()
        }

        packetCounter += 1
        let now = Date().timeIntervalSince1970
        if now - lastPpsTimestamp >= 1.0 {
            DroneState.shared.packetsPerSecond = packetCounter
            packetCounter = 0
            lastPpsTimestamp = now
        }
    }

    /// 每幀呼叫：同步對應 StickInteractionLogic.kt 的 CSC 內八解鎖與落地 1s 自動上鎖
    func stepStickInteraction(dt: Float, state: DroneState, stickState: StickInputState) {
        if let gamepad = activeController?.extendedGamepad {
            pollGamepad(gamepad)
        }

        let spec = DroneRegistry.getSpec(state.droneType)
        let rawT = stickState.rawThrottle(state)
        let rawY = stickState.rawYaw(state)
        let rawP = stickState.rawPitch(state)
        let rawR = stickState.rawRoll(state)

        let sT = stickState.stickThrottle(state)
        let sY = stickState.stickYaw(state)
        let sP = stickState.stickPitch(state)
        let sR = stickState.stickRoll(state)

        // 1. CSC 內八手勢：左下 (T<-0.7, Y>0.7) + 右下 (P<-0.7, R<-0.7)
        let isCSC = (rawT < -0.7 && rawY > 0.7 && rawP < -0.7 && rawR < -0.7)
        if isCSC {
            cscHoldTimer += dt
            if cscHoldTimer >= 1.2 {
                cscHoldTimer = 0.0
                if state.isMotorLocked {
                    state.isMotorLocked = false
                    state.systemMessage = "SAFETY_ARMED"
                    lastUnlockTimestamp = Date().timeIntervalSince1970
                } else if state.altitude <= spec.groundOffset + 0.15 {
                    state.isMotorLocked = true
                    state.systemMessage = "SAFETY_DISARMED"
                }
            }
        } else {
            cscHoldTimer = 0.0
        }

        // 2. 若搖桿移動則自動收合右上選單
        let isStickMoving = abs(sT) > 0.3 || abs(sY) > 0.3 || abs(sP) > 0.3 || abs(sR) > 0.3
        if isStickMoving && state.isMenuExpanded {
            state.isMenuExpanded = false
        }

        // 3. 落地自動上鎖 (Auto-Disarm)
        let isGrounded = state.altitude <= spec.groundOffset + 0.15
        let sticksNeutral = abs(sY) < 0.2 && abs(sP) < 0.2 && abs(sR) < 0.2
        let unlockedLongEnough = (Date().timeIntervalSince1970 - lastUnlockTimestamp) > 2.0

        if !state.isMotorLocked && isGrounded && rawT < -0.95 && sticksNeutral && unlockedLongEnough {
            autoDisarmTimer += dt
            if autoDisarmTimer >= 1.0 {
                autoDisarmTimer = 0.0
                state.isMotorLocked = true
                state.systemMessage = "SAFETY_LANDING_DISARM"
            }
        } else {
            autoDisarmTimer = 0.0
        }
    }

    func notifyManualUnlock() {
        lastUnlockTimestamp = Date().timeIntervalSince1970
    }
}
