package com.horizon.caadronesimulator.ui.theme

import com.horizon.caadronesimulator.model.AppConfig

/**
 * [v1.7.8] 主題註冊表 - 統一管理可用主題
 */
object ThemeRegistry {
    private val THEMES = listOf(
        ClassicTheme,
        LightTheme
    )

    fun getAllThemes(): List<ThemeModule> = THEMES

    fun getTheme(id: String): ThemeModule {
        return THEMES.find { it.id == id } ?: ClassicTheme
    }

    fun getColors(id: String): NikoColors {
        return getTheme(id).colors
    }
}
