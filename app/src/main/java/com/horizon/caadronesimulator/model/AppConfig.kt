package com.horizon.caadronesimulator.model

/**
 * 全域應用程式配置
 * 集中管理版本號、日期等資訊，方便維護。
 */
object AppConfig {
    const val CURRENT_VERSION = "1.6.0"
    const val RELEASE_DATE = "2026-05"
    const val DEVELOPER = "Enoch Wang"

    const val SPECIAL_TITLE = "NikoNiko考照場地模擬器"

    val SPECIAL_THANKS = listOf(
        "測試與建議：全體考照班教官與學員"
    )

    const val ADMIN_PASSWORD = "12345678"
}
