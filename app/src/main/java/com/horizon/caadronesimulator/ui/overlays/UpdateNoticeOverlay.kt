package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.ui.theme.NikoTheme
import com.horizon.caadronesimulator.util.ReadmeParser

@Composable
fun UpdateNoticeOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val updateData = remember { ReadmeParser.parseUpdateNotice() }
    val themeColors = NikoTheme.colors
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background.copy(alpha = 0.8f))
            .clickable(enabled = false) {}
            .zIndex(2000f),
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberScrollState()
        
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f),
            color = themeColors.panel,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, themeColors.divider)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 內容區域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp)
                ) {
                    if (updateData != null) {
                        // 1. 完整標題 (保留 test/oba 等後綴)
                        Text(
                            text = updateData.fullTitle,
                            color = themeColors.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 2. 前言/概述文字 (Intro)
                        if (updateData.intro.isNotEmpty()) {
                            Text(
                                text = updateData.intro,
                                color = themeColors.textPrimary.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(bottom = 20.dp)
                            )
                        }
                        
                        // 3. 功能更新區塊
                        updateData.features.forEach { feature ->
                            UpdateSection(feature.title, feature.items)
                        }

                        // 4. 已知問題
                        if (updateData.knownIssues.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.notice_known_issues),
                                color = themeColors.warning,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            updateData.knownIssues.forEach { issue ->
                                Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                                    Text("• ", color = themeColors.warning, fontSize = 13.sp)
                                    Text(
                                        text = issue,
                                        color = themeColors.textSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }

                        // 5. 特別感謝 (合併 README內容與 AppConfig 全域清單)
                        val allSpecialThanks = (updateData.specialThanks + AppConfig.SPECIAL_THANKS).distinct()
                        
                        if (allSpecialThanks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.notice_special_thanks),
                                color = themeColors.accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            allSpecialThanks.forEach { thanks ->
                                Text(
                                    text = thanks,
                                    color = themeColors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                )
                            }
                        }
                    } else {
                        // 解析失敗時的 Fallback
                        Text(
                            text = stringResource(R.string.notice_title_update),
                            color = themeColors.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = stringResource(R.string.notice_intro_error),
                            color = themeColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }

                // 底部滾動提示漸層
                val isAtBottom by remember { derivedStateOf { scrollState.value >= scrollState.maxValue } }
                if (!isAtBottom) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, themeColors.panel)
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(stringResource(R.string.notice_scroll_hint), color = themeColors.textPrimary.copy(alpha = 0.5f), fontSize = 10.sp)
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                null,
                                tint = themeColors.textPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 右上角關閉按鈕
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(themeColors.textPrimary.copy(alpha = 0.1f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = themeColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateSection(title: String, items: List<String>) {
    val themeColors = NikoTheme.colors
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            color = themeColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        items.forEach { item ->
            Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                Text("• ", color = themeColors.primary, fontSize = 13.sp)
                Text(
                    text = item,
                    color = themeColors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
