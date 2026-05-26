package com.horizon.caadronesimulator.model

/**
 * [v1.5.3] 全系統物理常數定義 (DATUM: H-Pad)
 * 基準點 (0,0,0) 為 H 坪起飛點中心。
 */
object Constants {
    // 物理角椎位置 (相對於 H 點)
    val CONE_POSITIONS = arrayOf(
        floatArrayOf(-6f, 12f),  // P1: 左前
        floatArrayOf(-12f, 6f),  // P2: 左中
        floatArrayOf(-6f, 0f),   // P3: 左後 (與 H 點平齊)
        floatArrayOf(0f, 6f),    // P4: 中心 (原座標系原點)
        floatArrayOf(6f, 12f),   // P5: 右前
        floatArrayOf(12f, 6f),   // P6: 右中
        floatArrayOf(6f, 0f)     // P7: 右後 (與 H 點平齊)
    )

    // 場地限制定義
    const val FIELD_WIDTH_HALF = AppConfig.SystemDefaults.FIELD_WIDTH_HALF
    const val FIELD_Z_FRONT = AppConfig.SystemDefaults.FIELD_Z_FRONT
    const val FIELD_Z_BACK = AppConfig.SystemDefaults.FIELD_Z_BACK
    const val WARNING_BUFFER = AppConfig.SystemDefaults.WARNING_BUFFER

    // [v1.5.9] 實體障礙物定義 [X, Z, 高度, 類型ID, 碰撞半徑]
    val OBSTACLES = arrayOf(
        floatArrayOf(-32f, 16f, 13f, 0f, 2.5f), 
        floatArrayOf(-28f, 31f, 8f, 2f, 1.8f),
        floatArrayOf(32f, 11f, 10f, 1f, 2.0f), 
        floatArrayOf(28f, 1f, 7f, 3f, 1.5f),
        floatArrayOf(35f, 26f, 11f, 2f, 1.8f)
    )
}
