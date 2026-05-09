package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.model.ChannelMapping
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneCategory

/**
 * [v1.5.0] 擴充映射彈窗 - 左右分欄優化版
 */
@Composable
fun AuxMappingOverlay(
    state: DroneState,
    rawChannels: List<Float>,
    onStartBinding: (String) -> Unit,
    onManualBind: (String, Int) -> Unit,
    onToggleInvert: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}
            .zIndex(1000f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.95f),
            color = Color(0xFF111111),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                // Header - 極簡化
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
                    Text("擴充功能映射與開關設定", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Text("撥動開關後點擊「偵測」即可綁定通道。", color = Color.Gray, fontSize = 9.sp)
                
                Spacer(Modifier.height(4.dp))

                // 核心分欄區域
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 左側：安全控制組
                    Column(modifier = Modifier.weight(1f)) {
                        AuxSectionTitle("🔒 安全防護")
                        Surface(color = Color(0x0AFFFFFF), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AuxMappingLine("解鎖 (Arm)", state.mappingArm, "arm", state.isAutoBinding, rawChannels, onStartBinding, onManualBind, onToggleInvert, state.inputMode)
                                
                                val spec = com.horizon.caadronesimulator.model.DroneRegistry.getSpec(state.droneType)
                                if (spec.category == DroneCategory.HELI) {
                                    AuxMappingLine("熄火 (Hold)", state.mappingHold, "hold", state.isAutoBinding, rawChannels, onStartBinding, onManualBind, onToggleInvert, state.inputMode)
                                } else {
                                    Box(modifier = Modifier.height(22.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("多旋翼無需熄火開關", color = Color.DarkGray, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 右側：視覺與站位組
                    Column(modifier = Modifier.weight(1.3f)) {
                        AuxSectionTitle("👁️ 站位與視角控制")
                        Surface(color = Color(0x0AFFFFFF), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AuxMappingLine("站位高度", state.mappingObsHeight, "obsHeight", state.isAutoBinding, rawChannels, onStartBinding, onManualBind, onToggleInvert, state.inputMode)
                                AuxMappingLine("抬頭角度", state.mappingObsTilt, "obsTilt", state.isAutoBinding, rawChannels, onStartBinding, onManualBind, onToggleInvert, state.inputMode)
                                AuxMappingLine("FPV 雲台", state.mappingFpvTilt, "fpvTilt", state.isAutoBinding, rawChannels, onStartBinding, onManualBind, onToggleInvert, state.inputMode)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("完成設定並返回", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AuxSectionTitle(text: String) {
    Text(text, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
}

@Composable
fun AuxMappingLine(
    label: String, 
    mapping: ChannelMapping, 
    key: String, 
    isBinding: String?, 
    rawChannels: List<Float>,
    onStartBinding: (String) -> Unit,
    onManualBind: (String, Int) -> Unit,
    onToggleInvert: (String) -> Unit,
    inputMode: Int
) {
    val liveValue = if (mapping.axis >= 101) rawChannels.getOrNull(mapping.axis - 101) ?: 0f else 0f
    
    CompactMappingLine(
        label = label,
        mapping = mapping,
        key = key,
        isBinding = isBinding,
        liveValue = liveValue,
        onStartBinding = onStartBinding,
        onToggleInvert = onToggleInvert,
        onManualBind = onManualBind,
        inputMode = inputMode,
        isSmall = true
    )
}
