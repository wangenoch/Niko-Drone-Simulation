import SwiftUI

/// [v1.8.01] 整合式戰術設定選單 (1:1 對齊 UnifiedSettingsScreen.kt + iPhone Duo 折疊模式控制)
struct UnifiedSettingsSheet: View {
    @Bindable var state: DroneState
    var stickState: StickInputState
    var onResetSimulation: () -> Void

    var body: some View {
        let theme = TacticalColors.current(state)

        ZStack {
            Color.black.opacity(0.65).ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack {
                    Image(systemName: "slider.horizontal.3")
                        .foregroundStyle(theme.primary)
                    Text(L10n.tr("settings_title"))
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(theme.textPrimary)
                    Spacer()
                    Text("v\(AppConfig.CURRENT_VERSION) (iOS Type-C & Duo)")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(theme.textSecondary)
                    Button {
                        ConfigurationStore.shared.saveSettings(state)
                        state.showSettings = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(theme.textSecondary)
                    }
                    .padding(.leading, 8)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(Color.black.opacity(0.35))

                Divider().background(theme.border)

                HStack(spacing: 0) {
                    // Left Tab Sidebar
                    ScrollView {
                        VStack(spacing: 6) {
                            settingsTabButton(.controller, icon: "gamecontroller.fill", title: L10n.tr("tab_controller"))
                            settingsTabButton(.environment, icon: "wind", title: L10n.tr("tab_environment"))
                            settingsTabButton(.droneSelection, icon: "airplane.circle.fill", title: L10n.tr("tab_drone"))
                            settingsTabButton(.camera, icon: "video.fill", title: L10n.tr("tab_camera"))
                            settingsTabButton(.system, icon: "rectangle.split.2x1.fill", title: L10n.tr("tab_system"))
                            settingsTabButton(.theme, icon: "paintbrush.fill", title: L10n.tr("tab_theme"))
                        }
                        .padding(10)
                    }
                    .frame(width: 175)
                    .background(Color.black.opacity(0.25))

                    Divider().background(theme.border)

                    // Right Tab Content
                    ScrollView {
                        VStack(alignment: .leading, spacing: 14) {
                            switch state.settingsTab {
                            case .controller:
                                controllerTabContent(theme)
                            case .environment:
                                environmentTabContent(theme)
                            case .droneSelection:
                                droneSelectionTabContent(theme)
                            case .camera:
                                cameraTabContent(theme)
                            case .system:
                                systemAndDuoTabContent(theme)
                            case .theme:
                                themeTabContent(theme)
                            }
                        }
                        .padding(16)
                    }
                }
            }
            .frame(maxWidth: 760, maxHeight: 440)
            .background(RoundedRectangle(cornerRadius: 16).fill(theme.panel))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(theme.border, lineWidth: 1.5))
            .padding(16)
        }
    }

    private func settingsTabButton(_ tab: SettingsTab, icon: String, title: String) -> some View {
        let theme = TacticalColors.current(state)
        let isSelected = (state.settingsTab == tab)
        return Button {
            state.settingsTab = tab
        } label: {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 13))
                Text(title)
                    .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                Spacer()
            }
            .foregroundStyle(isSelected ? theme.primary : theme.textPrimary)
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? theme.primary.opacity(0.18) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? theme.primary.opacity(0.5) : Color.clear, lineWidth: 1)
            )
        }
    }

    // MARK: - 1. 搖桿與 USB Type-C 分頁
    @ViewBuilder
    private func controllerTabContent(_ theme: TacticalColors) -> some View {
        let typeCCap = TypeCHidControllerManager.detectTypeCCapability()

        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "cable.connector.horizontal")
                    .foregroundStyle(theme.primary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("USB Type-C 實體遙控器 / HID 狀態: \(state.controllerConnected ? state.activeHidName : "未連接 (使用觸控搖桿)")")
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundStyle(state.controllerConnected ? theme.success : theme.warning)
                    Text("硬體介面檢查: \(typeCCap.deviceDescription) | PPS: \(state.packetsPerSecond) Hz")
                        .font(.system(size: 10.5, design: .monospaced))
                        .foregroundStyle(theme.textSecondary)
                }
                Spacer()
                Button("重新掃描 USB-C") {
                    TypeCHidControllerManager.shared.scanConnectedControllers()
                }
                .font(.system(size: 11, weight: .bold))
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 6).fill(theme.primary.opacity(0.25)))
                .foregroundStyle(theme.primary)
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.black.opacity(0.3)))

            // Mode 1 - 4 選擇
            Text("搖桿操作手別 (Stick Mode)")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(theme.textPrimary)

            HStack(spacing: 8) {
                ForEach([1, 2, 3, 4], id: \.self) { m in
                    let label = (m == 1) ? "Mode 1 (日本手)" : (m == 2 ? "Mode 2 (美國手)" : "Mode \(m)")
                    Button {
                        state.joystickMode = m
                    } label: {
                        Text(label)
                            .font(.system(size: 11.5, weight: .bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 8).fill(state.joystickMode == m ? theme.primary : Color.black.opacity(0.3)))
                            .foregroundStyle(state.joystickMode == m ? .black : theme.textPrimary)
                    }
                }
            }

            // 手感滑桿
            sliderRow("全域靈敏度 (Rate)", value: $state.globalRate, range: 0.4...2.0, format: "%.2f")
            sliderRow("指數柔和曲線 (Expo)", value: $state.globalExpo, range: 0.0...0.8, format: "%.2f")
            sliderRow("搖桿中位死區 (Deadzone)", value: $state.joystickDeadzone, range: 0.0...0.2, format: "%.2f")

            Toggle(isOn: $state.useIdleWakeupPatch) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("起動動力增益 (Idle Wakeup & Escape Impulse)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(theme.textPrimary)
                    Text("消除地面起飛數值下溢與黏滯感 (v1.7.35 核心守護補丁)")
                        .font(.system(size: 10))
                        .foregroundStyle(theme.textSecondary)
                }
            }
            .tint(theme.primary)
        }
    }

    // MARK: - 2. 氣候與風場分頁
    @ViewBuilder
    private func environmentTabContent(_ theme: TacticalColors) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("風力等級 (Wind Level): L\(state.windLevel)")
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(theme.textPrimary)
                Spacer()
                ForEach(0...5, id: \.self) { lvl in
                    Button("L\(lvl)") {
                        state.windLevel = lvl
                        if lvl > 0 && state.windDirection == AppConfig.WIND_DIR_NONE {
                            state.windDirection = AppConfig.WIND_DIR_N
                        }
                    }
                    .font(.system(size: 11, weight: .bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 6).fill(state.windLevel == lvl ? theme.warning : Color.black.opacity(0.3)))
                    .foregroundStyle(state.windLevel == lvl ? .black : theme.textPrimary)
                }
            }

            Text("風向來源 (Wind Source Direction)")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(theme.textPrimary)

            let dirs = [
                AppConfig.WIND_DIR_NONE, AppConfig.WIND_DIR_N, AppConfig.WIND_DIR_NE,
                AppConfig.WIND_DIR_E, AppConfig.WIND_DIR_SE, AppConfig.WIND_DIR_S,
                AppConfig.WIND_DIR_SW, AppConfig.WIND_DIR_W, AppConfig.WIND_DIR_NW,
                AppConfig.WIND_DIR_RANDOM
            ]
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 5), spacing: 6) {
                ForEach(dirs, id: \.self) { dir in
                    Button(dir) {
                        state.windDirection = dir
                        if dir == AppConfig.WIND_DIR_RANDOM {
                            state.randomWindAngle = Float.random(in: 0..<360)
                        }
                    }
                    .font(.system(size: 11, weight: .bold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 6).fill(state.windDirection == dir ? theme.primary : Color.black.opacity(0.3)))
                    .foregroundStyle(state.windDirection == dir ? .black : theme.textPrimary)
                }
            }

            Toggle("進階高度風切與分形脈衝 (Hardcore Wind Physics)", isOn: $state.useHardcorePhysics)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle("垂直升降氣流模擬 (Vertical Draft)", isOn: $state.enableVerticalDraft)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            sliderRow("太陽方位角 (Sun Position)", value: $state.sunPosition, range: 0.1...0.9, format: "%.2f")
        }
    }

    // MARK: - 3. 機種選擇分頁
    @ViewBuilder
    private func droneSelectionTabContent(_ theme: TacticalColors) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(DroneRegistry.getAllSpecs()) { spec in
                let isSelected = (state.droneType == spec.id)
                let mod = DroneRegistry.getModule(spec.id)
                Button {
                    state.droneType = spec.id
                    onResetSimulation()
                } label: {
                    HStack(spacing: 14) {
                        Text(spec.icon)
                            .font(.system(size: 28))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(L10n.tr(spec.name))
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(isSelected ? theme.primary : theme.textPrimary)
                            Text("軸距: \(mod.hardwareSpecs.wheelbaseMm)mm | 重量: \(String(format: "%.1f", mod.hardwareSpecs.takeoffWeightKg))kg | 推力基因: \(String(format: "%.0f", spec.physicsPower)) | 續航: \(spec.flightTimeMin)min")
                                .font(.system(size: 10.5, design: .monospaced))
                                .foregroundStyle(theme.textSecondary)
                        }
                        Spacer()
                        if isSelected {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(theme.primary)
                        }
                    }
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 10).fill(isSelected ? theme.primary.opacity(0.15) : Color.black.opacity(0.3)))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(isSelected ? theme.primary : theme.border, lineWidth: 1))
                }
            }
        }
    }

    // MARK: - 4. 視覺與鏡頭分頁
    @ViewBuilder
    private func cameraTabContent(_ theme: TacticalColors) -> some View {
        let modes: [(String, String)] = [
            (AppConfig.CAM_MODE_STATION_TRACK, L10n.tr("visual_cam_mode_station_track")),
            (AppConfig.CAM_MODE_STATION_SMART, L10n.tr("visual_cam_mode_station_smart")),
            (AppConfig.CAM_MODE_STATION_FIXED, L10n.tr("visual_cam_mode_station_fixed")),
            (AppConfig.CAM_MODE_FOLLOW, L10n.tr("visual_cam_mode_follow")),
            (AppConfig.CAM_MODE_FPV, L10n.tr("visual_cam_mode_fpv")),
            (AppConfig.CAM_MODE_OBS, L10n.tr("visual_cam_mode_obs"))
        ]

        VStack(alignment: .leading, spacing: 10) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                ForEach(modes, id: \.0) { item in
                    Button {
                        state.cameraMode = item.0
                    } label: {
                        Text(item.1)
                            .font(.system(size: 11.5, weight: .bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 8).fill(state.cameraMode == item.0 ? theme.primary : Color.black.opacity(0.3)))
                            .foregroundStyle(state.cameraMode == item.0 ? .black : theme.textPrimary)
                    }
                }
            }

            sliderRow("光學變焦倍率 (Zoom Factor)", value: $state.zoomFactor, range: 0.5...3.0, format: "%.1fX")
            sliderRow("觀察員高度 (Observer Height)", value: $state.observerHeight, range: 1.2...12.0, format: "%.1fm")
            sliderRow("鏡頭俯仰角 (Camera Tilt)", value: $state.observerTilt, range: -25.0...45.0, format: "%.0f°")

            Toggle("AR 地面垂直投影定位環 (Ground Anchor)", isOn: $state.showGroundAnchor)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle("遠距離姿態放大助手視窗 (Zoom Assistant PiP)", isOn: $state.enableZoomAssistant)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle("顯示 3D 飛行軌跡與障礙物", isOn: $state.showObstacles)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)
        }
    }

    // MARK: - 5. 系統與 iPhone Duo 折疊特性分頁
    @ViewBuilder
    private func systemAndDuoTabContent(_ theme: TacticalColors) -> some View {
        let postures: [(FoldablePostureMode, String)] = [
            (.auto, L10n.tr("duo_posture_auto")),
            (.standard, L10n.tr("duo_posture_standard")),
            (.duoVerticalDeck, L10n.tr("duo_posture_deck")),
            (.duoBookSplit, L10n.tr("duo_posture_book")),
            (.duoTentFlex, L10n.tr("duo_posture_tent"))
        ]

        VStack(alignment: .leading, spacing: 10) {
            Text("📱 iPhone Duo / 折疊螢幕自適應佈局模式 (Foldable Posture)")
                .font(.system(size: 12.5, weight: .bold))
                .foregroundStyle(theme.primary)

            ForEach(postures, id: \.0) { item in
                Button {
                    state.foldablePosture = item.0
                } label: {
                    HStack {
                        Image(systemName: state.foldablePosture == item.0 ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(theme.primary)
                        Text(item.1)
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(theme.textPrimary)
                        Spacer()
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 8).fill(state.foldablePosture == item.0 ? theme.primary.opacity(0.16) : Color.black.opacity(0.25)))
                }
            }

            Divider().background(theme.border)

            Toggle("繫留練習模式 (Tethered Mentor: 限高 3.5m / 半徑 2.0m)", isOn: $state.isTetherModeEnabled)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle("地面效應模擬 (Ground Effect Lift)", isOn: $state.enableGroundEffect)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.warning)

            Toggle("非中心點力矩模擬 (Multi-point Force)", isOn: $state.enableNonCenterForce)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.warning)

            Toggle("專業考照嚴格降落損毀判定 (2.2 m/s 閾值)", isOn: $state.useStrictLanding)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle("靜音模式 (Mute Flight Audio)", isOn: $state.isMuted)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            HStack {
                Text("介面語言 (Language):")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(theme.textPrimary)
                ForEach([("zh", "繁體中文"), ("en", "English"), ("ja", "日本語")], id: \.0) { lang in
                    Button(lang.1) {
                        state.appLanguage = lang.0
                    }
                    .font(.system(size: 11, weight: .bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 6).fill(state.appLanguage == lang.0 ? theme.primary : Color.black.opacity(0.3)))
                    .foregroundStyle(state.appLanguage == lang.0 ? .black : theme.textPrimary)
                }
            }
        }
    }

    // MARK: - 6. 主題與關於分頁
    @ViewBuilder
    private func themeTabContent(_ theme: TacticalColors) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Button {
                    state.appTheme = AppConfig.THEME_CLASSIC
                } label: {
                    Text("戰術深色主題 (Classic Tactical Dark)")
                        .font(.system(size: 12, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 10).fill(state.appTheme == AppConfig.THEME_CLASSIC ? theme.primary : Color.black.opacity(0.3)))
                        .foregroundStyle(state.appTheme == AppConfig.THEME_CLASSIC ? .black : theme.textPrimary)
                }

                Button {
                    state.appTheme = AppConfig.THEME_LIGHT
                } label: {
                    Text("戶外高對比亮色 (Daylight High-Contrast)")
                        .font(.system(size: 12, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 10).fill(state.appTheme == AppConfig.THEME_LIGHT ? theme.primary : Color.black.opacity(0.3)))
                        .foregroundStyle(state.appTheme == AppConfig.THEME_LIGHT ? .black : theme.textPrimary)
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Developer: \(AppConfig.DEVELOPER) | Version: v\(AppConfig.CURRENT_VERSION)")
                    .font(.system(size: 11.5, weight: .bold, design: .monospaced))
                    .foregroundStyle(theme.textPrimary)
                Text(L10n.tr("notice_special_thanks"))
                    .font(.system(size: 11))
                    .foregroundStyle(theme.textSecondary)
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.black.opacity(0.25)))
        }
    }

    private func sliderRow(_ title: String, value: Binding<Float>, range: ClosedRange<Float>, format: String) -> some View {
        let theme = TacticalColors.current(state)
        return VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(title)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(theme.textPrimary)
                Spacer()
                Text(String(format: format, value.wrappedValue))
                    .font(.system(size: 11.5, weight: .bold, design: .monospaced))
                    .foregroundStyle(theme.primary)
            }
            Slider(value: value, in: range)
                .tint(theme.primary)
        }
    }
}
