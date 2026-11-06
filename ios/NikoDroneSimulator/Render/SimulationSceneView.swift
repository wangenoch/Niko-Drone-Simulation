import SwiftUI
import SceneKit
import simd

/// [v1.8.01] Metal 驅動之 3D 模擬場景控制器 (1:1 還原 FieldRenderer / DroneRenderer / EnvironmentalPropRenderer / ArAnchorRenderer)
final class SimulationSceneCoordinator: NSObject, SCNSceneRendererDelegate {
    static let shared = SimulationSceneCoordinator()

    let scene = SCNScene()
    let mainCameraNode = SCNNode()
    let fpvCameraNode = SCNNode()
    let zoomCameraNode = SCNNode()

    private let droneRootNode = SCNNode()
    private let droneShadowNode = SCNNode()
    private let arAnchorLineNode = SCNNode()
    private let arAnchorRingNode = SCNNode()
    private let tetherLineNode = SCNNode()
    private let obstaclesRootNode = SCNNode()
    private let windFlagPivotNode = SCNNode()
    private var windFlagSegments: [SCNNode] = []
    private let cloudLayerNode = SCNNode()
    private let mountainRootNode = SCNNode()
    private let sunNode = SCNNode()
    private let ambientLightNode = SCNNode()
    private let directionalLightNode = SCNNode()

    private var propellerNodes: [(node: SCNNode, part: DronePart)] = []
    private var builtDroneType: String = ""

    let physicsState = DronePhysicsState(posY: StandardDrone.shared.groundOffset, posX: 0.0, posZ: 0.0)
    private var lastFrameTime: TimeInterval = 0
    private var rendererTime: Float = 0.0
    private var randomWindPhase: Float = 0.0
    private var turbulencePhase: Float = 0.0
    private var pathSampleTimer: Float = 0.0

    override init() {
        super.init()
        setupScene()
    }

    private func setupScene() {
        scene.background.contents = UIColor(red: 0.42, green: 0.65, blue: 0.90, alpha: 1.0)

        // Cameras
        let mainCam = SCNCamera()
        mainCam.fieldOfView = 45.0
        mainCam.zNear = 0.1
        mainCam.zFar = 5000.0
        mainCameraNode.camera = mainCam
        scene.rootNode.addChildNode(mainCameraNode)

        let fpvCam = SCNCamera()
        fpvCam.fieldOfView = 85.0
        fpvCam.zNear = 0.22
        fpvCam.zFar = 1000.0
        fpvCameraNode.camera = fpvCam
        scene.rootNode.addChildNode(fpvCameraNode)

        let zoomCam = SCNCamera()
        zoomCam.fieldOfView = 25.0
        zoomCam.zNear = 0.1
        zoomCam.zFar = 1000.0
        zoomCameraNode.camera = zoomCam
        scene.rootNode.addChildNode(zoomCameraNode)

        // Ambient & Directional Light
        ambientLightNode.light = SCNLight()
        ambientLightNode.light?.type = .ambient
        ambientLightNode.light?.intensity = 650
        ambientLightNode.light?.color = UIColor.white
        scene.rootNode.addChildNode(ambientLightNode)

        directionalLightNode.light = SCNLight()
        directionalLightNode.light?.type = .directional
        directionalLightNode.light?.intensity = 900
        directionalLightNode.eulerAngles = SCNVector3(-Float.pi / 3, Float.pi / 6, 0)
        scene.rootNode.addChildNode(directionalLightNode)

        buildFieldGeometry()
        buildObstaclesGeometry()
        buildWindFlagGeometry()
        buildAtmosphereGeometry()
        buildArAnchorGeometry()
        buildDroneGeometry(for: DroneState.shared.droneType)

        scene.rootNode.addChildNode(droneRootNode)
        scene.rootNode.addChildNode(droneShadowNode)
    }

