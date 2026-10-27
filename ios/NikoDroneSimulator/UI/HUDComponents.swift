import SwiftUI

// MARK: - 戰術主題色彩 (.cursorrules 規範：0xEE111111 深色半透明 + 0.1 白框)
struct TacticalColors {
    let isLight: Bool

    var panel: Color {
        isLight ? Color(white: 0.95, opacity: 0.88) : Color(red: 0.067, green: 0.067, blue: 0.067, opacity: 0.93)
    }
    var border: Color {
        isLight ? Color.black.opacity(0.15) : Color.white.opacity(0.12)
    }
    var textPrimary: Color {
        isLight ? Color.black : Color.white
    }
    var textSecondary: Color {
        isLight ? Color.gray : Color(white: 0.68)
    }
    var primary: Color {
        Color(red: 0.0, green: 0.78, blue: 1.0)
    }
    var warning: Color {
        Color(red: 1.0, green: 0.65, blue: 0.0)
    }
    var danger: Color {
        Color(red: 0.96, green: 0.26, blue: 0.21)
    }
    var success: Color {
        Color(red: 0.30, green: 0.85, blue: 0.39)
    }

    static func current(_ state: DroneState) -> TacticalColors {
        TacticalColors(isLight: state.appTheme == AppConfig.THEME_LIGHT)
    }
}

// MARK: - 1. 雷達視窗 (固定 150x100 pt，1:1 對齊 RadarHUD.kt)
struct RadarHUDView: View {
    var state: DroneState

