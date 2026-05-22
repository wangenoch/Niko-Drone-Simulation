package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.DronePhysicsState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * [v1.7.7] 核心物理引擎護衛測試 (Core Physics Protection Test)
 * 職責：確保在清理「殭屍代碼」時，不傷及核心飛行公式。
 */
class PhysicsEngineTest {

    private val atmos = PhysicsEngine.AtmosConfig(
        windLevel = 0, windDirection = "無", windVariation = 0, windDirVariation = 0,
        enableVerticalDraft = false, useFlightLimit = true, randomWindPhase = 0f,
        turbulencePhase = 0f, randomDirAngle = 0f, applyPhysicalSpecs = false,
        isMotorLocked = false, useHardcore = false, useStrictLanding = true, showObstacles = false
    )

    @Test
    fun testTakeoffThrust() {
        val state = DronePhysicsState(posY = 0.1f) // 假設初始高度
        val input = PhysicsEngine.ControlInput(throttle = 1.0f, yaw = 0f, pitch = 0f, roll = 0f)
        
        // 執行 10 幀 (每幀 16ms)
        repeat(10) {
            PhysicsEngine.step(0.016f, state, input, atmos, "QUAD_STANDARD")
        }
        
        // 滿油門後，高度必須增加
        assertTrue("起飛失敗：高度未增加 (目前: ${state.posY})", state.posY > 0.1f)
        assertTrue("動力不足：垂直速度應為正 (目前: ${state.velY})", state.velY > 0f)
    }

    @Test
    fun testHorizontalMovement() {
        val state = DronePhysicsState(posY = 2.0f) // 已在空中
        // 1. 測試俯仰 (Pitch)：推桿向前 (Pitch=-1.0)，位移應發生在 +Z 軸 (向前)
        // [修正] 原本邏輯中 Pitch 負值是低頭前進
        val inputP = PhysicsEngine.ControlInput(throttle = 0.5f, yaw = 0f, pitch = -1.0f, roll = 0f)
        
        repeat(20) {
            PhysicsEngine.step(0.016f, state, inputP, atmos, "QUAD_STANDARD")
        }
        
        assertTrue("俯仰位移失敗：Z 座標應變動 (目前: ${state.posZ})", abs(state.posZ) > 0.01f)

        // 2. [v1.7.7 核心同步] 測試橫滾 (Roll) 極性：打桿向右 (Roll=1.0) 應向 +X 移動 (Right)
        val stateR = DronePhysicsState(posY = 2.0f)
        val inputR = PhysicsEngine.ControlInput(throttle = 0.5f, yaw = 0f, pitch = 0f, roll = 1.0f)
        
        repeat(20) {
            PhysicsEngine.step(0.016f, stateR, inputR, atmos, "QUAD_STANDARD")
        }
        
        // 如果打桿向右 (1.0) 卻向左飛 (-X)，此測試會抓到極性錯誤。目前代碼應保證 +1.0 產生正向加速。
        assertTrue("橫滾極性錯誤：Roll=+1.0 應產生 +X 移動 (目前: ${stateR.posX})", stateR.posX > 0.01f)
    }

    @Test
    fun testWindDriftPolarity() {
        // [v1.7.7 核心同步] 測試風力極性：東風 (From East, Flow West) 應將飛機推向 -X
        val state = DronePhysicsState(posY = 2.0f)
        val eastWindAtmos = atmos.copy(
            windLevel = 4, 
            windDirection = com.horizon.caadronesimulator.model.AppConfig.WIND_DIR_E
        )
        val input = PhysicsEngine.ControlInput(throttle = 0.5f, yaw = 0f, pitch = 0f, roll = 0f)
        
        repeat(20) {
            PhysicsEngine.step(0.016f, state, input, eastWindAtmos, "QUAD_STANDARD")
        }
        
        assertTrue("風力極性錯誤：東風應將飛機推向 -X (目前: ${state.posX})", state.posX < 0f)
    }

    @Test
    fun testBatteryDepletion() {
        // 初始狀態：滿電
        val state = DronePhysicsState(posY = 5.0f, batteryVoltage = 4.2f, batteryPercent = 100)
        val input = PhysicsEngine.ControlInput(0f, 0f, 0f, 0f)
        
        // 模擬長時間過去 (例如 20 分鐘，足以耗盡所有機型電量)
        repeat(20) {
            PhysicsEngine.step(60f, state, input, atmos, "QUAD_STANDARD")
        }
        
        assertTrue("電池模擬失敗：百分比應降低 (目前: ${state.batteryPercent})", state.batteryPercent < 100)
        assertTrue("電池耗盡失敗：最終電量應歸零 (目前: ${state.batteryPercent})", state.batteryPercent <= 0)
        assertTrue("電量熔斷失敗：電壓不應低於截止值 3.2V (目前: ${state.batteryVoltage})", state.batteryVoltage >= 3.19f)
    }

    @Test
    fun testMotorLockSafety() {
        val state = DronePhysicsState(posY = 5.0f, velY = 10f)
        val lockedAtmos = atmos.copy(isMotorLocked = true)
        val input = PhysicsEngine.ControlInput(1f, 0f, 0f, 0f)
        
        PhysicsEngine.step(0.016f, state, input, lockedAtmos, "QUAD_STANDARD")
        
        assertEquals("安全鎖失效：垂直速度未歸零", 0f, state.velY)
        assertEquals("安全鎖失效：高度未重置為地面", 0.1f, state.posY, 0.05f) // 假設 groundOffset 附近
    }
}
