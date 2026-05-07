package com.horizon.caadronesimulator.model

object Constants {
    val CONE_POSITIONS = arrayOf(
        floatArrayOf(-6f, 6f), floatArrayOf(-12f, 0f), floatArrayOf(-6f, -6f),
        floatArrayOf(0f, 0f),
        floatArrayOf(6f, 6f), floatArrayOf(12f, 0f), floatArrayOf(6f, -6f)
    )

    // [v1.3.9] 擴大後的 CAA 高級場地規格
    const val FIELD_WIDTH_HALF = 50f  // 總寬 100m (-50 to 50)
    const val FIELD_Z_FRONT = 40f     // 前方空域 40m
    const val FIELD_Z_BACK = -15f     // 後方限制 -15m (含站位線)
    const val WARNING_BUFFER = 5f     // 5公尺預警緩衝區
}