    var body: some View {
        let theme = TacticalColors.current(state)
        let scaleMultiplier: CGFloat = {
            switch state.radarZoomMode {
            case 0: return 0.9
            case 2: return 3.2
            default: return 1.5
            }
        }()

        ZStack(alignment: .topLeading) {
            Canvas { context, size in
                let s: CGFloat = scaleMultiplier * 1.4
                let centerX = size.width / 2.0
                let centerY = (state.radarZoomMode == 2) ? (size.height / 2.0) : (size.height * 0.75)

                let hRadarX = (state.radarZoomMode == 2) ? (centerX + CGFloat(state.posX) * s) : centerX
                let hRadarY = (state.radarZoomMode == 2) ? (centerY + CGFloat(state.posZ) * s) : centerY

                func toRadar(_ wx: Float, _ wz: Float) -> CGPoint {
                    CGPoint(x: hRadarX - CGFloat(wx) * s, y: hRadarY - CGFloat(wz) * s)
                }

                // 電子圍欄邊界 (-50..50, -9..46)
                let tl = toRadar(-50, 46)
                let br = toRadar(50, -9)
                let boundaryRect = CGRect(x: tl.x, y: tl.y, width: br.x - tl.x, height: br.y - tl.y)
                context.stroke(Path(boundaryRect), with: .color(.red.opacity(0.35)), lineWidth: 1.0)

                // 預警緩衝區
                let wtl = toRadar(-45, 41)
                let wbr = toRadar(45, -4)
                let warnRect = CGRect(x: wtl.x, y: wtl.y, width: wbr.x - wtl.x, height: wbr.y - wtl.y)
                context.stroke(Path(warnRect), with: .color(.yellow.opacity(0.35)), style: StrokeStyle(lineWidth: 0.8, dash: [4, 4]))

                // 16m / 8m 考照方框 (中心 Z=6)
                for sz: Float in [16.0, 8.0] {
                    let o = toRadar(sz / 2.0, 6.0 + sz / 2.0)
                    let r = CGRect(x: o.x, y: o.y, width: CGFloat(sz) * s, height: CGFloat(sz) * s)
                    context.stroke(Path(r), with: .color(.yellow.opacity(0.65)), lineWidth: 1.0)
                }

                // 8 字圓圈 (中心 ±6, Z=6, 半徑 4m / 8m)
                for rM: Float in [4.0, 8.0] {
                    let rad = CGFloat(rM) * s
                    for cx: Float in [-6.0, 6.0] {
                        let c = toRadar(cx, 6.0)
                        let circleRect = CGRect(x: c.x - rad, y: c.y - rad, width: rad * 2, height: rad * 2)
                        context.stroke(Path(ellipseIn: circleRect), with: .color(rM == 8.0 ? .red.opacity(0.45) : .yellow.opacity(0.55)), lineWidth: 0.8)
                    }
                }

                // H 坪 (0, 0)
                let hC = toRadar(0, 0)
                let hRad = max(CGFloat(1.2) * s, 3.0)
                context.fill(Path(ellipseIn: CGRect(x: hC.x - hRad, y: hC.y - hRad, width: hRad * 2, height: hRad * 2)), with: .color(.blue.opacity(0.75)))

                // 飛行軌跡
                if state.showFlightPath && state.flightPath.count > 1 {
                    var trail = Path()
                    for (idx, pt) in state.flightPath.enumerated() {
                        let rp = toRadar(Float(pt.x), Float(pt.y))
                        if idx == 0 { trail.move(to: rp) } else { trail.addLine(to: rp) }
                    }
                    context.stroke(trail, with: .color(theme.primary.opacity(0.6)), style: StrokeStyle(lineWidth: 1.2, dash: [3, 3]))
                }

                // 飛機游標與航向
                let dP = (state.radarZoomMode == 2) ? CGPoint(x: centerX, y: centerY) : toRadar(state.posX, state.posZ)
                context.drawLayer { layerCtx in
                    layerCtx.translateBy(x: dP.x, y: dP.y)
                    layerCtx.rotate(by: .degrees(Double(-state.yaw)))
                    var arrow = Path()
                    arrow.move(to: CGPoint(x: 0, y: -6))
                    arrow.addLine(to: CGPoint(x: -4.5, y: 4.5))
                    arrow.addLine(to: CGPoint(x: 4.5, y: 4.5))
                    arrow.closeSubpath()
                    layerCtx.fill(arrow, with: .color(theme.primary))
                }
            }
            .padding(5)

            // 縮放切換按鈕
            Button {
                state.radarZoomMode = (state.radarZoomMode + 1) % 3
            } label: {
                Text("+")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 20, height: 20)
                    .background(Circle().fill(Color.red.opacity(0.55)))
            }
            .padding(5)

            // 模式標籤
            VStack {
                Spacer()
                HStack {
                    let modeText: String = {
                        switch state.radarZoomMode {
                        case 0: return "[\(L10n.tr("radar_zoom_global"))] 0.6X"
                        case 1: return "[\(L10n.tr("radar_zoom_auto"))] \(L10n.tr("radar_zoom_dynamic"))"
                        default: return "[\(L10n.tr("radar_zoom_precise"))] 4.0X"
                        }
                    }()
                    Text(modeText)
                        .font(.system(size: 8.5, weight: .bold, design: .monospaced))
                        .foregroundStyle(theme.primary)
                        .padding(.leading, 8)
                        .padding(.bottom, 4)
                    Spacer()
                }
            }
        }
        .frame(width: 150, height: 100)
        .background(RoundedRectangle(cornerRadius: 12).fill(theme.panel))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(theme.border, lineWidth: 1))
    }
}

// MARK: - 2. 姿態球儀表 (固定 120x120 pt 正圓，符合 .cursorrules)
struct AttitudeHorizonView: View {
    var state: DroneState

    var body: some View {
        let theme = TacticalColors.current(state)

        ZStack {
            Canvas { context, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let radius = min(size.width, size.height) / 2 - 4

                context.clip(to: Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)))

                context.translateBy(x: center.x, y: center.y)
                context.rotate(by: .degrees(Double(-state.roll)))
                let pitchShift = CGFloat(state.pitch) * 1.4

                // 天空與大地
                let skyRect = CGRect(x: -radius * 2, y: -radius * 2 + pitchShift, width: radius * 4, height: radius * 2)
                let groundRect = CGRect(x: -radius * 2, y: pitchShift, width: radius * 4, height: radius * 2)
                context.fill(Path(skyRect), with: .color(Color(red: 0.12, green: 0.42, blue: 0.72, opacity: 0.75)))
                context.fill(Path(groundRect), with: .color(Color(red: 0.38, green: 0.26, blue: 0.14, opacity: 0.75)))

                // 水平線
                var horizon = Path()
                horizon.move(to: CGPoint(x: -radius, y: pitchShift))
                horizon.addLine(to: CGPoint(x: radius, y: pitchShift))
                context.stroke(horizon, with: .color(.white.opacity(0.85)), lineWidth: 1.5)
            }

