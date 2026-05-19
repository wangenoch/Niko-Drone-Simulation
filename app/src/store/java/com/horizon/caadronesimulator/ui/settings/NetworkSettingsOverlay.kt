package com.horizon.caadronesimulator.ui.settings

import androidx.compose.runtime.Composable

/** [v1.7.6] Store 版佔位符：上架版不支援 SITL 網路對接 */
@Composable
fun NetworkSettingsOverlay(
    host: String,
    port: Int,
    protocol: String,
    onDismiss: () -> Unit,
    onSave: (String, Int, String) -> Unit
) {
    // No-op for store version
}
