package com.horizon.nikonikodronesimulator.ui.theme

/**
 * [v1.7.7] 主題註冊表 - 統一管理可用主題
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