            // 中心十字準星
            Path { path in
                path.move(to: CGPoint(x: 38, y: 60))
                path.addLine(to: CGPoint(x: 52, y: 60))
                path.move(to: CGPoint(x: 68, y: 60))
                path.addLine(to: CGPoint(x: 82, y: 60))
            }
            .stroke(theme.warning, lineWidth: 2.0)

            VStack {
                Text(String(format: "YAW %.0f°", (state.yaw.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)))
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundStyle(.white)
                    .padding(.top, 8)
                Spacer()
                Text(String(format: "P:%.0f° R:%.0f°", state.pitch, state.roll))
                    .font(.system(size: 8.5, weight: .semibold, design: .monospaced))
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(.bottom, 8)
            }
        }
        .frame(width: 120, height: 120)
        .background(Circle().fill(theme.panel))
        .overlay(Circle().stroke(theme.border, lineWidth: 1.5))
    }
}

// MARK: - 3. 虛擬搖桿組件 (反映 Expo，打滿必觸邊且絕不出框)
struct VirtualJoystickView: View {
    var stickX: Float
    var stickY: Float
    var size: CGFloat = 136
    var onDragStateChange: (Bool) -> Void
    var onValueChange: (Float, Float) -> Void

    var body: some View {
        let radius = size / 2.0
        let knobRadius: CGFloat = size * 0.19
        let maxTravel = radius - knobRadius - 4.0
        let clampedX = CGFloat(min(max(stickX, -1.0), 1.0))
        let clampedY = CGFloat(min(max(stickY, -1.0), 1.0))

        ZStack {
            Circle()
                .fill(Color.black.opacity(0.42))
                .overlay(Circle().stroke(Color.white.opacity(0.22), lineWidth: 1.5))

            // 十字刻度線
            Path { path in
                path.move(to: CGPoint(x: radius, y: 8))
                path.addLine(to: CGPoint(x: radius, y: size - 8))
                path.move(to: CGPoint(x: 8, y: radius))
                path.addLine(to: CGPoint(x: size - 8, y: radius))
            }
            .stroke(Color.white.opacity(0.12), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))

            // 搖桿頭
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Color(red: 0.0, green: 0.82, blue: 1.0), Color(red: 0.0, green: 0.45, blue: 0.75)],
                        center: .center,
                        startRadius: 2,
                        endRadius: knobRadius
                    )
                )
                .frame(width: knobRadius * 2, height: knobRadius * 2)
                .overlay(Circle().stroke(Color.white.opacity(0.7), lineWidth: 1.2))
                .offset(x: clampedX * maxTravel, y: -clampedY * maxTravel)
        }
        .frame(width: size, height: size)
        .contentShape(Circle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    onDragStateChange(true)
                    let dx = Float((value.location.x - radius) / maxTravel)
                    let dy = Float(-(value.location.y - radius) / maxTravel)
                    onValueChange(min(max(dx, -1.0), 1.0), min(max(dy, -1.0), 1.0))
                }
                .onEnded { _ in
                    onDragStateChange(false)
                    onValueChange(0.0, 0.0)
                }
        )
    }
}

// MARK: - 4. 風向與電量狀態列 (底部高頻遙測數據欄)
struct StatusTelemetryBar: View {
    var state: DroneState

    var body: some View {
        let theme = TacticalColors.current(state)
        let spec = DroneRegistry.getSpec(state.droneType)
        let relAlt = max(state.altitude - spec.groundOffset, 0.0)

        HStack(spacing: 12) {
            TelemetryCell(label: "ALT", value: String(format: "%.1fm", relAlt), color: theme.primary)
            Divider().frame(height: 16).background(theme.border)
            TelemetryCell(label: "DIST", value: String(format: "%.1fm", state.horizontalDist), color: theme.textPrimary)
            Divider().frame(height: 16).background(theme.border)
            TelemetryCell(label: "SPD", value: String(format: "%.1fm/s", state.speed), color: theme.success)
            Divider().frame(height: 16).background(theme.border)
            TelemetryCell(
                label: "WIND",
                value: state.windLevel > 0 ? "L\(state.windLevel) (\(Int(state.currentWindAngle))°)" : "CALM",
                color: state.windLevel > 0 ? theme.warning : theme.textSecondary
            )
            Divider().frame(height: 16).background(theme.border)
            TelemetryCell(
                label: "BAT",
                value: String(format: "%.2fV (%d%%)", state.batteryVoltage, state.batteryPercent),
                color: state.batteryPercent < 25 ? theme.danger : theme.success
            )
            if state.controllerConnected {
                Divider().frame(height: 16).background(theme.border)
                TelemetryCell(label: "USB-C HID", value: "ACTIVE", color: theme.primary)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 10).fill(theme.panel))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(theme.border, lineWidth: 1))
    }
}

