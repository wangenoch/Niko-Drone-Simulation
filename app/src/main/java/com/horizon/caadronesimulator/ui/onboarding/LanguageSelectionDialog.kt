package com.horizon.caadronesimulator.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.7.7] 首次啟動語言選擇對話框 (First Launch Language Selector)
 */
@Composable
fun LanguageSelectionDialog(
    onLanguageSelected: (String) -> Unit
) {
    val themeColors = NikoTheme.colors
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .zIndex(100f), // 強制置頂
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(24.dp),
            color = themeColors.panel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, themeColors.primary.copy(0.3f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome / 歡迎",
                    color = themeColors.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Choose your language\n請選擇語言",
                    color = themeColors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 語言選項按鈕
                LanguageOptionButton(
                    label = "繁體中文",
                    onClick = { onLanguageSelected("zh") }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LanguageOptionButton(
                    label = "English",
                    onClick = { onLanguageSelected("en") }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "You can change this anytime in settings\n可在設定中隨時更改",
                    color = themeColors.textSecondary.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LanguageOptionButton(
    label: String,
    onClick: () -> Unit
) {
    val themeColors = NikoTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp), // 增加高度從 48dp 到 54dp
        colors = ButtonDefaults.buttonColors(
            containerColor = themeColors.primary,
            contentColor = if (themeColors.isLight) Color.White else Color.Black
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label, 
            fontSize = 16.sp, // 稍微加大字體
            fontWeight = FontWeight.ExtraBold, // 加粗
            letterSpacing = 2.sp // 增加字距讓中文更透氣
        )
    }
}