    // MARK: - 1. 考照場地幾何 (1:1 對齊 FieldRenderer.kt)
    private func buildFieldGeometry() {
        let fieldNode = SCNNode()

        // 廣闊草地背景 (800m x 800m)
        fieldNode.addChildNode(makeFlatRect(x: 0, y: -0.1, z: 0, w: 800, d: 800, color: [0.15, 0.35, 0.15, 1.0]))
        // 考場水泥地坪 (120m x 100m, Z=6)
        fieldNode.addChildNode(makeFlatRect(x: 0, y: -0.05, z: 6.0, w: 120, d: 100, color: [0.35, 0.35, 0.35, 1.0]))

        let colorWhite: [Float] = [1.0, 1.0, 1.0, 0.9]
        let colorYellow: [Float] = [1.0, 0.9, 0.0, 0.9]
        let colorRed: [Float] = [1.0, 0.1, 0.1, 0.75]
        let lineY: Float = 0.005

        // 中軸十字基準線
        fieldNode.addChildNode(makeFlatRect(x: 0, y: lineY, z: 6.0, w: 0.08, d: 23.0, color: colorWhite))
        fieldNode.addChildNode(makeFlatRect(x: 0, y: lineY, z: 6.0, w: 30.0, d: 0.08, color: colorWhite))

        // 矩形框 (16m / 8m, Z=6)
        addRectOutline(to: fieldNode, y: lineY + 0.002, offsetZ: 6.0, w: 16.0, d: 16.0, color: colorYellow)
        addRectOutline(to: fieldNode, y: lineY + 0.002, offsetZ: 6.0, w: 8.0, d: 8.0, color: colorYellow)

        // 8 字圓圈 (中心 ±6, Z=6, 半徑 8m 與 4m)
        fieldNode.addChildNode(makeRingNode(x: 6.0, y: lineY + 0.004, z: 6.0, radius: 8.0, thickness: 0.12, color: colorRed))
        fieldNode.addChildNode(makeRingNode(x: 6.0, y: lineY + 0.004, z: 6.0, radius: 4.0, thickness: 0.12, color: colorRed))
        fieldNode.addChildNode(makeRingNode(x: -6.0, y: lineY + 0.004, z: 6.0, radius: 8.0, thickness: 0.12, color: colorRed))
        fieldNode.addChildNode(makeRingNode(x: -6.0, y: lineY + 0.004, z: 6.0, radius: 4.0, thickness: 0.12, color: colorRed))

        // H 坪 (0, 0, 0)
        fieldNode.addChildNode(makeDiscNode(x: 0, y: lineY + 0.006, z: 0, radius: 1.2, color: [1.0, 1.0, 1.0, 1.0]))
        fieldNode.addChildNode(makeDiscNode(x: 0, y: lineY + 0.008, z: 0, radius: 1.0, color: [0.0, 0.35, 0.7, 1.0]))
        fieldNode.addChildNode(makeFlatRect(x: -0.2, y: lineY + 0.01, z: 0, w: 0.06, d: 0.4, color: colorWhite))
        fieldNode.addChildNode(makeFlatRect(x: 0.2, y: lineY + 0.01, z: 0, w: 0.06, d: 0.4, color: colorWhite))
        fieldNode.addChildNode(makeFlatRect(x: 0.0, y: lineY + 0.01, z: 0, w: 0.35, d: 0.06, color: colorWhite))

        // 紅圈定位點 (X=±13.5, Z=16)
        fieldNode.addChildNode(makeDiscNode(x: -13.5, y: lineY + 0.005, z: 16.0, radius: 1.0, color: [1.0, 0.0, 0.0, 1.0]))
        fieldNode.addChildNode(makeDiscNode(x: 13.5, y: lineY + 0.005, z: 16.0, radius: 1.0, color: [1.0, 0.0, 0.0, 1.0]))

        // 觀察員站位標線 (Z=-9)
        fieldNode.addChildNode(makeFlatRect(x: 0, y: lineY + 0.002, z: -9.0, w: 1.8, d: 0.1, color: colorWhite))
        fieldNode.addChildNode(makeFlatRect(x: 0, y: lineY + 0.002, z: -9.25, w: 0.1, d: 0.5, color: colorWhite))

        // 7 座標準考照角錐
        for cone in Constants.CONE_POSITIONS {
            addCone(to: fieldNode, x: cone[0], z: cone[1])
        }

        scene.rootNode.addChildNode(fieldNode)
    }

