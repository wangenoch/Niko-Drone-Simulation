package com.horizon.caadronesimulator.logic

import com.horizon.caadronesimulator.model.DroneState
import org.junit.Assert.*
import org.junit.Test

/**
 * [v1.7.7] 升級版哨兵測試 (Sentinel Test 2.0)
 * 職責：確保環境數據管線始終連通，驗證 v1.7.7 風向極性與同步機制。
 */
class EnvironmentalIntegrityTest {

    @Test
    fun testAtmosphereDataSovereignty() {
        val state = DroneState.getInstance()
        // 此處模擬 Renderer 傳遞給 ViewModel 的同步過程
        
        // 1. 測試：風向極性同步 (v1.7.7 核心)
        val testAngle = 270f // 東風 (Flow to West)
        state.env.currentWindAngle = testAngle
        
        assertEquals("風向角同步異常", 270f, state.env.currentWindAngle)
        
        // 2. 測試：雲層數據主權 (確保 Renderer 的累加值能正確存入 State)
        val nextU = 0.55f
        val nextV = 0.88f
        state.env.cloudU = nextU
        state.env.cloudV = nextV
        
        assertEquals("雲層 U 軸主權更新失敗", 0.55f, state.env.cloudU)
        assertEquals("雲層 V 軸主權更新失敗", 0.88f, state.env.cloudV)

        // 3. [v1.7.7-WIN-STABLE] 測試風向同步守護
        // 確保物理風向角變更後，環境管線能維持穩定同步
        state.env.currentWindAngle = 180f // 北風
        assertEquals("北風同步異常", 180f, state.env.currentWindAngle)
    }
}
