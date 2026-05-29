package com.horizon.nikonikodronesimulator.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.nikonikodronesimulator.ui.theme.NikoTheme

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.text.style.TextAlign

/**
 * [v1.7.15] 首次啟動語言選擇對話框 - 下拉選單升級版
 */
@Composable
fun LanguageSelectionDialog(
    onLanguageSelected: (String) -> Unit
) {
    val themeColors = NikoTheme.colors
    var expanded by remember { mutableStateOf(false) }
    
    // 預設跟隨系統語系
    val sysLang = java.util.Locale.getDefault().language
    val initialLang = if (sysLang.startsWith("zh")) "zh" else "en"
    var selectedLang by remember { mutableStateOf(initialLang) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .zIndex(100f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(24.dp),
            color = themeColors.panel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, themeColors.primary.copy(0.3f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val titleText = when(selectedLang) {
                    "zh" -> "歡迎使用"
                    "ja" -> "ようこそ"
                    else -> "Welcome"
                }
                Text(
                    text = titleText,
                    color = themeColors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val subtitleText = when(selectedLang) {
                    "zh" -> "請選擇您的慣用語系"
                    "ja" -> "言語を選択してください"
                    else -> "Please select your language"
                }
                Text(
                    text = subtitleText,
                    color = themeColors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // --- 下拉式選單組件 ---
                Box {
                    Surface(
                        color = themeColors.textPrimary.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, themeColors.divider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { expanded = true }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val label = when(selectedLang) {
                                "zh" -> "繁體中文 (Traditional Chinese)"
                                "ja" -> "日本語 (Japanese)"
                                else -> "English (Global)"
                            }
                            Text(label, color = themeColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.ArrowDropDown, null, tint = themeColors.primary)
                        }
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(themeColors.panel).width(292.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("繁體中文 (Traditional Chinese)", color = themeColors.textPrimary) },
                            onClick = { selectedLang = "zh"; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("日本語 (Japanese)", color = themeColors.textPrimary) },
                            onClick = { selectedLang = "ja"; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("English (Global)", color = themeColors.textPrimary) },
                            onClick = { selectedLang = "en"; expanded = false }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // 確認進入按鈕
                Button(
                    onClick = { onLanguageSelected(selectedLang) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val btnText = when(selectedLang) {
                        "zh" -> "開始飛行"
                        "ja" -> "フライト開始"
                        else -> "Start Flight"
                    }
                    Text(
                        text = btnText,
                        color = if (themeColors.isLight) Color.White else Color.Black,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val footerText = when(selectedLang) {
                    "zh" -> "可在設定選單中隨時更改"
                    "ja" -> "設定からいつでも変更可能です"
                    else -> "Change anytime in settings"
                }
                Text(
                    text = footerText,
                    color = themeColors.textSecondary.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
