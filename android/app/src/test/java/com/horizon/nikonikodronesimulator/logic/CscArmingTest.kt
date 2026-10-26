package com.horizon.nikonikodronesimulator.logic

import com.horizon.nikonikodronesimulator.model.ChannelMapping
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.StickInputState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v1.7.11] 內八解鎖 (CSC) 邏輯驗證測試
 * 職責：確保解鎖判定與 Rate 靈敏度完全解耦，且在各種模式下表現穩定。
 */
class CscArmingTest {

    @Test
    fun testCscWithLowRate() {
        val state = DroneState.getInstance()
        val stick = StickInputState()
        
        // 1. 設定極低的 Rate (0.5)，這在舊邏輯下會導致無法解鎖
        state.globalRate = 0.5f
        state.useGlobalRates = true
        state.joystickMode = 2 // 美國手
        
        // 2. 設置預設映射 (Mode 2)
        state.mappingLY = ChannelMapping(0, false, "Throttle")
        state.mappingLX = ChannelMapping(1, false, "Yaw")
        state.mappingRY = ChannelMapping(2, false, "Pitch")
        state.mappingRX = ChannelMapping(3, false, "Roll")
        
        // 3. 模擬物理搖桿打到「內八」位置 (-1.0 或 1.0)
        // 左搖桿右下: Throttle=-1.0, Yaw=1.0
        // 右搖桿左下: Pitch=-1.0, Roll=-1.0
        stick.setChannel(0, -1.0f) // Throttle
        stick.setChannel(1, 1.0f)  // Yaw
        stick.setChannel(2, -1.0f) // Pitch
        stick.setChannel(3, -1.0f) // Roll
        
        // 4. 驗證 Raw 行程是否正確 (應無視 0.5 的 Rate)
        val rawT = stick.rawThrottle(state)
        val rawY = stick.rawYaw(state)
        val rawP = stick.rawPitch(state)
        val rawR = stick.rawRoll(state)
        
        assertTrue("油門原始行程應為 -1.0 (無視 Rate)", rawT < -0.9f)
        assertTrue("航向原始行程應為 1.0", rawY > 0.9f)
        assertTrue("俯仰原始行程應為 -1.0", rawP < -0.9f)
        assertTrue("橫滾原始行程應為 -1.0", rawR < -0.9f)
        
        // 5. 執行解鎖判定式
        val isCSC = (rawT < -0.7f && rawY > 0.7f && rawP < -0.7f && rawR < -0.7f)
        assertTrue("內八解鎖失敗：即使在低 Rate 下，原始行程也應能觸發解鎖", isCSC)
    }

    @Test
    fun testCscWithInversion() {
        val state = DroneState.getInstance()
        val stick = StickInputState()
        
        state.joystickMode = 2
        
        // 模擬「油門反轉」的情境（某些偷工減料搖桿的需求）
        // 原本向下是 1.0，反轉後向下變為 -1.0
        state.mappingLY = ChannelMapping(0, true, "Throttle Inverted")
        state.mappingLX = ChannelMapping(1, false, "Yaw")
        state.mappingRY = ChannelMapping(2, false, "Pitch")
        state.mappingRX = ChannelMapping(3, false, "Roll")
        
        // 模擬物理搖桿向下打。因為開了 Inversion，物理輸入 1.0 會被處理成 -1.0
        stick.setChannel(0, 1.0f) 
        
        val rawT = stick.rawThrottle(state)
        assertTrue("反轉後的油門行程判定應正確對位為負值", rawT < -0.9f)
    }
}
