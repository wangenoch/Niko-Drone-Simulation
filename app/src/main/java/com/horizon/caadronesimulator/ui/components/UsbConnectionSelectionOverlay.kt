package com.horizon.caadronesimulator.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * [v1.2.68] USB 裝置插入時的模式選擇引導視窗
 */
@Composable
fun UsbConnectionSelectionOverlay(
    onSelectExternal: () -> Unit,
    onSelectInternal: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() }
            .zIndex(3000f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.8f)
                .clickable(enabled = false) {},
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "偵測到新連線設備",
                        color = Color.Cyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-12).dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
                
                Text(
                    "請選擇此設備的運作模式：",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SelectionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Gamepad,
                        title = "外接手把",
                        desc = "Xbox/PS5 手把\n藍牙控制器",
                        color = Color(0xFF2196F3),
                        onClick = onSelectExternal
                    )

                    SelectionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SettingsInputComponent,
                        title = "專業遙控器",
                        desc = "AX12 / MK15\n數位直連",
                        color = Color(0xFF4CAF50),
                        onClick = onSelectInternal
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "如果不確定，可以點擊右上角關閉，\n稍後在設定頁面中隨時切換。",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SelectionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    desc: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                desc,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}
