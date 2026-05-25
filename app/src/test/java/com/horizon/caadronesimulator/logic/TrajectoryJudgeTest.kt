package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.DronePhysicsState
import org.junit.Assert.*
import org.junit.Test

class TrajectoryJudgeTest {

    private val atmos = PhysicsEngine.AtmosConfig(
        windLevel = 0, windDirection = "NONE", windVariation = 0, windDirVariation = 0,
        enableVerticalDraft = false, useFlightLimit = true, randomWindPhase = 0f,
        turbulencePhase = 0f, randomDirAngle = 0f, applyPhysicalSpecs = false,
        isMotorLocked = false, useHardcore = false, useStrictLanding = true, showObstacles = false
    )

    @Test
    fun testCrashWithHighRateInterference() {
        // 模擬砸地情境：高度 0.12m (低於地面 0.08m), 速度 -2.6m/s
        // 上一幀高度 0.12m, 速度 -2.6m/s, 下一幀位移預測：0.12 + (-2.6 * 0.016) = 0.0784m (穿透 0.08m)
        val state = DronePhysicsState(posY = 0.12f, velY = -2.6f)
        
        // 模擬玩家干預：瘋狂推油門 (Throttle=1.0)，試圖抵消砸地
        val input = PhysicsEngine.ControlInput(throttle = 1.0f, yaw = 0f, pitch = 0f, roll = 0f)
        
        // 執行一步物理
        val result = PhysicsEngine.step(0.016f, state, input, atmos, "QUAD_STANDARD")
        
        // 驗證：判官應無視油門干預，100% 判定損毀
        assertTrue("彩票機制依然存在：高油門干預下未能觸發損毀", result.isImpact)
        assertEquals("判定訊息錯誤", "CRASH_STRUCTURAL", result.systemMessage)
        assertEquals("飛機位置未強制修正至地面", 0.08f, state.posY, 0.01f)
    }

    @Test
    fun testTakeoffIsNeverBlocked() {
        // 模擬地面起飛：高度 0.08m, 速度 0
        val state = DronePhysicsState(posY = 0.08f, velY = 0f)
        val input = PhysicsEngine.ControlInput(throttle = 1.0f, yaw = 0f, pitch = 0f, roll = 0f)
        
        val result = PhysicsEngine.step(0.016f, state, input, atmos, "QUAD_STANDARD")
        
        // 驗證：起飛不應被判為撞擊，且高度必須增加
        assertFalse("起飛被誤判為撞擊", result.isImpact)
        assertTrue("起飛動力被釘死在地面 (高度未增加: ${state.posY})", state.posY > 0.08f)
    }

    @Test
    fun testStationaryDownwardPressureDoesNotCrash() {
        // 模擬落地後壓桿：高度 0.08m, 速度 0
        val state = DronePhysicsState(posY = 0.08f, velY = 0f)
        val input = PhysicsEngine.ControlInput(throttle = -1.0f, yaw = 0f, pitch = 0f, roll = 0f)
        
        val result = PhysicsEngine.step(0.016f, state, input, atmos, "QUAD_STANDARD")
        
        // 驗證：落地壓桿不應損毀
        assertFalse("落地後壓桿觸發了損毀", result.isImpact)
    }
}
