package com.horizon.caadronesimulator.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * [v1.5.9] 視訊導航與介面配置頁面
 * 互動優化：標題輸入改為彈出對話框模式。
 */
@Composable
fun VisualNavigationScreen(
    cameraMode: String,
    mainFOV: Float,
    zoomFactor: Float,
    showSpecialTitle: Boolean,
    customTitle: String,
    showSideSliders: Boolean,
    showSideRulers: Boolean,
    reverseSliderSides: Boolean,
    showGroundAnchor: Boolean,
    autoPiPRelocate: Boolean,
    enableZoomAssistant: Boolean,
    onUpdateCameraMode: (String) -> Unit,
    onUpdateFOV: (Float) -> Unit,
    onUpdateZoom: (Float) -> Unit,
    onToggleSpecialTitle: (Boolean) -> Unit,
    onUpdateCustomTitle: (String) -> Unit,
    onToggleSideSliders: (Boolean) -> Unit,
    onToggleSideRulers: (Boolean) -> Unit,
    onToggleReverseSliders: (Boolean) -> Unit,
    onToggleGroundAnchor: (Boolean) -> Unit,
    onTogglePiPRelocate: (Boolean) -> Unit,
    onToggleZoomAssistant: (Boolean) -> Unit,
    onSave: () -> Unit = {}
) {
    var showTitleDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 📷 鏡頭與視角
        Surface(
            modifier = Modifier.weight(1.2f),
            color = Color(0x1AFFFFFF),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("📷 鏡頭與視角", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("視角模式", color = Color.Gray, fontSize = 10.sp)
                val modes = listOf("站位視角 (追蹤)", "站位視角 (智慧)", "站位視角 (固定)", "跟隨視角", "FPV 視角")
                modes.forEach { mode ->
                    CompactChip(
                        text = mode,
                        selected = cameraMode == mode,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).height(28.dp),
                        onClick = { onUpdateCameraMode(mode); onSave() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("視野範圍 (FOV)", color = Color.White, fontSize = 11.sp)
                        Text("${mainFOV.toInt()}°", color = Color.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = mainFOV,
                        onValueChange = onUpdateFOV,
                        onValueChangeFinished = onSave,
                        valueRange = 30f..110f,
                        modifier = Modifier.weight(2f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.Cyan)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("縮放倍率", color = Color.White, fontSize = 11.sp)
                        Text("${String.format(Locale.US, "%.1f", zoomFactor)}x", color = Color.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = zoomFactor,
                        onValueChange = onUpdateZoom,
                        onValueChangeFinished = onSave,
                        valueRange = 0.5f..4.0f,
                        modifier = Modifier.weight(2f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.Cyan)
                    )
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("0.5x", "1.0x", "1.5x", "2.0x", "3.0x").forEach { label ->
                        val valOf = label.replace("x", "").toFloat()
                        FilterChip(
                            selected = kotlin.math.abs(zoomFactor - valOf) < 0.1f,
                            onClick = { onUpdateZoom(valOf); onSave() },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                VisualSwitchItem("遠距離自動放大助手", enableZoomAssistant, onToggleZoomAssistant, onSave)
            }
        }

        // 2. 🖥️ HUD 介面元素
        Surface(
            modifier = Modifier.weight(1.1f),
            color = Color(0x1AFFFFFF),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("🖥️ HUD 介面元素", color = Color.Yellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // 標題顯示與編輯 (彈出式)
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.fillMaxWidth().height(32.dp).clickable { if (showSpecialTitle) showTitleDialog = true }
                ) {
                    Text("頂部特別標題顯示", color = Color.White.copy(0.9f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    if (showSpecialTitle) {
                        Icon(Icons.Default.Edit, null, tint = Color.Yellow.copy(0.6f), modifier = Modifier.size(12.dp).padding(end = 4.dp))
                    }
                    Switch(
                        checked = showSpecialTitle,
                        onCheckedChange = { 
                            onToggleSpecialTitle(it)
                            if (it) showTitleDialog = true 
                            onSave()
                        },
                        modifier = Modifier.scale(0.55f),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                VisualSwitchItem("顯示側邊操作拉桿", showSideSliders, onToggleSideSliders, onSave)
                VisualSwitchItem("顯示高度速度標尺", showSideRulers, onToggleSideRulers, onSave)
                VisualSwitchItem("拉桿位置左右反轉", reverseSliderSides, onToggleReverseSliders, onSave)
            }
        }

        // 3. 🛰️ 導航輔助
        Surface(
            modifier = Modifier.weight(0.9f),
            color = Color(0x1AFFFFFF),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("🛰️ 導航輔助", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                VisualSwitchItem("AR 輔助投影 (H點)", showGroundAnchor, onToggleGroundAnchor, onSave)
                VisualSwitchItem("子畫面自動位移", autoPiPRelocate, onTogglePiPRelocate, onSave)
                
                Spacer(Modifier.weight(1f))
                Text("所有設定將即時套用至導航系統。", color = Color.Gray, fontSize = 9.sp)
            }
        }
    }

    // 編輯對話框
    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("編輯特別標題", color = Color.White, fontSize = 16.sp) },
            text = {
                Column {
                    Text("請輸入顯示於起飛點上方的文字：", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { onUpdateCustomTitle(it) },
                        placeholder = { Text("例如：Niko Drone Team", color = Color.DarkGray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Cyan, 
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showTitleDialog = false; onSave() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) {
                    Text("確認套用", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

@Composable
fun VisualSwitchItem(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(32.dp)) {
        Text(label, color = Color.White.copy(0.9f), fontSize = 11.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it); onSave() },
            modifier = Modifier.scale(0.55f),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan)
        )
    }
}
