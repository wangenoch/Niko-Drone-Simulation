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
                    Text("\(L10n.tr("set_usbc_status_title")) \(state.controllerConnected ? state.activeHidName : L10n.tr("set_usbc_unconnected"))")
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundStyle(state.controllerConnected ? theme.success : theme.warning)
                    Text("\(L10n.tr("set_hw_port_check")) \(typeCCap.deviceDescription) | PPS: \(state.packetsPerSecond) Hz")
                        .font(.system(size: 10.5, design: .monospaced))
                        .foregroundStyle(theme.textSecondary)
                }
                Spacer()
                Button(L10n.tr("set_rescan_usbc")) {
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
            Text(L10n.tr("set_stick_mode"))
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(theme.textPrimary)

            HStack(spacing: 8) {
                ForEach([1, 2, 3, 4], id: \.self) { m in
                    let label = (m == 1) ? L10n.tr("set_mode_1") : (m == 2 ? L10n.tr("set_mode_2") : "Mode \(m)")
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

            Toggle(L10n.tr("set_half_throttle"), isOn: $state.halfThrottle)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            // 通道反轉與中立點校準
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(L10n.tr("set_axis_invert_cal"))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(theme.primary)
                    Spacer()
                    Button(L10n.tr("set_cal_center_btn")) {
                        state.mappingLY.center = stickState.visualBuffer[1]
                        state.mappingLX.center = stickState.visualBuffer[0]
                        state.mappingRY.center = stickState.visualBuffer[2]
                        state.mappingRX.center = stickState.visualBuffer[3]
                        state.mappingLY.min = -1.0; state.mappingLY.max = 1.0
                        state.mappingLX.min = -1.0; state.mappingLX.max = 1.0
                        state.mappingRY.min = -1.0; state.mappingRY.max = 1.0
                        state.mappingRX.min = -1.0; state.mappingRX.max = 1.0
                    }
                    .font(.system(size: 10.5, weight: .bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(RoundedRectangle(cornerRadius: 6).fill(theme.warning.opacity(0.22)))
                    .foregroundStyle(theme.warning)
                }

                HStack(spacing: 8) {
                    axisInvertToggle("LY (左縱)", isInverted: $state.mappingLY.inverted, theme: theme)
                    axisInvertToggle("LX (左橫)", isInverted: $state.mappingLX.inverted, theme: theme)
                    axisInvertToggle("RY (右縱)", isInverted: $state.mappingRY.inverted, theme: theme)
                    axisInvertToggle("RX (右橫)", isInverted: $state.mappingRX.inverted, theme: theme)
                }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.black.opacity(0.25)))

            // 手感滑桿
            Toggle(L10n.tr("set_use_global_rates"), isOn: $state.useGlobalRates)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            if state.useGlobalRates {
                sliderRow(L10n.tr("set_global_rate"), value: $state.globalRate, range: 0.4...2.0, format: "%.2f")
                sliderRow(L10n.tr("set_global_expo"), value: $state.globalExpo, range: 0.0...0.8, format: "%.2f")
            } else {
                sliderRow("Throttle Up Rate", value: $state.rateT_Up, range: 0.3...2.0, format: "%.2f")
                sliderRow("Throttle Down Rate", value: $state.rateT_Down, range: 0.3...2.0, format: "%.2f")
                sliderRow("Yaw Rate", value: $state.rateY, range: 0.3...2.0, format: "%.2f")
                sliderRow("Pitch Rate", value: $state.rateP, range: 0.3...2.0, format: "%.2f")
                sliderRow("Roll Rate", value: $state.rateR, range: 0.3...2.0, format: "%.2f")
                sliderRow("Yaw Expo", value: $state.expoY, range: 0.0...0.8, format: "%.2f")
                sliderRow("Pitch Expo", value: $state.expoP, range: 0.0...0.8, format: "%.2f")
                sliderRow("Roll Expo", value: $state.expoR, range: 0.0...0.8, format: "%.2f")
            }

            sliderRow(L10n.tr("set_deadzone"), value: $state.joystickDeadzone, range: 0.0...0.2, format: "%.2f")

            Toggle(isOn: $state.useIdleWakeupPatch) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.tr("set_idle_wakeup_title"))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(theme.textPrimary)
                    Text(L10n.tr("set_idle_wakeup_desc"))
                        .font(.system(size: 10))
                        .foregroundStyle(theme.textSecondary)
                }
            }
            .tint(theme.primary)
        }
    }

    private func axisInvertToggle(_ title: String, isInverted: Binding<Bool>, theme: TacticalColors) -> some View {
        Button {
            isInverted.wrappedValue.toggle()
        } label: {
            HStack(spacing: 4) {
                Image(systemName: isInverted.wrappedValue ? "arrow.up.arrow.down.circle.fill" : "arrow.up.arrow.down.circle")
                Text("\(title) \(isInverted.wrappedValue ? "REV" : "NOR")")
            }
            .font(.system(size: 10.5, weight: .bold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 6).fill(isInverted.wrappedValue ? theme.warning : Color.black.opacity(0.35)))
            .foregroundStyle(isInverted.wrappedValue ? .black : theme.textPrimary)
        }
    }

    // MARK: - 2. 氣候與風場分頁
    @ViewBuilder
    private func environmentTabContent(_ theme: TacticalColors) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(L10n.tr("set_wind_level")) L\(state.windLevel)")
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(theme.textPrimary)
                Spacer()
                ForEach(0...5, id: \.self) { lvl in
                    Button("L\(lvl)") {
                        state.windLevel = lvl
                        if lvl == 0 {
                            state.windDirection = AppConfig.WIND_DIR_NONE
                        } else if state.windDirection == AppConfig.WIND_DIR_NONE {
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

            // 陣風強度變化與風向擺動幅度
            HStack {
                Text("\(L10n.tr("set_wind_variation")): \(state.windVariation)")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(theme.textPrimary)
                Spacer()
                ForEach(0...3, id: \.self) { v in
                    Button("V\(v)") { state.windVariation = v }
                        .font(.system(size: 10.5, weight: .bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 6).fill(state.windVariation == v ? theme.primary : Color.black.opacity(0.3)))
                        .foregroundStyle(state.windVariation == v ? .black : theme.textPrimary)
                }
            }

            HStack {
                Text("\(L10n.tr("set_wind_dir_variation")): \(state.windDirVariation)")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(theme.textPrimary)
                Spacer()
                ForEach(0...3, id: \.self) { dv in
                    Button("D\(dv)") { state.windDirVariation = dv }
                        .font(.system(size: 10.5, weight: .bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 6).fill(state.windDirVariation == dv ? theme.primary : Color.black.opacity(0.3)))
                        .foregroundStyle(state.windDirVariation == dv ? .black : theme.textPrimary)
                }
            }

            Text(L10n.tr("set_wind_source"))
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
                        if dir == AppConfig.WIND_DIR_NONE {
                            state.windLevel = 0
                        } else if state.windLevel == 0 {
                            state.windLevel = 1
                        }
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

            // 時段與天氣模式
            HStack(spacing: 8) {
                Text(L10n.tr("set_time_of_day"))
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(theme.textPrimary)
                Spacer()
                ForEach([
                    (AppConfig.TIME_MORNING, L10n.tr("set_time_morning")),
                    (AppConfig.TIME_NOON, L10n.tr("set_time_noon")),
                    (AppConfig.TIME_AFTERNOON, L10n.tr("set_time_afternoon"))
                ], id: \.0) { item in
                    Button(item.1) { state.timeOfDay = item.0 }
                        .font(.system(size: 10.5, weight: .bold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(RoundedRectangle(cornerRadius: 6).fill(state.timeOfDay == item.0 ? theme.primary : Color.black.opacity(0.3)))
                        .foregroundStyle(state.timeOfDay == item.0 ? .black : theme.textPrimary)
                }
            }

            HStack(spacing: 8) {
                Text(L10n.tr("set_weather_mode"))
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(theme.textPrimary)
                Spacer()
                ForEach([
                    (0, L10n.tr("set_weather_sunny")),
                    (1, L10n.tr("set_weather_cloudy")),
                    (2, L10n.tr("set_weather_overcast"))
                ], id: \.0) { w in
                    Button(w.1) { state.weatherMode = w.0 }
                        .font(.system(size: 10.5, weight: .bold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(RoundedRectangle(cornerRadius: 6).fill(state.weatherMode == w.0 ? theme.primary : Color.black.opacity(0.3)))
                        .foregroundStyle(state.weatherMode == w.0 ? .black : theme.textPrimary)
                }
            }

            Toggle(L10n.tr("set_hardcore_wind"), isOn: $state.useHardcorePhysics)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(L10n.tr("set_vertical_draft"), isOn: $state.enableVerticalDraft)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            HStack(spacing: 16) {
                Toggle(L10n.tr("set_clouds"), isOn: $state.showClouds)
                Toggle(L10n.tr("set_mountains"), isOn: $state.showMountains)
                Toggle(L10n.tr("set_shadow"), isOn: $state.showShadow)
            }
            .font(.system(size: 11.5, weight: .semibold))
            .foregroundStyle(theme.textPrimary)
            .tint(theme.primary)

            sliderRow(L10n.tr("set_sun_pos"), value: $state.sunPosition, range: 0.1...0.9, format: "%.2f")
            sliderRow(L10n.tr("set_cloud_density"), value: $state.cloudDensity, range: 0.1...1.0, format: "%.2f")
            sliderRow(L10n.tr("set_shadow_intensity"), value: $state.shadowIntensity, range: 0.1...1.0, format: "%.2f")
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
                            Text(L10n.tr(mod.hardwareSpecs.description))
                                .font(.system(size: 11))
                                .foregroundStyle(theme.textSecondary)
                            Text("Wheelbase: \(String(mod.hardwareSpecs.wheelbaseMm))mm | Weight: \(String(format: "%.1f", mod.hardwareSpecs.takeoffWeightKg))kg | Power: \(String(format: "%.0f", spec.physicsPower)) | Flight: \(String(spec.flightTimeMin))min")
                                .font(.system(size: 10, design: .monospaced))
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
                        state.applyCameraModeDefaults(item.0)
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

            sliderRow(L10n.tr("set_zoom_factor"), value: $state.zoomFactor, range: 0.5...3.0, format: "%.1fX")
            sliderRow(L10n.tr("set_main_fov"), value: $state.mainFOV, range: 25.0...90.0, format: "%.0f°")
            sliderRow(L10n.tr("set_obs_height"), value: $state.observerHeight, range: 1.2...12.0, format: "%.1fm")
            sliderRow(L10n.tr("set_obs_tilt"), value: $state.observerTilt, range: -25.0...45.0, format: "%.0f°")
            sliderRow(L10n.tr("set_fpv_tilt"), value: $state.cameraTilt, range: -15.0...45.0, format: "%.0f°")

            Toggle(L10n.tr("set_ground_anchor"), isOn: $state.showGroundAnchor)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(L10n.tr("set_zoom_assist"), isOn: $state.enableZoomAssistant)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(L10n.tr("set_show_flight_path"), isOn: $state.showFlightPath)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(L10n.tr("set_show_obstacles"), isOn: $state.showObstacles)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(
                L10n.tr("set_show_attitude_ball"),
                isOn: Binding(
                    get: { state.hudMode == 2 },
                    set: { state.hudMode = $0 ? 2 : 1 }
                )
            )
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
            Text(L10n.tr("set_duo_header"))
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

            Toggle(L10n.tr("set_tether_mode"), isOn: $state.isTetherModeEnabled)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(L10n.tr("set_apply_phys_specs"), isOn: $state.applyPhysicalSpecs)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(L10n.tr("set_use_flight_limit"), isOn: $state.useFlightLimit)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(L10n.tr("set_ground_effect"), isOn: $state.enableGroundEffect)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.warning)

            Toggle(L10n.tr("set_non_center_force"), isOn: $state.enableNonCenterForce)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.warning)

            Toggle(L10n.tr("set_strict_landing"), isOn: $state.useStrictLanding)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            Toggle(L10n.tr("set_mute_audio"), isOn: $state.isMuted)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.textPrimary)
                .tint(theme.primary)

            HStack {
                Text(L10n.tr("set_language"))
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

            Divider().background(theme.border)

            Button {
                state.restoreFactoryDefaults()
                onResetSimulation()
            } label: {
                HStack {
                    Image(systemName: "arrow.counterclockwise.circle.fill")
                    Text(L10n.tr("set_restore_defaults"))
                        .fontWeight(.bold)
                }
                .font(.system(size: 12))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(RoundedRectangle(cornerRadius: 8).fill(theme.danger.opacity(0.75)))
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
                    Text(L10n.tr("set_theme_dark"))
                        .font(.system(size: 12, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 10).fill(state.appTheme == AppConfig.THEME_CLASSIC ? theme.primary : Color.black.opacity(0.3)))
                        .foregroundStyle(state.appTheme == AppConfig.THEME_CLASSIC ? .black : theme.textPrimary)
                }

                Button {
                    state.appTheme = AppConfig.THEME_LIGHT
                } label: {
                    Text(L10n.tr("set_theme_light"))
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
                Text(L10n.tr("set_privacy_note"))
                    .font(.system(size: 10.5))
                    .foregroundStyle(theme.textSecondary)
                    .padding(.top, 4)
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
