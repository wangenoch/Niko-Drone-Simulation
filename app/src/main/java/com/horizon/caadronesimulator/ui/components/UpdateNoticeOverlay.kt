package com.horizon.caadronesimulator.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.util.ReadmeParser

@Composable
fun UpdateNoticeOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val updateData = remember { ReadmeParser.parseUpdateNotice() }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(enabled = false) {}
            .zIndex(2000f),
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberScrollState()
        
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f),
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f))
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
                            color = Color.Cyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 2. 前言/概述文字 (Intro)
                        if (updateData.intro.isNotEmpty()) {
                            Text(
                                text = updateData.intro,
                                color = Color.White.copy(alpha = 0.8f),
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
                                text = "⚠️ 已知問題",
                                color = Color(0xFFFF9800),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            updateData.knownIssues.forEach { issue ->
                                Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                                    Text("• ", color = Color(0xFFFF9800), fontSize = 13.sp)
                                    Text(
                                        text = issue,
                                        color = Color.LightGray,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }

                        // 5. 特別感謝
                        if (updateData.specialThanks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "🎁 特別感謝 (Special Thanks)",
                                color = Color(0xFFE1BEE7),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            updateData.specialThanks.forEach { thanks ->
                                Text(
                                    text = thanks,
                                    color = Color.LightGray,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                )
                            }
                        }
                    } else {
                        // 解析失敗時的 Fallback
                        Text(
                            text = "🚀 功能升級報告",
                            color = Color.Cyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "無法載入更新細節，請參閱專案根目錄 README.md 檔案。",
                            color = Color.Gray,
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
                                    colors = listOf(Color.Transparent, Color(0xFF1A1A1A))
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("向下捲動查看更多", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                null,
                                tint = Color.White.copy(alpha = 0.5f),
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
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "關閉",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateSection(title: String, items: List<String>) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        items.forEach { item ->
            Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                Text("• ", color = Color.Cyan, fontSize = 13.sp)
                Text(
                    text = item,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
