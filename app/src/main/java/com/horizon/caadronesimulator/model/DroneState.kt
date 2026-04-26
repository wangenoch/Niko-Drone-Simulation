package com.horizon.caadronesimulator.model

data class ChannelMapping(
    val axis: Int = -1,
    val inverted: Boolean = false,
    val label: String = "未設定",
    val min: Float = -1f,
    val max: Float = 1f,
    val center: Float = 0f
)

data class DroneState(
    val altitude: Float = 0f,
    val posX: Float = 0f,
    val posZ: Float = -6.0f,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val speed: Float = 0f,
    val isMotorLocked: Boolean = true,
    val isCollision: Boolean = false,
    val isMuted: Boolean = true,
    val showShadow: Boolean = true,
    val shadowIntensity: Float = 0.5f,
    val showObstacles: Boolean = false,
    val windLevel: Int = 0,
    val windDirection: String = "無",
    val windVariation: Int = 0,
    val windDirVariation: Int = 0, 
    val timeOfDay: String = "中午",
    val cameraMode: String = "站位視角 (追蹤)",
    val showStatusBar: Boolean = false,
    val pauseInSettings: Boolean = true,
    val applyPhysicalSpecs: Boolean = false,
    val showTutorial: Boolean = true,
    val showJoystickTutorial: Boolean = false,
    val showClimateTutorial: Boolean = false,
    val controllerConnected: Boolean = false,
    val showVirtualJoysticks: Boolean = false,
    val zoomFactor: Float = 1.5f,
    val joystickMode: Int = 2, // 1: 日本手, 2: 美國手, 3: 中國手
    val droneType: String = "QUAD_STANDARD", 
    val showSettings: Boolean = false,
    val settingsTab: Int = 0, // 0: 搖桿, 1: 環境, 2: 機型
    val showJoystickSettings: Boolean = false, // Keep for compatibility if needed or replace
    val showClimateSettings: Boolean = false,
    val showDroneSelection: Boolean = false,
    val showJoystickCalibration: Boolean = false,
    val isCalibrating: Boolean = false,
    val calibrationStep: Int = 0, 
    
    // 改為「位置型映射」：綁定實體搖桿的四個物理位置
    val mappingLY: ChannelMapping = ChannelMapping(-1, false, "左垂直"),
    val mappingLX: ChannelMapping = ChannelMapping(-1, false, "左水平"),
    val mappingRY: ChannelMapping = ChannelMapping(-1, false, "右垂直"),
    val mappingRX: ChannelMapping = ChannelMapping(-1, false, "右水平"),
    
    val isAutoBinding: String? = null,
    val setupWizardStep: Int = 0,
    val wizardWaitingForNeutral: Boolean = false,
    // val wizardLastAxis: Int = -1, // 舊版未使用
    // val wizardLastStepTime: Long = 0L, // 舊版未使用
    val useRawMapping: Boolean = false,
    val halfThrottle: Boolean = false,
    val joystickDeadzone: Float = 0.15f,
    val activeAxisLabel: String = "NONE",
    val infoMessage: String? = null,
    val wizardCountdown: Int = 0,

    // 實體搖桿原始物理值 (-1.0 to 1.0, 向上/向右為正)
    val rawLY: Float = 0f,
    val rawLX: Float = 0f,
    val rawRY: Float = 0f,
    val rawRX: Float = 0f,
    
    val cameraTilt: Float = 0f 
) {
    // 根據 Mode 決定各功能對應的物理軸
    // Mode 1 (日本): 左(Yaw, Pitch), 右(Roll, Throttle)
    // Mode 2 (美國): 左(Yaw, Throttle), 右(Roll, Pitch)
    // Mode 3 (中國): 左(Roll, Pitch), 右(Yaw, Throttle)
    
    val physRawThrottle: Float get() = when(joystickMode) { 1 -> rawRY; 3 -> rawRY; else -> rawLY }
    val physRawYaw: Float get() = when(joystickMode) { 3 -> rawRX; else -> rawLX }
    val physRawPitch: Float get() = when(joystickMode) { 1 -> rawLY; 3 -> rawLY; else -> rawRY }
    val physRawRoll: Float get() = when(joystickMode) { 3 -> rawLX; else -> rawRX }

    // 獲取各功能的映射設定（用於檢查 Invert 狀態）
    private val mapT get() = when(joystickMode) { 1 -> mappingRY; 3 -> mappingRY; else -> mappingLY }
    private val mapY get() = when(joystickMode) { 3 -> mappingRX; else -> mappingLX }
    private val mapP get() = when(joystickMode) { 1 -> mappingLY; 3 -> mappingLY; else -> mappingRY }
    private val mapR get() = when(joystickMode) { 3 -> mappingLX; else -> mappingRX }

    // 最終飛行指令 (套用反相)
    val stickThrottle: Float get() = if (mapT.inverted) -physRawThrottle else physRawThrottle
    val stickYaw: Float get() = if (mapY.inverted) -physRawYaw else physRawYaw
    val stickPitch: Float get() = if (mapP.inverted) -physRawPitch else physRawPitch
    val stickRoll: Float get() = if (mapR.inverted) -physRawRoll else physRawRoll

    // HUD 視覺位置 (直接對應物理位置)
    val stickLX: Float get() = if (mappingLX.inverted) -rawLX else rawLX
    val stickLY: Float get() = if (mappingLY.inverted) -rawLY else rawLY
    val stickRX: Float get() = if (mappingRX.inverted) -rawRX else rawRX
    val stickRY: Float get() = if (mappingRY.inverted) -rawRY else rawRY
}
