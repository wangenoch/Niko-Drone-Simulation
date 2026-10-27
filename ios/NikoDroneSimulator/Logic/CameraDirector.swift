import Foundation
import simd

/// [v1.8.01] 視覺預設優化與相機導演系統 (1:1 對齊 ViewportOptimizer.kt & CameraDirector.kt)
enum ViewportOptimizer {
    struct ViewportParams {
        let fov: Float
        let zoom: Float
        let tilt: Float
    }

    static func getOptimalParams(
        observerHeight: Float,
        droneAltitude: Float,
        dronePosX: Float,
        dronePosZ: Float,
        cameraMode: String
    ) -> ViewportParams {
        let d = sqrt(dronePosX * dronePosX + dronePosZ * dronePosZ)
        let h = droneAltitude
        let v = AppConfig.VisualDefaults.self

        let baseFov: Float
        switch cameraMode {
        case AppConfig.CAM_MODE_STATION_FIXED: baseFov = v.DEFAULT_FIXED_FOV
        case AppConfig.CAM_MODE_STATION_TRACK: baseFov = v.DEFAULT_TRACKING_FOV
        case AppConfig.CAM_MODE_STATION_SMART: baseFov = v.DEFAULT_SMART_FOV
        default: baseFov = v.MAIN_FOV
        }

        if h < 0.2 || d < 2.0 {
            return ViewportParams(fov: baseFov, zoom: v.ZOOM_FACTOR_TRACKING, tilt: v.OBSERVER_TILT_TRACKING)
        } else if h > 15.0 || d > 20.0 {
            return ViewportParams(fov: 85.0, zoom: 1.0, tilt: 10.0)
        } else {
            return ViewportParams(fov: 55.0, zoom: 1.3, tilt: -5.0)
        }
    }
}

enum CameraDirector {
    static var smoothedFov: Float = 45.0
    static var smoothedZoom: Float = 1.5
    static var smoothedTilt: Float = 0.0
    static var smoothedHeight: Float = 6.0
    static var smoothedZoomPipFov: Float = 45.0

    static func snapToDefaults(height: Float, tilt: Float, zoom: Float, fov: Float) {
        smoothedHeight = height
        smoothedTilt = tilt
        smoothedZoom = zoom
        smoothedFov = fov
    }

    static func update(
        droneX: Float,
        droneY: Float,
        droneZ: Float,
        targetHeight: Float,
        targetTilt: Float,
        targetZoom: Float,
        targetFov: Float,
        cameraMode: String,
        lastManualTouchTime: TimeInterval,
        droneType: String,
        dt: Float,
        state: DroneState
    ) {
        let relH = max(droneY, 0.0)
        let d = sqrt(droneX * droneX + droneZ * droneZ)
        let strategy = ViewportOptimizer.getOptimalParams(
            observerHeight: targetHeight,
            droneAltitude: relH,
            dronePosX: droneX,
            dronePosZ: droneZ,
            cameraMode: cameraMode
        )
        let isOverrideActive = (Date().timeIntervalSince1970 - lastManualTouchTime < 3.0)

        let finalTargetHeight: Float
        let finalTargetTilt: Float
        let finalTargetZoom: Float
        let finalTargetFov: Float

        let isObserverMode = (cameraMode == AppConfig.CAM_MODE_OBS)

        if isObserverMode && !isOverrideActive {
            if d < 8.0 {
                let factor = min(max(d / 8.0, 0.0), 1.0)
                finalTargetHeight = 5.0 - (factor * 3.4)
            } else {
                finalTargetHeight = 1.6
            }
            finalTargetTilt = strategy.tilt
            finalTargetZoom = strategy.zoom
            finalTargetFov = strategy.fov
        } else if cameraMode == AppConfig.CAM_MODE_STATION_SMART {
            finalTargetHeight = targetHeight
            finalTargetTilt = targetTilt
            finalTargetZoom = min(max(1.2 + (d / 12.0), 1.0), 4.0)
            finalTargetFov = targetFov
        } else {
            finalTargetHeight = targetHeight
            finalTargetTilt = targetTilt
            finalTargetZoom = targetZoom
            finalTargetFov = targetFov
        }

        let lerp: Float = 0.05
        smoothedFov += (finalTargetFov - smoothedFov) * lerp
        smoothedZoom += (finalTargetZoom - smoothedZoom) * lerp
        smoothedTilt += (finalTargetTilt - smoothedTilt) * lerp
        smoothedHeight += (finalTargetHeight - smoothedHeight) * lerp

        let distToCam = sqrt(droneX * droneX + (smoothedHeight - droneY) * (smoothedHeight - droneY) + (droneZ + 9.0) * (droneZ + 9.0))
        let targetZoomPipFov = min(max(120.0 / max(distToCam, 0.1), 3.0), 45.0)
        smoothedZoomPipFov += (targetZoomPipFov - smoothedZoomPipFov) * lerp
    }