struct TelemetryCell: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 1) {
            Text(label)
                .font(.system(size: 8, weight: .semibold, design: .monospaced))
                .foregroundStyle(.gray)
            Text(value)
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundStyle(color)
        }
    }
}

// MARK: - 5. 定點計時浮動標籤 (1:1 對齊 SpotTimerEvaluator.kt)
struct SpotTimerFloatingOverlay: View {
    var state: DroneState

    private func formatMessage() -> String {
        let raw = state.spotTimerMessage ?? "IDLE"
        let parts = raw.split(separator: "|")
        let key = String(parts.first ?? "IDLE")
        let param = Float(parts.dropFirst().first ?? "0") ?? 0.0

        switch key {
        case "IDLE": return state.isMotorLocked ? L10n.tr("status_motor_locked") : L10n.tr("spot_idle")
        case "SEARCHING": return L10n.tr("spot_searching")
        case "TOO_LOW": return String(format: "高度過低 (%.1fm < 1.0m)", param)
        case "TOO_HIGH": return String(format: "高度過高 (%.1fm > 3.5m)", param)
        case "ROTATING": return L10n.tr("spot_rotating")
        case "YAW_ERROR_H": return String(format: "H坪航向偏差 %.0f° (需 ≤8°)", param)
        case "YAW_ERROR_G": return String(format: "角錐航向偏差 %.0f° (需 ≤15°)", param)
        case "PERFECT_H": return L10n.tr("spot_perfect_h")
        case "SUCCESS_G": return L10n.tr("spot_success_g")
        case "COUNTING_H": return String(format: "H坪定點倒數: %.1fs", param)
        case "COUNTING_G": return String(format: "角錐定點倒數: %.1fs", param)
        default: return raw
        }
    }

    var body: some View {
        if state.isSpotTimerEnabled && !state.isCollision && !state.showSettings {
            let theme = TacticalColors.current(state)
            let dotColor: Color = state.spotTimerSuccess ? .green : (state.spotTimerStable ? .cyan : .red)

            HStack(spacing: 8) {
                Circle()
                    .fill(dotColor)
                    .frame(width: 7, height: 7)
                Text(formatMessage())
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(theme.textPrimary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 8).fill(theme.panel))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(theme.border, lineWidth: 1))
        }
    }
}

// MARK: - 6. 碰撞損毀覆蓋層 (顯示撞擊瞬間速度快照 impactSpeedSnapshot)
struct CollisionOverlayView: View {
    var state: DroneState
    var onReset: () -> Void

    var body: some View {
        if state.isCollision {
            let theme = TacticalColors.current(state)
            ZStack {
                Color.black.opacity(0.55).ignoresSafeArea()
                VStack(spacing: 14) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 38))
                        .foregroundStyle(theme.danger)
                    Text(L10n.tr("status_crash"))
                        .font(.system(size: 18, weight: .heavy))
                        .foregroundStyle(.white)
                    Text(String(format: "Impact Speed Snapshot: %.2f m/s | Code: %@", state.impactSpeedSnapshot, state.systemMessage ?? "IMPACT"))
                        .font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundStyle(theme.warning)
                    Button(action: onReset) {
                        HStack {
                            Image(systemName: "arrow.counterclockwise")
                            Text("RESET AIRCRAFT (H-PAD)")
                                .fontWeight(.bold)
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(theme.danger))
                    }
                }
                .padding(24)
                .background(RoundedRectangle(cornerRadius: 16).fill(theme.panel))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(theme.danger.opacity(0.7), lineWidth: 1.5))
            }
        }
    }
}
