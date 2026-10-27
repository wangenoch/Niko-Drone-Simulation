import SwiftUI

/// [v1.8.01] 主飛行互動畫面與 iPhone Duo 折疊自適應座艙容器
struct MainAppScreen: View {
    @State private var state = DroneState.shared
    @State private var stickState = StickInputState.shared

    /// 根據螢幕長寬比與使用者設定，決定當前啟用的 iPhone Duo 折疊形態
    private func effectivePosture(for size: CGSize) -> FoldablePostureMode {
        if state.foldablePosture != .auto {
            return state.foldablePosture
        }
        let aspect = size.width / max(size.height, 1.0)
        // iPhone Duo 展開時接近正方形或直立雙屏 (aspect 介於 0.65 ~ 1.45 且短邊 >= 580)
        if aspect < 1.15 {
            return .duoVerticalDeck
        } else if min(size.width, size.height) >= 650 && aspect < 1.55 {
            return .duoBookSplit
        }
        return .standard
    }

    var body: some View {
        GeometryReader { geo in
            let posture = effectivePosture(for: geo.size)
            let theme = TacticalColors.current(state)

            ZStack {
                switch posture {
                case .standard, .auto:
                    standardSingleScreenLayout(size: geo.size, theme: theme)
                case .duoVerticalDeck:
                    duoVerticalDeckLayout(size: geo.size, theme: theme)
                case .duoBookSplit:
                    duoBookSplitLayout(size: geo.size, theme: theme)
                case .duoTentFlex:
                    duoTentFlexLayout(size: geo.size, theme: theme)
                }

                // 碰撞損毀提示層
                CollisionOverlayView(state: state) {
                    SimulationSceneCoordinator.shared.resetSimulation()
                }

                // 整合設定選單
                if state.showSettings {
                    UnifiedSettingsSheet(state: state, stickState: stickState) {
                        SimulationSceneCoordinator.shared.resetSimulation()
                    }
                }
            }
            .background(Color.black)
            .ignoresSafeArea()
        }
        .statusBarHidden(state.hideStatusBar)
    }