    static func computeCameraPose(
        mode: String,
        curX: Float,
        curY: Float,
        curZ: Float,
        curYaw: Float,
        predictX: Float,
        predictZ: Float,
        cameraTilt: Float,
        droneType: String
    ) -> (eye: SIMD3<Float>, target: SIMD3<Float>, up: SIMD3<Float>) {
        let dx = curX - 0.0
        let dz = curZ - (-9.0)
        let distH = max(sqrt(dx * dx + dz * dz), 0.001)

        switch mode {
        case AppConfig.CAM_MODE_STATION_SMART, AppConfig.CAM_MODE_STATION_TRACK:
            let isOverhead = distH < 2.0
            let up = isOverhead ? SIMD3<Float>(0, 0, 1) : SIMD3<Float>(0, 1, 0)
            let rad = smoothedTilt * .pi / 180.0
            let verticalShift: Float = isOverhead ? 0.0 : tan(rad) * distH
            return (
                eye: SIMD3<Float>(0, smoothedHeight, -9.0),
                target: SIMD3<Float>(curX, curY + verticalShift, curZ),
                up: up
            )
        case AppConfig.CAM_MODE_STATION_FIXED:
            let rad = smoothedTilt * .pi / 180.0
            let verticalTargetShift = tan(rad) * 9.0
            return (
                eye: SIMD3<Float>(0, smoothedHeight, -9.0),
                target: SIMD3<Float>(0, verticalTargetShift, 6.0),
                up: SIMD3<Float>(0, 1, 0)
            )
        case AppConfig.CAM_MODE_OBS:
            let isOverhead = distH < 2.0
            let up = isOverhead ? SIMD3<Float>(0, 0, 1) : SIMD3<Float>(0, 1, 0)
            let rad = smoothedTilt * .pi / 180.0
            let hWeight = min(max((curY - 3.0) / 9.0, 0.0), 1.0)
            let currentFov = smoothedFov / max(smoothedZoom, 0.1)
            let targetBiasDeg = hWeight * (currentFov * 0.25)
            let landingFactor = min(max((distH - 2.0) / 3.0, 0.0), 1.0)
            let distanceScale = min(max(distH / 6.0, 0.3), 1.0) * landingFactor
            let finalBiasRad = (targetBiasDeg * distanceScale) * .pi / 180.0
            let verticalShift: Float = !isOverhead ? (tan(rad) * distH) - (tan(finalBiasRad) * distH) : 0.0
            return (
                eye: SIMD3<Float>(0, smoothedHeight, -9.0),
                target: SIMD3<Float>(predictX, curY + verticalShift, predictZ),
                up: up
            )
        case AppConfig.CAM_MODE_FOLLOW:
            let rad = curYaw * .pi / 180.0
            let camX = curX - sin(rad) * 5.0
            let camZ = curZ - cos(rad) * 5.0
            return (
                eye: SIMD3<Float>(camX, curY + 2.5, camZ),
                target: SIMD3<Float>(curX, curY, curZ),
                up: SIMD3<Float>(0, 1, 0)
            )
        case AppConfig.CAM_MODE_FPV:
            let rad = curYaw * .pi / 180.0
            let tRad = cameraTilt * .pi / 180.0
            let spec = DroneRegistry.getSpec(droneType)
            let s = spec.scale
            let fOff = spec.cameraVisualOffset * s
            let eY = curY + spec.cameraHeightOffset * s
            let eX = curX + sin(rad) * fOff
            let eZ = curZ + cos(rad) * fOff
            let lX = eX + sin(rad) * cos(tRad) * 10.0
            let lZ = eZ + cos(rad) * cos(tRad) * 10.0
            let lY = eY + sin(tRad) * 10.0
            return (
                eye: SIMD3<Float>(eX, eY, eZ),
                target: SIMD3<Float>(lX, lY, lZ),
                up: SIMD3<Float>(-sin(rad) * sin(tRad), cos(tRad), -cos(rad) * sin(tRad))
            )
        default:
            return (
                eye: SIMD3<Float>(0, 6.0, -15.0),
                target: SIMD3<Float>(0, 0, 0),
                up: SIMD3<Float>(0, 1, 0)
            )
        }
    }
}
