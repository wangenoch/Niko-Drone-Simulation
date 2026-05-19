package com.horizon.caadronesimulator.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.horizon.caadronesimulator.model.DroneState

/**
 * [v1.7.6] 專業版硬體連線狀態 Bar
 * 職責：提供內置/外接模式切換、快速鎖定與診斷監控入口。
 */
@Composable
fun HardwareConnectionHeader(
    state: DroneState,
    inputMode: Int,
    isSignalActive: Boolean,
    showHardwareMonitor: Boolean,
    onUpdateInputMode: (Int) -> Unit,
    onToggleHardwareMonitor: (Boolean) -> Unit
) {
    if (state.isExpertModeLocked) return

    Surface(
        color = Color.White.copy(alpha = 0.05f), 
        shape = RoundedCornerShape(12.dp), 
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("遙控器連線與模式設定", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp)) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            listOf("外接", "內置").forEachIndexed { idx, name ->
                                val isSel = inputMode == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if(isSel) Color(0xFF333333) else Color.Transparent)
                                        .border(if(isSel) 1.dp else 0.dp, if(isSel) Color.Gray else Color.Transparent, RoundedCornerShape(4.dp))
                                        .clickable { onUpdateInputMode(idx) }
                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                ) {
                                    Text(name, color = if(isSel) Color.White else Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (isSignalActive) "連線正常" else "等待信號...", 
                        color = if (isSignalActive) Color.Green else Color.Red, 
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 快速鎖定開關
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.clickable { state.isExpertModeLocked = true }
                ) {
                    Text("快速鎖定", color = Color.Yellow.copy(0.6f), fontSize = 10.sp)
                    Switch(
                        checked = false,
                        onCheckedChange = { state.isExpertModeLocked = true }, 
                        modifier = Modifier.scale(0.55f), 
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow)
                    )
                }
                
                // 診斷監測按鈕
                IconButton(
                    onClick = { onToggleHardwareMonitor(!showHardwareMonitor) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (showHardwareMonitor) Color.Cyan.copy(alpha = 0.2f) else Color(0xFF222222), CircleShape)
                        .border(1.dp, if (showHardwareMonitor) Color.Cyan else Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.BugReport, 
                        null, 
                        tint = if (showHardwareMonitor) Color.Cyan else Color.White, 
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
