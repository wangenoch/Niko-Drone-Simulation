package com.horizon.caadronesimulator.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [v1.2.68] 硬體連線控制組件
 * 整合了多狀態掃描、呼吸燈動畫與交互鎖定保護機制。
 */
@Composable
fun HardwareConnectionSection(
    inputMode: Int,
    isHardwareController: Boolean,
    isNetworkConnected: Boolean = false,
    connectionStatus: com.horizon.caadronesimulator.model.ConnectionStatus = com.horizon.caadronesimulator.model.ConnectionStatus.IDLE,
    isInteractionLocked: Boolean,
    serialByteCount: Int,
    infoMessage: String?,
    showHardwareMonitor: Boolean,
    onUpdateInputMode: (Int) -> Unit,
    onScanUsb: () -> Unit,
    onOpenNetworkSettings: () -> Unit = {}, // [v1.3.9]
    onToggleHardwareMonitor: (Boolean) -> Unit,
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    Surface(
        color = Color(0x0AFFFFFF),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 模式切換器 (外接/內置)
                Row(
                    modifier = Modifier
                        .background(Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                        .onGloballyPositioned { onTargetPositioned("input_mode", it.boundsInRoot()) }
                        .padding(2.dp)
                        .alpha(if (isInteractionLocked) 0.5f else 1f)
                ) {
                    listOf("外接", "內置", "網路").forEachIndexed { index, label ->
                        val isAvailable = when(index) {
                            1 -> isHardwareController
                            else -> true
                        }
                        val isSelected = inputMode == index
                        Surface(
                            color = if (isSelected) Color.Cyan else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clickable(enabled = isAvailable && !isInteractionLocked) { onUpdateInputMode(index) }
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .alpha(if (isAvailable) 1f else 0.3f)
                        ) {
                            Text(
                                label, 
                                color = if (isSelected) Color.Black else Color.Gray, 
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 2. 狀態文字資訊
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RX: $serialByteCount", color = if(serialByteCount > 0) Color.Cyan else Color.Gray, fontSize = 9.sp)
                    }
                    Text(
                        text = when {
                            inputMode == 2 -> if (isNetworkConnected) "網路模式：連線中" else "網路模式：監聽中"
                            connectionStatus == com.horizon.caadronesimulator.model.ConnectionStatus.ACTIVE -> "連線狀態：正常運作中"
                            connectionStatus == com.horizon.caadronesimulator.model.ConnectionStatus.LINKED -> "已連線 (等待信號...)"
                            connectionStatus == com.horizon.caadronesimulator.model.ConnectionStatus.SEARCHING -> "正在偵測協議與硬體..."
                            else -> (infoMessage ?: "等待掃描連接")
                        },
                        color = when {
                            inputMode == 2 -> if (isNetworkConnected) Color.Green else Color.Cyan
                            connectionStatus == com.horizon.caadronesimulator.model.ConnectionStatus.ACTIVE -> Color.Green
                            connectionStatus == com.horizon.caadronesimulator.model.ConnectionStatus.LINKED -> Color(0xFFFFA000)
                            connectionStatus == com.horizon.caadronesimulator.model.ConnectionStatus.SEARCHING -> Color.Cyan
                            else -> Color.Gray
                        },
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 3. 診斷面板切換按鈕
                IconButton(
                    onClick = { onToggleHardwareMonitor(!showHardwareMonitor) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(if(showHardwareMonitor) Color.Cyan.copy(alpha=0.2f) else Color.Transparent, CircleShape)
                        .border(1.dp, Color.White.copy(alpha=0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.BugReport, null, tint = if(showHardwareMonitor) Color.Cyan else Color.White, modifier = Modifier.size(18.dp))
                }
                
                Spacer(modifier = Modifier.width(12.dp))

                // 4. 動態多狀態掃描按鈕 (v1.3.6 優化版)
                if (inputMode == 2) {
                    IconButton(
                        onClick = { onOpenNetworkSettings() },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Cyan.copy(alpha=0.2f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha=0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Language, null, tint = Color.Cyan, modifier = Modifier.size(18.dp))
                    }
                } else {
                    AnimatedScanButton(
                        status = connectionStatus,
                        locked = isInteractionLocked,
                        onClick = onScanUsb,
                        modifier = Modifier.onGloballyPositioned { onTargetPositioned("scan", it.boundsInRoot()) }
                    )
                }
                
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
fun AnimatedScanButton(
    status: com.horizon.caadronesimulator.model.ConnectionStatus,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_anim")
    
    // 旋轉動畫 (用於握手中)
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotate"
    )
    
    // 呼吸燈動畫 (用於握手中)
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val btnColor = when (status) {
        com.horizon.caadronesimulator.model.ConnectionStatus.ACTIVE -> Color(0xFF4CAF50) // 綠色
        com.horizon.caadronesimulator.model.ConnectionStatus.LINKED -> Color(0xFFFFA000) // 橘色 (無信號)
        com.horizon.caadronesimulator.model.ConnectionStatus.SEARCHING -> Color(0xFF2196F3) // 藍色
        com.horizon.caadronesimulator.model.ConnectionStatus.IDLE -> Color(0xFF607D8B) // 深灰色
    }

    val isSearching = status == com.horizon.caadronesimulator.model.ConnectionStatus.SEARCHING

    Button(
        onClick = onClick,
        enabled = !locked,
        colors = ButtonDefaults.buttonColors(
            containerColor = btnColor,
            disabledContainerColor = btnColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .height(30.dp)
            .alpha(if (isSearching) alpha else 1f),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        if (locked && !isSearching && status == com.horizon.caadronesimulator.model.ConnectionStatus.IDLE) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = when (status) {
                    com.horizon.caadronesimulator.model.ConnectionStatus.ACTIVE -> Icons.Default.CheckCircle
                    com.horizon.caadronesimulator.model.ConnectionStatus.LINKED -> Icons.Default.Warning
                    com.horizon.caadronesimulator.model.ConnectionStatus.SEARCHING -> Icons.Default.Sync
                    com.horizon.caadronesimulator.model.ConnectionStatus.IDLE -> Icons.Default.Usb
                },
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .then(if (isSearching) Modifier.rotate(rotation) else Modifier)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (status) {
                com.horizon.caadronesimulator.model.ConnectionStatus.ACTIVE -> "已連線"
                com.horizon.caadronesimulator.model.ConnectionStatus.LINKED -> "無信號"
                com.horizon.caadronesimulator.model.ConnectionStatus.SEARCHING -> "搜尋中..."
                com.horizon.caadronesimulator.model.ConnectionStatus.IDLE -> if(locked) "處理中..." else "掃描連接"
            },
            fontSize = 11.sp
        )
    }
}
