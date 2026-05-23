package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.DronePhysicsState
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
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
        // 1. 測試俯仰 (Pitch)：推桿向前 (Pitch=+1.0)，位移應發生在 +Z 軸 (向前)
        val inputP = PhysicsEngine.ControlInput(throttle = 0.5f, yaw = 0f, pitch = 1.0f, roll = 0f)
        
        repeat(20) {
            PhysicsEngine.step(0.016f, state, inputP, atmos, "QUAD_STANDARD")
        }
        
        // 驗證向前位移 (+Z)
        assertTrue("俯仰位移極性錯誤：向前推桿應產生正向 Z 位移 (目前: ${state.posZ})", state.posZ > 0f)

        // 2. [v1.7.9.9 FINAL] 測試橫滾 (Roll) 極性：打桿向右 (Roll=1.0) 應產生視覺向右位移
        // 注意：根據最新對位，視覺向右對應物理 -X 軸
        val stateR = DronePhysicsState(posY = 2.0f)
        val inputR = PhysicsEngine.ControlInput(throttle = 0.5f, yaw = 0f, pitch = 0f, roll = 1.0f)
        
        repeat(20) {
            PhysicsEngine.step(0.016f, stateR, inputR, atmos, "QUAD_STANDARD")
        }
        
        // 驗證視覺向右位移 (物理 -X)
        assertTrue("橫滾位移極性錯誤：向右打桿應產生視覺向右位移 (目前 posX: ${stateR.posX})", stateR.posX < 0f)

        // 3. [v1.7.9 Debug] 測試 90 度轉向後的位移：Yaw=90 (面向右方)，向前推桿應產生 +X 位移
        val stateY90 = DronePhysicsState(posY = 2.0f, yaw = 90f)
        val inputP90 = PhysicsEngine.ControlInput(throttle = 0.5f, yaw = 0f, pitch = 1.0f, roll = 0f)

        repeat(20) {
            PhysicsEngine.step(0.016f, stateY90, inputP90, atmos, "QUAD_STANDARD")
        }
        
        assertTrue("轉向位移錯誤：Yaw=90 時向前推桿應向 +X 移動 (目前 posX: ${stateY90.posX})", stateY90.posX > 0.01f)
        assertTrue("轉向位移錯誤：Yaw=90 時向前推桿不應有顯著 Z 位移 (目前 posZ: ${stateY90.posZ})", abs(stateY90.posZ) < 0.01f)
    }

    @Test
    fun testIntentArbitration() {
        val state = DroneState.getInstance()
        val stick = StickInputState()
        
        // 1. 模擬實體搖桿靜止
        stick.setChannel(102, 0.5f) // 假設為 Pitch 實體軸
        
        // 2. 模擬觸控接管
        stick.isTouchingRight = true
        stick.touchRY = 0.8f
        
        // 此處 resolveValue 應選取觸控數據
        // (註：resolveValue 為私有，此處透過公開接口驗證)
        val cmd = stick.stickPitch(state)
        assertTrue("實體靜止時應由觸控接管", cmd != 0f)
    }

    @Test
    fun testWindDriftPolarity() {
        // [v1.7.9.12 FINAL] 測試風力極性：東風 (From East, Flow West) 應將飛機視覺推向左
        // 在當前坐標系下，視覺向左對應物理 +X 軸
        val state = DronePhysicsState(posY = 2.0f)
        val eastWindAtmos = atmos.copy(
            windLevel = 4, 
            windDirection = com.horizon.caadronesimulator.model.AppConfig.WIND_DIR_E
        )
        val input = PhysicsEngine.ControlInput(throttle = 0.5f, yaw = 0f, pitch = 0f, roll = 0f)
        
        repeat(20) {
            PhysicsEngine.step(0.016f, state, input, eastWindAtmos, "QUAD_STANDARD")
        }
        
        assertTrue("風力極性錯誤：東風應將飛機視覺推向左 (目前 posX: ${state.posX})", state.posX > 0f)

        // [v1.7.7-WIN-STABLE] 測試北風：北風 (From North, Flow South) 應將飛機推向 -Z (向後)
        val northState = DronePhysicsState(posY = 2.0f)
        val northWindAtmos = atmos.copy(
            windLevel = 4, 
            windDirection = com.horizon.caadronesimulator.model.AppConfig.WIND_DIR_N
        )
        
        repeat(20) {
            PhysicsEngine.step(0.016f, northState, input, northWindAtmos, "QUAD_STANDARD")
        }
        
        assertTrue("風力極性錯誤：北風應將飛機推向 -Z (目前: ${northState.posZ})", northState.posZ < 0f)
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
