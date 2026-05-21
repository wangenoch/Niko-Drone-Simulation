package com.horizon.caadronesimulator.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.ui.theme.ThemeRegistry
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.7.8] 主題選擇分頁 - 與機型選擇保持一致
 */
@Composable
fun ThemeSelectionScreen(
    currentThemeId: String,
    onThemeSelected: (String) -> Unit
) {
    val config = LocalConfiguration.current
    val isSmallDevice = config.screenWidthDp < 500 && config.screenHeightDp < 300
    val themes = ThemeRegistry.getAllThemes()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            themes.forEach { theme ->
                ThemeCard(
                    title = stringResource(theme.nameRes),
                    themeId = theme.id,
                    isSelected = currentThemeId == theme.id,
                    onClick = { onThemeSelected(theme.id) },
                    renderIcon = { mod, sel -> theme.RenderIcon(mod, sel) }
                )
            }
        }
        
        if (!isSmallDevice) {
            Surface(
                color = NikoTheme.colors.textPrimary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    val currentTheme = ThemeRegistry.getTheme(currentThemeId)
                    
                    Text(
                        text = stringResource(R.string.settings_theme_detail_title, stringResource(currentTheme.nameRes)),
                        color = NikoTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(currentTheme.descriptionRes),
                        color = NikoTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun RowScope.ThemeCard(
    title: String,
    themeId: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    renderIcon: @Composable (Modifier, Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(140.dp)
            .background(
                if (isSelected) NikoTheme.colors.primary.copy(alpha = 0.2f) else NikoTheme.colors.surface.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .border(
                2.dp,
                if (isSelected) NikoTheme.colors.primary else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(if (isSelected) NikoTheme.colors.primary else NikoTheme.colors.textSecondary.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                renderIcon(Modifier.size(36.dp), isSelected)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = NikoTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
            if (isSelected) {
                Text(stringResource(R.string.drone_selection_current), color = NikoTheme.colors.primary, fontSize = 10.sp)
            }
        }
    }
}
