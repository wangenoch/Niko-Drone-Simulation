import SwiftUI

@main
struct NikoDroneSimulatorApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            MainAppScreen()
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                break
            case .inactive, .background:
                ConfigurationStore.shared.saveSettings(DroneState.shared)
                DroneSoundManager.shared.stop()
            @unknown default:
                break
            }
        }
    }
}
