package com.horizon.nikonikodronesimulator.logic

import com.horizon.nikonikodronesimulator.model.ChannelMapping
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.StickInputState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v1.7.11] 內八解鎖極限參數驗證測試
 * 職責：驗證在 Rate 極大 (5.0) 或極小 (0.1) 的情況下，解鎖判定是否依然 100% 穩定。
 */
class CscRateExtremeTest {

    private fun setupMode2(state: DroneState) {
        state.joystickMode = 2
        state.mappingLY = ChannelMapping(0, false, "Throttle")
        state.mappingLX = ChannelMapping(1, false, "Yaw")
        state.mappingRY = ChannelMapping(2, false, "Pitch")
        state.mappingRX = ChannelMapping(3, false, "Roll")
    }

    private fun simulateInternalEight(stick: StickInputState) {
        // 左手：右下 (T=-1, Y=1)
        stick.setChannel(0, -1.0f)
        stick.setChannel(1, 1.0f)
        // 右手：左下 (P=-1, R=-1)
        stick.setChannel(2, -1.0f)
        stick.setChannel(3, -1.0f)
    }

    @Test
    fun testCscWithMinimumRate() {
        val state = DroneState.getInstance()
        val stick = StickInputState()
        setupMode2(state)
        
        // 1. 設定極小靈敏度 (0.1)
        state.globalRate = 0.1f
        state.useGlobalRates = true
        
        simulateInternalEight(stick)
        
        // 2. 讀取數據
        val rawT = stick.rawThrottle(state)
        val rawY = stick.rawYaw(state)
        val rawP = stick.rawPitch(state)
        val rawR = stick.rawRoll(state)
        
        // 3. 驗證
        val isCSC = (rawT < -0.7f && rawY > 0.7f && rawP < -0.7f && rawR < -0.7f)
        assertTrue("極低靈敏度 (0.1) 下解鎖失敗：原始行程應無視靈敏度縮放", isCSC)
    }

    @Test
    fun testCscWithMaximumRate() {
        val state = DroneState.getInstance()
        val stick = StickInputState()
        setupMode2(state)
        
        // 1. 設定極大靈敏度 (5.0)
        state.globalRate = 5.0f
        state.useGlobalRates = true
        
        simulateInternalEight(stick)
        
        // 2. 讀取數據
        val rawT = stick.rawThrottle(state)
        val rawY = stick.rawYaw(state)
        val rawP = stick.rawPitch(state)
        val rawR = stick.rawRoll(state)
        
        // 3. 驗證
        val isCSC = (rawT < -0.7f && rawY > 0.7f && rawP < -0.7f && rawR < -0.7f)
        assertTrue("極高靈敏度 (5.0) 下解鎖失敗：原始行程應能抑制溢出干擾", isCSC)
    }
}