    private func buildObstaclesGeometry() {
        for p in Constants.OBSTACLES {
            let x = p[0]; let z = p[1]; let h = p[2]; let kind = Int(p[3])
            switch kind {
            case 0:
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h / 2, tz: z, w: 5.0, h: h, d: 5.0, color: [0.3, 0.32, 0.35, 1.0]))
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h, tz: z, w: 5.2, h: 0.5, d: 5.2, color: [0.15, 0.15, 0.15, 1.0]))
            case 1:
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h / 2, tz: z, w: 4.0, h: h, d: 4.0, color: [0.35, 0.38, 0.42, 1.0]))
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h, tz: z, w: 4.2, h: 0.3, d: 4.2, color: [0.2, 0.2, 0.2, 1.0]))
            case 2:
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h * 0.25, tz: z, w: 0.8, h: h * 0.5, d: 0.8, color: [0.35, 0.2, 0.1, 1.0]))
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h * 0.6, tz: z, w: 3.5, h: h * 0.4, d: 3.5, color: [0.1, 0.4, 0.15, 1.0]))
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h * 0.85, tz: z, w: 2.5, h: h * 0.3, d: 2.5, color: [0.15, 0.5, 0.2, 1.0]))
            default:
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h * 0.3, tz: z, w: 0.6, h: h * 0.6, d: 0.6, color: [0.3, 0.15, 0.05, 1.0]))
                obstaclesRootNode.addChildNode(makeBoxNode(tx: x, ty: h * 0.75, tz: z, w: 4.0, h: 3.5, d: 4.0, color: [0.05, 0.45, 0.1, 1.0]))
            }
        }
        // 考官帳篷 (Z=-11.5)
        let tentColor: [Float] = [0.2, 0.4, 0.8, 1.0]
        let poles: [[Float]] = [[-2.0, -9.6], [2.0, -9.6], [-2.0, -13.4], [2.0, -13.4]]
        for p in poles {
            obstaclesRootNode.addChildNode(makeBoxNode(tx: p[0], ty: 1.25, tz: p[1], w: 0.1, h: 2.5, d: 0.1, color: [0.7, 0.7, 0.7, 1.0]))
        }
        obstaclesRootNode.addChildNode(makeBoxNode(tx: 0, ty: 2.6, tz: -11.5, w: 4.2, h: 0.2, d: 4.0, color: tentColor))
        obstaclesRootNode.addChildNode(makeBoxNode(tx: 0, ty: 2.8, tz: -11.5, w: 3.0, h: 0.3, d: 3.0, color: tentColor))

        scene.rootNode.addChildNode(obstaclesRootNode)
    }

    private func buildWindFlagGeometry() {
        let poleX: Float = -14.0
        let poleZ: Float = 19.0
        let poleNode = makeBoxNode(tx: poleX, ty: 1.5, tz: poleZ, w: 0.1, h: 3.0, d: 0.1, color: [0.4, 0.4, 0.4, 1.0])
        scene.rootNode.addChildNode(poleNode)

        windFlagPivotNode.simdPosition = SIMD3<Float>(poleX, 2.8, poleZ)
        for i in 0..<4 {
            let size: Float = 0.3 - Float(i) * 0.04
            let color: [Float] = (i % 2 == 0) ? [1.0, 0.3, 0.0, 1.0] : [1.0, 1.0, 1.0, 1.0]
            let seg = makeBoxNode(tx: Float(i) * 0.3 + 0.15, ty: 0, tz: 0, w: 0.3, h: size, d: size, color: color)
            windFlagSegments.append(seg)
            windFlagPivotNode.addChildNode(seg)
        }
        scene.rootNode.addChildNode(windFlagPivotNode)
    }

    private func buildAtmosphereGeometry() {
        // 遠景山脈
        for i in 0..<14 {
            let angle = Float(i) * (2.0 * .pi / 14.0)
            let dist: Float = 260.0
            let mx = sin(angle) * dist
            let mz = cos(angle) * dist
            let mh: Float = 28.0 + Float((i * 17) % 22)
            let mountain = SCNCone(topRadius: 2.0, bottomRadius: 55.0, height: CGFloat(mh))
            mountain.firstMaterial?.diffuse.contents = UIColor(red: 0.22, green: 0.32, blue: 0.38, alpha: 0.9)
            let mNode = SCNNode(geometry: mountain)
            mNode.simdPosition = SIMD3<Float>(mx, mh / 2.0 - 1.0, mz)
            mountainRootNode.addChildNode(mNode)
        }
        scene.rootNode.addChildNode(mountainRootNode)

        // 太陽
        let sunSphere = SCNSphere(radius: 8.0)
        sunSphere.firstMaterial?.diffuse.contents = UIColor(red: 1.0, green: 0.95, blue: 0.7, alpha: 1.0)
        sunSphere.firstMaterial?.emission.contents = UIColor(red: 1.0, green: 0.85, blue: 0.4, alpha: 1.0)
        sunNode.geometry = sunSphere
        sunNode.simdPosition = SIMD3<Float>(0, 140, 220)
        scene.rootNode.addChildNode(sunNode)

        // 動態雲層群
        for i in 0..<18 {
            let cx = Float((i % 6) - 3) * 45.0 + Float((i * 13) % 19)
            let cz = Float((i / 6) - 1) * 55.0 + 30.0
            let cloudBox = SCNBox(width: 24.0, height: 2.5, length: 16.0, chamferRadius: 1.2)
            cloudBox.firstMaterial?.diffuse.contents = UIColor(white: 0.96, alpha: 0.55)
            let cNode = SCNNode(geometry: cloudBox)
            cNode.simdPosition = SIMD3<Float>(cx, 65.0 + Float(i % 3) * 4.0, cz)
            cloudLayerNode.addChildNode(cNode)
        }
        scene.rootNode.addChildNode(cloudLayerNode)
    }

    private func buildArAnchorGeometry() {
        let ring = SCNTorus(ringRadius: 0.6, pipeRadius: 0.035)
        ring.firstMaterial?.diffuse.contents = UIColor(red: 0.0, green: 1.0, blue: 1.0, alpha: 0.75)
        ring.firstMaterial?.emission.contents = UIColor(red: 0.0, green: 0.8, blue: 0.8, alpha: 0.5)
        arAnchorRingNode.geometry = ring
        scene.rootNode.addChildNode(arAnchorRingNode)

        let lineCyl = SCNCylinder(radius: 0.015, height: 1.0)
        lineCyl.firstMaterial?.diffuse.contents = UIColor(white: 1.0, alpha: 0.45)
        arAnchorLineNode.geometry = lineCyl
        scene.rootNode.addChildNode(arAnchorLineNode)

        let tetherCyl = SCNCylinder(radius: 0.02, height: 1.0)
        tetherCyl.firstMaterial?.diffuse.contents = UIColor(red: 1.0, green: 0.6, blue: 0.0, alpha: 0.75)
        tetherLineNode.geometry = tetherCyl
        scene.rootNode.addChildNode(tetherLineNode)
    }

    // MARK: - 2. 數據驅動無人機幾何 (1:1 對齊 DroneRenderer.kt)
    func buildDroneGeometry(for droneId: String) {
        guard builtDroneType != droneId else { return }
        builtDroneType = droneId

        droneRootNode.childNodes.forEach { $0.removeFromParentNode() }
        propellerNodes.removeAll()

        let spec = DroneRegistry.getSpec(droneId)
        let module = DroneRegistry.getModule(droneId)
        let s = spec.scale

        for part in module.geometry {
            let node = makeBoxNode(
                tx: part.tx * s,
                ty: part.ty * s,
                tz: part.tz * s,
                w: part.w * s,
                h: part.h * s,
                d: part.d * s,
                color: part.color,
                rx: part.rx,
                ry: part.ry,
                rz: part.rz
            )
            droneRootNode.addChildNode(node)
            if part.isPropeller || part.isTailPropeller {
                propellerNodes.append((node: node, part: part))
            }
        }

        // 陰影圓盤
        droneShadowNode.childNodes.forEach { $0.removeFromParentNode() }
        let shadowDisc = makeDiscNode(x: 0, y: 0.04, z: 0, radius: spec.shadowSizeBase, color: [0, 0, 0, 0.42])
        droneShadowNode.addChildNode(shadowDisc)
    }

    // MARK: - 3. 每幀物理與視覺更新 (1:1 對齊 DroneSimulationRenderer.kt)
    func stepSimulation(in view: SCNView?) {
        let now = CACurrentMediaTime()
        if lastFrameTime == 0 { lastFrameTime = now }
        let dt = Float(min(max(now - lastFrameTime, 0.001), 0.05))
        lastFrameTime = now
        rendererTime += dt

        let ds = DroneState.shared
        let stick = StickInputState.shared

        if builtDroneType != ds.droneType {
            buildDroneGeometry(for: ds.droneType)
        }

        // 處理 USB-C HID 搖桿與 CSC 解鎖手勢
        TypeCHidControllerManager.shared.stepStickInteraction(dt: dt, state: ds, stickState: stick)

        // 更新大氣風場 (統一時鐘：鎖定時使用 rendererTime，解鎖飛行時由 PhysicsEngine 使用 flightTime 更新)
        let unifiedWindTime = ds.isMotorLocked ? rendererTime : physicsState.flightTime
        if ds.isMotorLocked {
            WindManager.update(currentTime: unifiedWindTime, level: ds.windLevel, variation: ds.windVariation, useHardcore: ds.useHardcorePhysics)
        }
        let windRes = WindManager.calculateWindVector(
            level: ds.windLevel,
            direction: ds.windDirection,
            variation: ds.windVariation,
            dirVariation: ds.windDirVariation,
            flightTime: unifiedWindTime,
            randomWindAngle: ds.randomWindAngle
        )
        ds.currentWindAngle = windRes.visualAngle

        let isPaused = ds.pauseInSettings && ds.showSettings
        if isPaused {
            DroneSoundManager.shared.update(
                rpmFactor: 0.0,
                isLocked: true,
                isMuted: true,
                windLevel: 0,
                droneSpeed: 0.0,
                droneType: ds.droneType
            )
        } else {
            randomWindPhase += dt * (1.2 + Float(ds.windVariation) * 0.6)
            turbulencePhase += dt * (0.8 + Float(ds.windVariation) * 0.4)

            let ctrlT = stick.stickThrottle(ds)
            let ctrlY = stick.stickYaw(ds)
            let ctrlP = stick.stickPitch(ds)
            let ctrlR = stick.stickRoll(ds)

            let atmos = PhysicsEngine.AtmosConfig(
                windLevel: ds.windLevel,
                windDirection: ds.windDirection,
                windVariation: ds.windVariation,
                windDirVariation: ds.windDirVariation,
                enableVerticalDraft: ds.enableVerticalDraft,
                useFlightLimit: ds.useFlightLimit,
                randomWindPhase: randomWindPhase,
                turbulencePhase: turbulencePhase,
                randomDirAngle: 0.0,
                applyPhysicalSpecs: ds.applyPhysicalSpecs,
                isMotorLocked: ds.isMotorLocked,
                useHardcore: ds.useHardcorePhysics,
                useStrictLanding: ds.useStrictLanding,
                showObstacles: ds.showObstacles,
                enableGroundEffect: ds.enableGroundEffect,
                enableNonCenterForce: ds.enableNonCenterForce,
                isTetherModeEnabled: ds.isTetherModeEnabled
            )

            let result = PhysicsEngine.step(
                dt: dt,
                state: physicsState,
                input: PhysicsEngine.ControlInput(throttle: ctrlT, yaw: ctrlY, pitch: ctrlP, roll: ctrlR),
                atmos: atmos,
                droneType: ds.droneType
            )

            let spec = DroneRegistry.getSpec(ds.droneType)
            CameraDirector.update(
                droneX: physicsState.posX,
                droneY: physicsState.posY - spec.groundOffset,
                droneZ: physicsState.posZ,
                targetHeight: ds.observerHeight,
                targetTilt: ds.observerTilt,
                targetZoom: ds.zoomFactor,
                targetFov: ds.mainFOV,
                cameraMode: ds.cameraMode,
                lastManualTouchTime: ds.lastManualTouchTime,
                droneType: ds.droneType,
                dt: dt,
                state: ds
            )

            // 同步寫回 DroneState
            ds.altitude = physicsState.posY
            ds.posX = physicsState.posX
            ds.posZ = physicsState.posZ
            ds.yaw = physicsState.yaw
            ds.pitch = physicsState.visPitch
            ds.roll = physicsState.visRoll
            let totalVel = sqrt(physicsState.velX * physicsState.velX + physicsState.velY * physicsState.velY + physicsState.velZ * physicsState.velZ)
            ds.speed = totalVel
            ds.horizontalDist = result.distanceH
            ds.batteryVoltage = physicsState.batteryVoltage
            ds.batteryPercent = physicsState.batteryPercent
            ds.motorRpmFactor = result.motorRpm
            ds.sessionFlightTime = physicsState.flightTime
            ds.isNearBoundary = PhysicsEngine.isNearBoundary(x: physicsState.posX, z: physicsState.posZ)

            if result.isImpact && !ds.isCollision {
                ds.isCollision = true
                ds.crashReason = (result.systemMessage == "OUT_OF_BOUNDS") ? .outOfBounds : .impact
                ds.impactSpeedSnapshot = result.impactSpeed
                ds.isMotorLocked = true
            } else if ds.useFlightLimit && ds.batteryPercent <= 0 && !ds.isCollision && !ds.isMotorLocked {
                ds.isCollision = true
                ds.crashReason = .batteryLow
                ds.impactSpeedSnapshot = totalVel
                ds.isMotorLocked = true
            }
            if let msg = result.systemMessage {
                ds.systemMessage = msg
            } else if ds.systemMessage?.hasPrefix("TETHER_") == true {
                ds.systemMessage = nil
            }

            // 雲層隨風漂移
            if windRes.forceVector.count >= 3 {
                ds.cloudU += windRes.forceVector[0] * dt * 0.35
                ds.cloudV += windRes.forceVector[2] * dt * 0.35
            }

            // 軌跡記錄
            if ds.showFlightPath && !ds.isMotorLocked {
                pathSampleTimer += dt
                if pathSampleTimer >= 0.15 {
                    pathSampleTimer = 0.0
                    var path = ds.flightPath
                    path.append(CGPoint(x: CGFloat(physicsState.posX), y: CGFloat(physicsState.posZ)))
                    if path.count > 200 { path.removeFirst() }
                    ds.flightPath = path
                }
            }

            // 定點計時評測器
            SpotTimerEvaluator.update(state: ds, dt: dt, spec: spec)

            // 合成音效更新
            DroneSoundManager.shared.update(
                rpmFactor: ds.motorRpmFactor,
                isLocked: ds.isMotorLocked,
                isMuted: ds.isMuted || ds.isCollision,
                windLevel: ds.windLevel,
                droneSpeed: ds.speed,
                droneType: ds.droneType
            )
        }

        updateVisualNodes(state: ds)
        if let scnView = view {
            let projected = scnView.projectPoint(SCNVector3(physicsState.posX, physicsState.posY, physicsState.posZ))
            if projected.z < 1.0 {
                ds.droneScreenPos = CGPoint(x: CGFloat(projected.x), y: CGFloat(projected.y))
            }
        }
    }

    private func updateVisualNodes(state: DroneState) {
        let spec = DroneRegistry.getSpec(state.droneType)
        let drawY = physicsState.posY + spec.visualOffset

        // 1. 無人機位置與憲法姿態對位 (Yaw -> Pitch -> Roll)
        let yawRad = physicsState.yaw * .pi / 180.0
        let pitchRad = physicsState.visPitch * .pi / 180.0
        let rollRad = physicsState.visRoll * .pi / 180.0

        var transform = matrix_identity_float4x4
        transform.columns.3 = SIMD4<Float>(physicsState.posX, drawY, physicsState.posZ, 1.0)
        let rotY = simd_float4x4(simd_quatf(angle: yawRad, axis: SIMD3<Float>(0, 1, 0)))
        let rotX = simd_float4x4(simd_quatf(angle: pitchRad, axis: SIMD3<Float>(1, 0, 0)))
        let rotZ = simd_float4x4(simd_quatf(angle: rollRad, axis: SIMD3<Float>(0, 0, 1)))
        droneRootNode.simdTransform = simd_mul(transform, simd_mul(rotY, simd_mul(rotX, rotZ)))

        // 2. 螺旋槳與尾槳旋轉
        let s = spec.scale
        for item in propellerNodes {
            let part = item.part
            let spd = !state.isMotorLocked ? (rendererTime * part.baseRpm * max(state.motorRpmFactor, 0.25)) : 0.0
            let rx = part.rx * .pi / 180.0
            let ry = (part.isPropeller ? (part.ry + spd * part.rotationDirection) : part.ry) * .pi / 180.0
            let rz = (part.isTailPropeller ? (part.rz + spd * part.rotationDirection) : part.rz) * .pi / 180.0
            item.node.simdPosition = SIMD3<Float>(part.tx * s, part.ty * s, part.tz * s)
            item.node.simdEulerAngles = SIMD3<Float>(rx, ry, rz)
        }

        // 3. 陰影與 AR 投影環
        droneShadowNode.isHidden = !state.showShadow
        droneShadowNode.opacity = CGFloat(min(max(state.shadowIntensity, 0.1), 1.0))
        let relAlt = max(physicsState.posY - spec.groundOffset, 0.0)
        let sunAngle = (state.sunPosition * 180.0) * .pi / 180.0
        let shadowX = state.isSunSimEnabled ? (physicsState.posX - cos(sunAngle) * 1.2 * relAlt) : physicsState.posX
        droneShadowNode.simdPosition = SIMD3<Float>(shadowX, 0.02, physicsState.posZ)

        let showAnchor = state.showGroundAnchor && relAlt >= 0.2
        arAnchorRingNode.isHidden = !showAnchor
        arAnchorLineNode.isHidden = !showAnchor
        if showAnchor {
            arAnchorRingNode.simdPosition = SIMD3<Float>(physicsState.posX, spec.groundOffset + 0.02, physicsState.posZ)
            arAnchorLineNode.simdPosition = SIMD3<Float>(physicsState.posX, spec.groundOffset + relAlt / 2.0, physicsState.posZ)
            arAnchorLineNode.simdScale = SIMD3<Float>(1.0, relAlt, 1.0)
        }

        // 4. 繫留繩索視覺化 (防止正上方時 up 向量共線產生 NaN)
        tetherLineNode.isHidden = !state.isTetherModeEnabled
        if state.isTetherModeEnabled {
            let start = SIMD3<Float>(0, 0.02, 0)
            let end = SIMD3<Float>(physicsState.posX, physicsState.posY, physicsState.posZ)
            let mid = (start + end) * 0.5
            let diff = end - start
            let len = max(simd_length(diff), 0.05)
            let dir = diff / len
            let tetherUp = abs(dir.z) > 0.95 ? SIMD3<Float>(0, 1, 0) : SIMD3<Float>(0, 0, 1)
            tetherLineNode.simdPosition = mid
            tetherLineNode.simdScale = SIMD3<Float>(1.0, len, 1.0)
            tetherLineNode.simdLook(at: end, up: tetherUp, localFront: SIMD3<Float>(0, 1, 0))
        }

        // 5. 障礙物、山脈、雲層、太陽與天色更新
        obstaclesRootNode.isHidden = !state.showObstacles
        mountainRootNode.isHidden = !state.showMountains
        cloudLayerNode.isHidden = !state.showClouds
        cloudLayerNode.opacity = CGFloat(min(max(state.cloudDensity, 0.1), 1.0))
        let driftX = state.cloudU.truncatingRemainder(dividingBy: 90.0)
        let driftZ = state.cloudV.truncatingRemainder(dividingBy: 90.0)
        cloudLayerNode.simdPosition = SIMD3<Float>(driftX, 0, driftZ)

        sunNode.isHidden = !state.isSunSimEnabled
        if state.isSunSimEnabled {
            sunNode.simdPosition = SIMD3<Float>(cos(sunAngle) * 180.0, max(sin(sunAngle) * 140.0, 22.0), 220.0)
            directionalLightNode.eulerAngles = SCNVector3(-max(sin(sunAngle), 0.25) * (.pi / 2.4), cos(sunAngle) * (.pi / 3.0), 0)
        }

        // 根據時段 (MORNING / NOON / AFTERNOON) 與天氣模式調整天色與光照
        let weatherDim: CGFloat = state.weatherMode == 2 ? 0.68 : (state.weatherMode == 1 ? 0.85 : 1.0)
        switch state.timeOfDay {
        case AppConfig.TIME_MORNING:
            scene.background.contents = UIColor(red: 0.55 * weatherDim, green: 0.72 * weatherDim, blue: 0.92 * weatherDim, alpha: 1.0)
            ambientLightNode.light?.intensity = 580 * weatherDim
            directionalLightNode.light?.intensity = 820 * weatherDim
            directionalLightNode.light?.color = UIColor(red: 1.0, green: 0.94, blue: 0.84, alpha: 1.0)
        case AppConfig.TIME_AFTERNOON:
            scene.background.contents = UIColor(red: 0.72 * weatherDim, green: 0.58 * weatherDim, blue: 0.52 * weatherDim, alpha: 1.0)
            ambientLightNode.light?.intensity = 520 * weatherDim
            directionalLightNode.light?.intensity = 780 * weatherDim
            directionalLightNode.light?.color = UIColor(red: 1.0, green: 0.82, blue: 0.64, alpha: 1.0)
        default:
            scene.background.contents = UIColor(red: 0.42 * weatherDim, green: 0.65 * weatherDim, blue: 0.90 * weatherDim, alpha: 1.0)
            ambientLightNode.light?.intensity = 650 * weatherDim
            directionalLightNode.light?.intensity = 900 * weatherDim
            directionalLightNode.light?.color = UIColor.white
        }

        windFlagPivotNode.isHidden = (state.windLevel <= 0)
        if state.windLevel > 0 {
            let rotDeg = 270.0 - state.currentWindAngle
            windFlagPivotNode.simdEulerAngles = SIMD3<Float>(0, rotDeg * .pi / 180.0, 0)
            for (i, seg) in windFlagSegments.enumerated() {
                let offX = Float(i) * 0.3 + 0.15
                let dip = (Float(i * i) * 0.05) * (1.0 - Float(state.windLevel) * 0.15)
                let wobble = sin(rendererTime * 5.0 + Float(i)) * 0.02 * Float(state.windLevel)
                seg.simdPosition = SIMD3<Float>(offX, -dip + wobble, 0)
            }
        }

        // 6. 更新三顆攝影機矩陣 (主畫面 / FPV PiP / Zoom Assistant PiP)
        let mainPose = CameraDirector.computeCameraPose(
            mode: state.cameraMode,
            curX: physicsState.posX,
            curY: physicsState.posY,
            curZ: physicsState.posZ,
            curYaw: physicsState.yaw,
            predictX: physicsState.posX + physicsState.velX * 0.12,
            predictZ: physicsState.posZ + physicsState.velZ * 0.12,
            cameraTilt: state.cameraTilt,
            droneType: state.droneType
        )
        let finalFov = (state.cameraMode == AppConfig.CAM_MODE_FPV) ? spec.fpvFov : CameraDirector.smoothedFov
        let finalZoom = (state.cameraMode == AppConfig.CAM_MODE_FPV) ? state.zoomFactor : CameraDirector.smoothedZoom
        mainCameraNode.camera?.zNear = (state.cameraMode == AppConfig.CAM_MODE_FPV) ? 0.22 : 0.1
        mainCameraNode.camera?.fieldOfView = CGFloat(max(finalFov / max(finalZoom, 0.2), 5.0))
        mainCameraNode.simdPosition = mainPose.eye
        mainCameraNode.simdLook(at: mainPose.target, up: mainPose.up, localFront: SIMD3<Float>(0, 0, -1))

        let fpvPose = CameraDirector.computeCameraPose(
            mode: AppConfig.CAM_MODE_FPV,
            curX: physicsState.posX,
            curY: physicsState.posY,
            curZ: physicsState.posZ,
            curYaw: physicsState.yaw,
            predictX: physicsState.posX,
            predictZ: physicsState.posZ,
            cameraTilt: state.cameraTilt,
            droneType: state.droneType
        )
        fpvCameraNode.camera?.fieldOfView = CGFloat(spec.fpvFov)
        fpvCameraNode.simdPosition = fpvPose.eye
        fpvCameraNode.simdLook(at: fpvPose.target, up: fpvPose.up, localFront: SIMD3<Float>(0, 0, -1))

        let dx = physicsState.posX
        let dz = physicsState.posZ + 9.0
        let distH = max(sqrt(dx * dx + dz * dz), 0.001)
        let zoomUp = (distH < 1.5) ? SIMD3<Float>(0, 0, 1) : SIMD3<Float>(0, 1, 0)
        zoomCameraNode.camera?.fieldOfView = CGFloat(CameraDirector.smoothedZoomPipFov)
        zoomCameraNode.simdPosition = SIMD3<Float>(0, CameraDirector.smoothedHeight, -9.0)
        zoomCameraNode.simdLook(at: SIMD3<Float>(physicsState.posX, physicsState.posY, physicsState.posZ), up: zoomUp, localFront: SIMD3<Float>(0, 0, -1))
    }

    func resetSimulation() {
        let ds = DroneState.shared
        let spec = DroneRegistry.getSpec(ds.droneType)
        PhysicsEngine.clearState()
        WindManager.reset()
        physicsState.reset(groundY: spec.groundOffset)
        ds.altitude = spec.groundOffset
        ds.posX = 0.0
        ds.posZ = 0.0
        ds.yaw = 0.0
        ds.pitch = 0.0
        ds.roll = 0.0
        ds.speed = 0.0
        ds.horizontalDist = 0.0
        ds.batteryVoltage = 4.2
        ds.batteryPercent = 100
        ds.motorRpmFactor = 0.0
        ds.sessionFlightTime = 0.0
        ds.isCollision = false
        ds.crashReason = .none
        ds.isMotorLocked = true
        ds.isNearBoundary = false
        ds.systemMessage = nil
        ds.flightPath = []
        ds.spotTimerSeconds = AppConfig.SystemDefaults.SPOT_TIMER_SECONDS
        ds.spotTimerTargetId = -1
        ds.spotTimerMessage = "IDLE"
        ds.spotTimerSuccess = false
        ds.spotTimerInZone = false
        ds.spotTimerStable = false
        CameraDirector.snapToDefaults(height: ds.observerHeight, tilt: ds.observerTilt, zoom: ds.zoomFactor, fov: ds.mainFOV)
    }

    // MARK: - 幾何建構輔助函數
    private func makeBoxNode(tx: Float, ty: Float, tz: Float, w: Float, h: Float, d: Float, color: [Float], rx: Float = 0, ry: Float = 0, rz: Float = 0) -> SCNNode {
        let box = SCNBox(width: CGFloat(w), height: CGFloat(h), length: CGFloat(d), chamferRadius: 0)
        let mat = SCNMaterial()
        mat.diffuse.contents = UIColor(red: CGFloat(color[0]), green: CGFloat(color[1]), blue: CGFloat(color[2]), alpha: CGFloat(color[3]))
        box.materials = [mat]
        let node = SCNNode(geometry: box)
        node.simdPosition = SIMD3<Float>(tx, ty, tz)
        node.simdEulerAngles = SIMD3<Float>(rx * .pi / 180.0, ry * .pi / 180.0, rz * .pi / 180.0)
        return node
    }

    private func makeFlatRect(x: Float, y: Float, z: Float, w: Float, d: Float, color: [Float]) -> SCNNode {
        makeBoxNode(tx: x, ty: y, tz: z, w: w, h: 0.002, d: d, color: color)
    }

    private func addRectOutline(to parent: SCNNode, y: Float, offsetZ: Float, w: Float, d: Float, color: [Float]) {
        let t: Float = 0.12
        parent.addChildNode(makeFlatRect(x: 0, y: y, z: offsetZ + d / 2.0, w: w + t, d: t, color: color))
        parent.addChildNode(makeFlatRect(x: 0, y: y, z: offsetZ - d / 2.0, w: w + t, d: t, color: color))
        parent.addChildNode(makeFlatRect(x: -w / 2.0, y: y, z: offsetZ, w: t, d: d - t, color: color))
        parent.addChildNode(makeFlatRect(x: w / 2.0, y: y, z: offsetZ, w: t, d: d - t, color: color))
    }

    private func makeDiscNode(x: Float, y: Float, z: Float, radius: Float, color: [Float]) -> SCNNode {
        let cyl = SCNCylinder(radius: CGFloat(radius), height: 0.003)
        cyl.firstMaterial?.diffuse.contents = UIColor(red: CGFloat(color[0]), green: CGFloat(color[1]), blue: CGFloat(color[2]), alpha: CGFloat(color[3]))
        let node = SCNNode(geometry: cyl)
        node.simdPosition = SIMD3<Float>(x, y, z)
        return node
    }

    private func makeRingNode(x: Float, y: Float, z: Float, radius: Float, thickness: Float, color: [Float]) -> SCNNode {
        let torus = SCNTorus(ringRadius: CGFloat(radius), pipeRadius: CGFloat(thickness / 2.0))
        torus.firstMaterial?.diffuse.contents = UIColor(red: CGFloat(color[0]), green: CGFloat(color[1]), blue: CGFloat(color[2]), alpha: CGFloat(color[3]))
        let node = SCNNode(geometry: torus)
        node.simdPosition = SIMD3<Float>(x, y, z)
        return node
    }

    private func addCone(to parent: SCNNode, x: Float, z: Float) {
        parent.addChildNode(makeBoxNode(tx: x, ty: 0.025, tz: z, w: 0.4, h: 0.05, d: 0.4, color: [0.1, 0.1, 0.1, 1.0]))
        parent.addChildNode(makeBoxNode(tx: x, ty: 0.20, tz: z, w: 0.3, h: 0.3, d: 0.3, color: [1.0, 0.4, 0.0, 1.0]))
        parent.addChildNode(makeBoxNode(tx: x, ty: 0.45, tz: z, w: 0.2, h: 0.2, d: 0.2, color: [1.0, 1.0, 1.0, 1.0]))
        parent.addChildNode(makeBoxNode(tx: x, ty: 0.70, tz: z, w: 0.1, h: 0.2, d: 0.1, color: [1.0, 0.4, 0.0, 1.0]))
    }
}

