package com.horizon.nikonikodronesimulator.ui.settings

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
import com.horizon.nikonikodronesimulator.ui.theme.NikoTheme

/**
 * [v1.2.68] 硬體連線控制組件
 * 整合了多狀態掃描、呼吸燈動畫與交互鎖定保護機制。
 */
import androidx.compose.ui.res.stringResource
import com.horizon.nikonikodronesimulator.R

@Composable
fun HardwareConnectionSection(
    inputMode: Int,
    isHardwareController: Boolean,
    isNetworkConnected: Boolean = false,
    connectionStatus: com.horizon.nikonikodronesimulator.model.ConnectionStatus = com.horizon.nikonikodronesimulator.model.ConnectionStatus.IDLE,
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
                        .background(NikoTheme.colors.textPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .onGloballyPositioned { onTargetPositioned("input_mode", it.boundsInRoot()) }
                        .padding(2.dp)
                        .alpha(if (isInteractionLocked) 0.5f else 1f)
                ) {
                    val modeLabels = listOf(
                        stringResource(R.string.diag_input_ext),
                        stringResource(R.string.diag_input_int),
                        stringResource(R.string.diag_input_net)
                    )
                    modeLabels.forEachIndexed { index, label ->
                        val isAvailable = when(index) {
                            1 -> isHardwareController
                            else -> true
                        }
                        val isSelected = inputMode == index
                        Surface(
                            color = if (isSelected) NikoTheme.colors.primary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clickable(enabled = isAvailable && !isInteractionLocked) { onUpdateInputMode(index) }
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .alpha(if (isAvailable) 1f else 0.3f)
                        ) {
                            Text(
                                label, 
                                color = if (isSelected) (if(NikoTheme.colors.isLight) Color.White else Color.Black) else NikoTheme.colors.textSecondary, 
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
                        Text("RX: $serialByteCount", color = if(serialByteCount > 0) NikoTheme.colors.primary else NikoTheme.colors.textSecondary, fontSize = 9.sp)
                    }
                    Text(
                        text = when {
                            inputMode == 2 -> if (isNetworkConnected) stringResource(R.string.diag_net_connected) else stringResource(R.string.diag_net_listening)
                            connectionStatus == com.horizon.nikonikodronesimulator.model.ConnectionStatus.ACTIVE -> stringResource(R.string.diag_status_active_desc)
                            connectionStatus == com.horizon.nikonikodronesimulator.model.ConnectionStatus.LINKED -> stringResource(R.string.diag_status_linked_desc)
                            connectionStatus == com.horizon.nikonikodronesimulator.model.ConnectionStatus.SEARCHING -> stringResource(R.string.diag_status_searching_desc)
                            else -> (infoMessage ?: stringResource(R.string.diag_status_waiting_scan))
                        },
                        color = when {
                            inputMode == 2 -> if (isNetworkConnected) NikoTheme.colors.status else NikoTheme.colors.primary
                            connectionStatus == com.horizon.nikonikodronesimulator.model.ConnectionStatus.ACTIVE -> NikoTheme.colors.status
                            connectionStatus == com.horizon.nikonikodronesimulator.model.ConnectionStatus.LINKED -> NikoTheme.colors.safety
                            connectionStatus == com.horizon.nikonikodronesimulator.model.ConnectionStatus.SEARCHING -> NikoTheme.colors.primary
                            else -> NikoTheme.colors.textSecondary
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
                        .background(if(showHardwareMonitor) NikoTheme.colors.primary.copy(alpha=0.2f) else Color.Transparent, CircleShape)
                        .border(1.dp, NikoTheme.colors.divider, CircleShape)
                ) {
                    Icon(Icons.Default.BugReport, null, tint = if(showHardwareMonitor) NikoTheme.colors.primary else NikoTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
                }
                
                Spacer(modifier = Modifier.width(12.dp))

                // 4. 動態多狀態掃描按鈕 (v1.3.6 優化版)
                if (inputMode == 2) {
                    IconButton(
                        onClick = { onOpenNetworkSettings() },
                        modifier = Modifier
                            .size(32.dp)
                            .background(NikoTheme.colors.primary.copy(alpha=0.2f), CircleShape)
                            .border(1.dp, NikoTheme.colors.divider, CircleShape)
                    ) {
                        Icon(Icons.Default.Language, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(18.dp))
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
    status: com.horizon.nikonikodronesimulator.model.ConnectionStatus,
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
        com.horizon.nikonikodronesimulator.model.ConnectionStatus.ACTIVE -> Color(0xFF4CAF50) // 綠色
        com.horizon.nikonikodronesimulator.model.ConnectionStatus.LINKED -> Color(0xFFFFA000) // 橘色 (無信號)
        com.horizon.nikonikodronesimulator.model.ConnectionStatus.SEARCHING -> Color(0xFF2196F3) // 藍色
        com.horizon.nikonikodronesimulator.model.ConnectionStatus.IDLE -> Color(0xFF607D8B) // 深灰色
    }

    val isSearching = status == com.horizon.nikonikodronesimulator.model.ConnectionStatus.SEARCHING

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
        if (locked && !isSearching && status == com.horizon.nikonikodronesimulator.model.ConnectionStatus.IDLE) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = when (status) {
                    com.horizon.nikonikodronesimulator.model.ConnectionStatus.ACTIVE -> Icons.Default.CheckCircle
                    com.horizon.nikonikodronesimulator.model.ConnectionStatus.LINKED -> Icons.Default.Warning
                    com.horizon.nikonikodronesimulator.model.ConnectionStatus.SEARCHING -> Icons.Default.Sync
                    com.horizon.nikonikodronesimulator.model.ConnectionStatus.IDLE -> Icons.Default.Usb
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
                com.horizon.nikonikodronesimulator.model.ConnectionStatus.ACTIVE -> stringResource(R.string.diag_status_active_full)
                com.horizon.nikonikodronesimulator.model.ConnectionStatus.LINKED -> stringResource(R.string.diag_status_no_signal)
                com.horizon.nikonikodronesimulator.model.ConnectionStatus.SEARCHING -> stringResource(R.string.diag_status_searching_dots)
                com.horizon.nikonikodronesimulator.model.ConnectionStatus.IDLE -> if(locked) stringResource(R.string.diag_status_processing) else stringResource(R.string.diag_status_scan_link)
            },
            fontSize = 11.sp
        )
    }
}