    // MARK: - 形態 1：標準橫屏全螢幕佈局 (1:1 對齊 Android DroneHUD + FlightInteractionLayer)
    private func standardSingleScreenLayout(size: CGSize, theme: TacticalColors) -> some View {
        let distToOpsCenter = sqrt(state.posX * state.posX + (state.posZ - 6.0) * (state.posZ - 6.0))
        let isInZoomZone = state.enableZoomAssistant &&
            !state.isTetherModeEnabled &&
            distToOpsCenter > AppConfig.VisualDefaults.ZOOM_ASSISTANT_DISTANCE &&
            state.cameraMode != AppConfig.CAM_MODE_FPV &&
            state.cameraMode != AppConfig.CAM_MODE_FOLLOW &&
            state.cameraMode != AppConfig.CAM_MODE_STATION_SMART &&
            !state.showSettings

        return ZStack {
            // 1. 底層 120Hz 3D 渲染視場
            SimulationSceneView(role: .main)
                .ignoresSafeArea()

            // 2. 定點計時浮動標籤 (跟隨飛機 2D 投影位置)
            if let dPos = state.droneScreenPos {
                SpotTimerFloatingOverlay(state: state)
                    .position(
                        x: min(max(dPos.x, 120), size.width - 120),
                        y: min(max(dPos.y - 75, 45), size.height - 90)
                    )
            } else {
                VStack {
                    SpotTimerFloatingOverlay(state: state)
                        .padding(.top, 58)
                    Spacer()
                }
            }

            // 3. 左上角多功能儀表層 (Radar 150x100 + FPV PiP 150x100 / Attitude 120x120)
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    RadarHUDView(state: state)

                    if state.cameraMode != AppConfig.CAM_MODE_FPV {
                        // FPV 子母畫面 (固定 150x100 pt)
                        ZStack(alignment: .topLeading) {
                            SimulationSceneView(role: .fpvPip)
                                .frame(width: 150, height: 100)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.orange.opacity(0.85), lineWidth: 1.5))
                            Text("FPV CAM")
                                .font(.system(size: 8.5, weight: .bold, design: .monospaced))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.black.opacity(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 4))
                                .padding(5)
                        }
                    }

                    if state.hudMode == 2 {
                        AttitudeHorizonView(state: state)
                    }
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 14)
            .padding(.leading, 18)

            // 4. 頂部中央：遠距離 Zoom Assistant PiP、ARM/DISARM 與警告橫幅
            VStack(spacing: 8) {
                if isInZoomZone {
                    ZStack(alignment: .bottomLeading) {
                        SimulationSceneView(role: .zoomPip)
                            .frame(width: 118, height: 118)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(theme.primary, lineWidth: 1.5))
                        Text("ZOOM ASSIST")
                            .font(.system(size: 8, weight: .bold, design: .monospaced))
                            .foregroundStyle(theme.primary)
                            .padding(4)
                    }
                }

                // 地面安全解鎖按鈕
                let spec = DroneRegistry.getSpec(state.droneType)
                if state.altitude <= spec.groundOffset + 0.5 {
                    armDisarmButton(theme: theme)
                }

                // 繫留警告 / 邊界警告橫幅
                warningBannerView(theme: theme)
                Spacer()
            }
            .padding(.top, 14)

            // 5. 右上角工具列 + iPhone Duo 折疊形態快速切換器
            VStack {
                HStack {
                    Spacer()
                    topRightActionMenu(theme: theme)
                }
                Spacer()
            }
            .padding(.top, 14)
            .padding(.trailing, 18)

            // 6. 虛擬搖桿 (左右對稱)
            if state.showVirtualJoysticks {
                VStack {
                    Spacer()
                    HStack {
                        VirtualJoystickView(
                            stickX: stickState.stickLX(state),
                            stickY: stickState.stickLY(state),
                            size: 138,
                            onDragStateChange: { isTouching in
                                stickState.isTouchingLeft = isTouching
                                state.lastInteractionTime = Date().timeIntervalSince1970
                            },
                            onValueChange: { x, y in
                                stickState.touchLX = x
                                stickState.touchLY = y
                                state.lastInteractionTime = Date().timeIntervalSince1970
                            }
                        )
                        .padding(.leading, 46)
                        .padding(.bottom, 28)

                        Spacer()

                        VirtualJoystickView(
                            stickX: stickState.stickRX(state),
                            stickY: stickState.stickRY(state),
                            size: 138,
                            onDragStateChange: { isTouching in
                                stickState.isTouchingRight = isTouching
                                state.lastInteractionTime = Date().timeIntervalSince1970
                            },
                            onValueChange: { x, y in
                                stickState.touchRX = x
                                stickState.touchRY = y
                                state.lastInteractionTime = Date().timeIntervalSince1970
                            }
                        )
                        .padding(.trailing, 46)
                        .padding(.bottom, 28)
                    }
                }
            }

            // 7. 底部中央高頻遙測數據列
            VStack {
                Spacer()
                StatusTelemetryBar(state: state)
                    .padding(.bottom, 10)
            }
        }
    }

    // MARK: - 形態 2：iPhone Duo 上下雙屏・戰術地面控制站模式 (上屏 100% 無遮擋 3D 視場 + 下屏 GCS 操控台)
    private func duoVerticalDeckLayout(size: CGSize, theme: TacticalColors) -> some View {
        let topHeight = size.height * state.hingeCreaseRatio

        return VStack(spacing: 0) {
            // 【上螢幕】100% 無手指遮擋之純淨 3D 飛行視場
            ZStack {
                SimulationSceneView(role: .main)

                VStack {
                    HStack {
                        duoModeBadge(label: "iPhone Duo • 上下雙屏戰術地面站", theme: theme)
                        Spacer()
                        warningBannerView(theme: theme)
                        Spacer()
                        topRightActionMenu(theme: theme)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                    Spacer()

                    HStack {
                        SpotTimerFloatingOverlay(state: state)
                        Spacer()
                        StatusTelemetryBar(state: state)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                }
            }
            .frame(height: topHeight)
            .clipped()

            // 【iPhone Duo 轉軸折痕安全帶 (Hinge Crease Seam Bar)】
            duoHingeSeamToolbar(theme: theme)

            // 【下螢幕】戰術地面控制站 (GCS Tactical Deck：左搖桿 + 中央三聯 MFD 儀表艙 + 右搖桿)
            HStack(spacing: 14) {
                // 左搖桿區
                VStack(spacing: 6) {
                    Text(state.joystickMode == 2 ? "THROTTLE / YAW" : "PITCH / YAW")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundStyle(theme.textSecondary)
                    VirtualJoystickView(
                        stickX: stickState.stickLX(state),
                        stickY: stickState.stickLY(state),
                        size: min(size.height * 0.34, 165),
                        onDragStateChange: { stickState.isTouchingLeft = $0 },
                        onValueChange: { x, y in stickState.touchLX = x; stickState.touchLY = y }
                    )
                }
                .padding(.leading, 18)

                Spacer()

                // 中央戰術 MFD 儀表與實體按鍵區
                VStack(spacing: 10) {
                    HStack(spacing: 10) {
                        RadarHUDView(state: state)
                        AttitudeHorizonView(state: state)
                        ZStack(alignment: .topLeading) {
                            SimulationSceneView(role: .fpvPip)
                                .frame(width: 150, height: 100)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.orange.opacity(0.8), lineWidth: 1.5))
                            Text("FPV LIVE")
                                .font(.system(size: 8.5, weight: .bold, design: .monospaced))
                                .foregroundStyle(.white)
                                .padding(5)
                        }
                    }

                    // 戰術實體撥桿列
                    HStack(spacing: 10) {
                        armDisarmButton(theme: theme)
                        tacticalDeckButton(
                            title: state.isTetherModeEnabled ? "⚓ 繫留 ON" : "⚓ 繫留 OFF",
                            isActive: state.isTetherModeEnabled,
                            activeColor: theme.primary
                        ) {
                            state.isTetherModeEnabled.toggle()
                        }
                        tacticalDeckButton(
                            title: state.isSpotTimerEnabled ? "⏱ 定點計時 ON" : "⏱ 定點計時 OFF",
                            isActive: state.isSpotTimerEnabled,
                            activeColor: theme.warning
                        ) {
                            state.isSpotTimerEnabled.toggle()
                        }
                        tacticalDeckButton(
                            title: "↺ 重置 H 坪",
                            isActive: false,
                            activeColor: theme.danger
                        ) {
                            SimulationSceneCoordinator.shared.resetSimulation()
                        }
                    }
                }

                Spacer()

                // 右搖桿區
                VStack(spacing: 6) {
                    Text(state.joystickMode == 2 ? "PITCH / ROLL" : "THROTTLE / ROLL")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundStyle(theme.textSecondary)
                    VirtualJoystickView(
                        stickX: stickState.stickRX(state),
                        stickY: stickState.stickRY(state),
                        size: min(size.height * 0.34, 165),
                        onDragStateChange: { stickState.isTouchingRight = $0 },
                        onValueChange: { x, y in stickState.touchRX = x; stickState.touchRY = y }
                    )
                }
                .padding(.trailing, 18)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(
                LinearGradient(
                    colors: [Color(red: 0.08, green: 0.09, blue: 0.11), Color(red: 0.04, green: 0.04, blue: 0.05)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        }
    }

    // MARK: - 形態 3：iPhone Duo 左右書本展開模式 (左屏主視場 + 右屏戰術航電艙)
    private func duoBookSplitLayout(size: CGSize, theme: TacticalColors) -> some View {
        let leftWidth = size.width * 0.56

        return HStack(spacing: 0) {
            // 【左螢幕】主飛行視場
            ZStack {
                SimulationSceneView(role: .main)

                VStack {
                    HStack {
                        duoModeBadge(label: "iPhone Duo • 書本展開雙聯屏", theme: theme)
                        Spacer()
                        armDisarmButton(theme: theme)
                    }
                    .padding(14)

                    warningBannerView(theme: theme)
                    SpotTimerFloatingOverlay(state: state)
                    Spacer()
                    StatusTelemetryBar(state: state)
                        .padding(.bottom, 12)
                }
            }
            .frame(width: leftWidth)
            .clipped()

            // 垂直轉軸分隔線
            Rectangle()
                .fill(LinearGradient(colors: [.black, theme.primary.opacity(0.45), .black], startPoint: .top, endPoint: .bottom))
                .frame(width: 6)

            // 【右螢幕】獨立戰術航電與雙搖桿操控艙
            VStack(spacing: 10) {
                HStack {
                    Text("DUO TACTICAL AVIONICS BAY")
                        .font(.system(size: 11, weight: .heavy, design: .monospaced))
                        .foregroundStyle(theme.primary)
                    Spacer()
                    topRightActionMenu(theme: theme)
                }
                .padding(.horizontal, 12)
                .padding(.top, 12)

                // 雙子母畫面並列 (FPV + Precision Zoom)
                HStack(spacing: 10) {
                    ZStack(alignment: .topLeading) {
                        SimulationSceneView(role: .fpvPip)
                            .frame(height: 105)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.orange.opacity(0.8), lineWidth: 1.2))
                        Text("FPV COCKPIT")
                            .font(.system(size: 8.5, weight: .bold, design: .monospaced))
                            .foregroundStyle(.white)
                            .padding(5)
                    }

                    ZStack(alignment: .topLeading) {
                        SimulationSceneView(role: .zoomPip)
                            .frame(height: 105)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(theme.primary.opacity(0.8), lineWidth: 1.2))
                        Text("OPTICAL TRACKER")
                            .font(.system(size: 8.5, weight: .bold, design: .monospaced))
                            .foregroundStyle(theme.primary)
                            .padding(5)
                    }
                }
                .padding(.horizontal, 12)

                // 雷達與姿態儀並列
                HStack(spacing: 12) {
                    RadarHUDView(state: state)
                    AttitudeHorizonView(state: state)
                }

                Spacer()

                // 底部雙搖桿
                HStack {
                    VirtualJoystickView(
                        stickX: stickState.stickLX(state),
                        stickY: stickState.stickLY(state),
                        size: 124,
                        onDragStateChange: { stickState.isTouchingLeft = $0 },
                        onValueChange: { x, y in stickState.touchLX = x; stickState.touchLY = y }
                    )
                    Spacer()
                    VirtualJoystickView(
                        stickX: stickState.stickRX(state),
                        stickY: stickState.stickRY(state),
                        size: 124,
                        onDragStateChange: { stickState.isTouchingRight = $0 },
                        onValueChange: { x, y in stickState.touchRX = x; stickState.touchRY = y }
                    )
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 16)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(red: 0.06, green: 0.07, blue: 0.09))
        }
    }

    // MARK: - 形態 4：iPhone Duo 半折立架模式 (90°~135° 桌面立架 + 外接 Type-C 實體遙控器監控台)
    private func duoTentFlexLayout(size: CGSize, theme: TacticalColors) -> some View {
        let topHeight = size.height * 0.56

        return VStack(spacing: 0) {
            // 【上螢幕 (立起面向飛手)】全景 3D + FPV PiP + 雷達
            ZStack {
                SimulationSceneView(role: .main)

                VStack {
                    HStack(alignment: .top) {
                        RadarHUDView(state: state)
                        Spacer()
                        VStack(spacing: 6) {
                            duoModeBadge(label: "iPhone Duo • 90°~135° 立架模式 (Type-C 遙控器專用)", theme: theme)
                            warningBannerView(theme: theme)
                            SpotTimerFloatingOverlay(state: state)
                        }
                        Spacer()
                        SimulationSceneView(role: .fpvPip)
                            .frame(width: 150, height: 100)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.orange, lineWidth: 1.5))
                    }
                    .padding(14)
                    Spacer()
                }
            }
            .frame(height: topHeight)
            .clipped()

            duoHingeSeamToolbar(theme: theme)

            // 【下螢幕 (平放於桌面)】Type-C 遙控器 4 軸即時行程監控 + 大尺寸戰術巨集按鍵盤
            HStack(spacing: 18) {
                // 4 軸即時通道監視器
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "cable.connector.horizontal")
                            .foregroundStyle(theme.primary)
                        Text("USB-C HID TELEMETRY (\(state.activeHidName))")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundStyle(theme.primary)
                    }

                    channelBarRow(label: "THR (油門)", value: stickState.stickThrottle(state), color: theme.primary)
                    channelBarRow(label: "YAW (航向)", value: stickState.stickYaw(state), color: theme.warning)
                    channelBarRow(label: "PIT (俯仰)", value: stickState.stickPitch(state), color: theme.success)
                    channelBarRow(label: "ROL (橫滾)", value: stickState.stickRoll(state), color: .cyan)
                }
                .padding(12)
                .frame(width: 280)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.black.opacity(0.4)))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(theme.border, lineWidth: 1))

                AttitudeHorizonView(state: state)

                // 桌面大尺寸戰術巨集鍵盤
                VStack(spacing: 10) {
                    StatusTelemetryBar(state: state)
                    HStack(spacing: 10) {
                        armDisarmButton(theme: theme)
                        tacticalDeckButton(
                            title: state.isTetherModeEnabled ? "⚓ 繫留練習 ON" : "⚓ 繫留練習 OFF",
                            isActive: state.isTetherModeEnabled,
                            activeColor: theme.primary
                        ) {
                            state.isTetherModeEnabled.toggle()
                        }
                        tacticalDeckButton(
                            title: state.isSpotTimerEnabled ? "⏱ 定點考照 ON" : "⏱ 定點考照 OFF",
                            isActive: state.isSpotTimerEnabled,
                            activeColor: theme.warning
                        ) {
                            state.isSpotTimerEnabled.toggle()
                        }
                        tacticalDeckButton(
                            title: "⚙ 系統設定",
                            isActive: state.showSettings,
                            activeColor: theme.primary
                        ) {
                            state.showSettings = true
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(red: 0.05, green: 0.06, blue: 0.08))
        }
    }

    // MARK: - 共用戰術互動元件
    private func duoHingeSeamToolbar(theme: TacticalColors) -> some View {
        HStack(spacing: 14) {
            HStack(spacing: 5) {
                Image(systemName: "rectangle.split.2x1")
                    .font(.system(size: 10))
                Text("DUO HINGE SEAM")
                    .font(.system(size: 9, weight: .heavy, design: .monospaced))
            }
            .foregroundStyle(theme.textSecondary)

            Spacer()

            // 快速切換視角
            ForEach([
                (AppConfig.CAM_MODE_STATION_TRACK, "追蹤"),
                (AppConfig.CAM_MODE_STATION_SMART, "智慧"),
                (AppConfig.CAM_MODE_FPV, "FPV"),
                (AppConfig.CAM_MODE_OBS, "考官")
            ], id: \.0) { mode in
                Button {
                    state.cameraMode = mode.0
                } label: {
                    Text(mode.1)
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(state.cameraMode == mode.0 ? theme.primary : Color.white.opacity(0.08)))
                        .foregroundStyle(state.cameraMode == mode.0 ? .black : theme.textPrimary)
                }
            }

            Spacer()

            // 快速切換 Duo 折疊形態
            Menu {
                ForEach(FoldablePostureMode.allCases) { mode in
                    Button(mode.rawValue) {
                        state.foldablePosture = mode
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "iphone.gen3")
                    Text("形態: \(state.foldablePosture.rawValue)")
                }
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundStyle(theme.primary)
            }
        }
        .padding(.horizontal, 18)
        .frame(height: 26)
        .background(Color.black.opacity(0.92))
        .overlay(Rectangle().stroke(theme.border, lineWidth: 0.5))
    }

    private func duoModeBadge(label: String, theme: TacticalColors) -> some View {
        HStack(spacing: 6) {
            Image(systemName: "rectangle.split.2x1.fill")
                .font(.system(size: 11))
                .foregroundStyle(theme.primary)
            Text(label)
                .font(.system(size: 10.5, weight: .bold))
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(theme.panel))
        .overlay(Capsule().stroke(theme.primary.opacity(0.5), lineWidth: 1))
    }

    private func armDisarmButton(theme: TacticalColors) -> some View {
        Button {
            state.isMotorLocked.toggle()
            if !state.isMotorLocked {
                TypeCHidControllerManager.shared.notifyManualUnlock()
            }
        } label: {
            Text(state.isMotorLocked ? L10n.tr("action_arm") : L10n.tr("action_disarm"))
                .font(.system(size: 13, weight: .heavy))
                .foregroundStyle(.white)
                .frame(width: 98, height: 38)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(state.isMotorLocked ? Color.green.opacity(0.75) : Color.red.opacity(0.78))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.white.opacity(0.55), lineWidth: 1)
                )
        }
    }

    private func tacticalDeckButton(title: String, isActive: Bool, activeColor: Color, action: @escaping () -> Void) -> some View {
        let theme = TacticalColors.current(state)
        return Button(action: action) {
            Text(title)
                .font(.system(size: 11.5, weight: .bold))
                .foregroundStyle(isActive ? .black : theme.textPrimary)
                .padding(.horizontal, 12)
                .frame(height: 38)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(isActive ? activeColor : Color.white.opacity(0.08))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isActive ? activeColor : theme.border, lineWidth: 1)
                )
        }
    }

    @ViewBuilder
    private func warningBannerView(theme: TacticalColors) -> some View {
        let tetherMsg: String? = {
            switch state.systemMessage {
            case "TETHER_ALT_LIMIT": return L10n.tr("sys_msg_tether_alt")
            case "TETHER_DIST_LIMIT": return L10n.tr("sys_msg_tether_dist")
            case "TETHER_GROUND_LIMIT": return L10n.tr("sys_msg_tether_ground")
            default: return nil
            }
        }()

        if let msg = tetherMsg {
            Text(msg)
                .font(.system(size: 13, weight: .heavy))
                .foregroundStyle(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 7)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color.red.opacity(0.85)))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white, lineWidth: 1.5))
        } else if state.isNearBoundary {
            Text(L10n.tr("hud_boundary_warning"))
                .font(.system(size: 13, weight: .heavy))
                .foregroundStyle(theme.warning)
                .padding(.horizontal, 16)
                .padding(.vertical, 7)
                .background(RoundedRectangle(cornerRadius: 8).fill(theme.panel))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(theme.warning, lineWidth: 1.5))
        }
    }

    private func topRightActionMenu(theme: TacticalColors) -> some View {
        HStack(spacing: 10) {
            // iPhone Duo 形態一鍵循環切換按鈕 (隨時可在單屏/雙屏/書本/立架間切換)
            Button {
                let all = FoldablePostureMode.allCases
                if let idx = all.firstIndex(of: state.foldablePosture) {
                    state.foldablePosture = all[(idx + 1) % all.count]
                }
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "rectangle.split.2x1.fill")
                    Text(state.foldablePosture.rawValue)
                }
                .font(.system(size: 10.5, weight: .bold, design: .monospaced))
                .foregroundStyle(theme.primary)
                .padding(.horizontal, 10)
                .frame(height: 40)
                .background(Capsule().fill(theme.panel))
                .overlay(Capsule().stroke(theme.primary.opacity(0.6), lineWidth: 1))
            }

            if state.isMenuExpanded {
                HStack(spacing: 10) {
                    circleIconButton("arrow.counterclockwise", tint: theme.warning, isSelected: false) {
                        SimulationSceneCoordinator.shared.resetSimulation()
                        state.isMenuExpanded = false
                    }
                    circleIconButton("gamecontroller.fill", tint: theme.textPrimary, isSelected: state.showVirtualJoysticks) {
                        state.showVirtualJoysticks.toggle()
                    }
                    circleIconButton("timer", tint: theme.textPrimary, isSelected: state.isSpotTimerEnabled) {
                        state.isSpotTimerEnabled.toggle()
                        if state.isSpotTimerEnabled {
                            state.spotTimerMessage = "IDLE"
                            state.spotTimerSeconds = 5.0
                        }
                    }
                    circleIconButton("link", tint: theme.textPrimary, isSelected: state.isTetherModeEnabled) {
                        state.isTetherModeEnabled.toggle()
                    }
                    circleIconButton("gearshape.fill", tint: theme.textPrimary, isSelected: state.showSettings) {
                        state.showSettings = true
                    }
                }
            }

            circleIconButton(
                state.isMenuExpanded ? "xmark" : "line.3.horizontal",
                tint: theme.textPrimary,
                isSelected: state.isMenuExpanded
            ) {
                withAnimation(.easeInOut(duration: 0.2)) {
                    state.isMenuExpanded.toggle()
                }
            }
        }
    }

    private func circleIconButton(_ systemName: String, tint: Color, isSelected: Bool, action: @escaping () -> Void) -> some View {
        let theme = TacticalColors.current(state)
        return Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(isSelected ? theme.primary : tint)
                .frame(width: 40, height: 40)
                .background(Circle().fill(theme.panel))
                .overlay(Circle().stroke(isSelected ? theme.primary : theme.border, lineWidth: 1))
        }
    }

    private func channelBarRow(label: String, value: Float, color: Color) -> some View {
        let normalized = CGFloat(min(max((value + 1.0) / 2.0, 0.0), 1.0))
        return HStack(spacing: 8) {
            Text(label)
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
                .frame(width: 72, alignment: .leading)
            GeometryReader { barGeo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.white.opacity(0.12))
                    RoundedRectangle(cornerRadius: 4)
                        .fill(color)
                        .frame(width: barGeo.size.width * normalized)
                }
            }
            .frame(height: 8)
            Text(String(format: "%+.2f", value))
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundStyle(color)
                .frame(width: 42, alignment: .trailing)
        }
    }
}