enum ViewportCameraRole {
    case main
    case fpvPip
    case zoomPip
}

/// SwiftUI 3D 模擬視窗包裝器 (支援 120Hz ProMotion 與多重子母畫面鏡頭)
struct SimulationSceneView: UIViewRepresentable {
    let role: ViewportCameraRole

    func makeUIView(context: Context) -> SCNView {
        let scnView = SCNView()
        let coord = SimulationSceneCoordinator.shared
        scnView.scene = coord.scene
        scnView.antialiasingMode = .multisampling4X
        scnView.preferredFramesPerSecond = 120
        scnView.isPlaying = true
        scnView.rendersContinuously = true

        switch role {
        case .main:
            scnView.pointOfView = coord.mainCameraNode
            scnView.delegate = context.coordinator
        case .fpvPip:
            scnView.pointOfView = coord.fpvCameraNode
        case .zoomPip:
            scnView.pointOfView = coord.zoomCameraNode
        }
        return scnView
    }

    func updateUIView(_ uiView: SCNView, context: Context) {
        let coord = SimulationSceneCoordinator.shared
        switch role {
        case .main: uiView.pointOfView = coord.mainCameraNode
        case .fpvPip: uiView.pointOfView = coord.fpvCameraNode
        case .zoomPip: uiView.pointOfView = coord.zoomCameraNode
        }
    }

    func makeCoordinator() -> RenderLoopBridge {
        RenderLoopBridge()
    }

    final class RenderLoopBridge: NSObject, SCNSceneRendererDelegate {
        func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
            let scnView = renderer as? SCNView
            DispatchQueue.main.async {
                SimulationSceneCoordinator.shared.stepSimulation(in: scnView)
            }
        }
    }
}
