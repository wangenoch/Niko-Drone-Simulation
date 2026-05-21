package com.horizon.caadronesimulator.ui.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.ChannelMapping
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.ui.theme.NikoTheme

@Composable
fun AuxMappingOverlay(
    state: DroneState,
    onStartBinding: (String) -> Unit,
    onManualBind: (String, Int) -> Unit,
    onToggleInvert: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeColors = NikoTheme.colors
    // [v1.5.2 修正] 採用全螢幕沉浸式架構，與 RatesOverlay 同圖層且獨立於捲動容器之外
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background.copy(alpha = 0.9f)) // 稍深的背景增強專注感
            .clickable(enabled = false) {}
            .zIndex(1000f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f),
            color = themeColors.panel,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, themeColors.divider)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxSize()) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    Text(stringResource(R.string.joystick_aux_title), color = themeColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, tint = themeColors.textPrimary, modifier = Modifier.size(24.dp))
                    }
                }
                
                Text(stringResource(R.string.joystick_aux_hint), color = themeColors.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))

                // 映射區域
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 左側：安全防護
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.joystick_aux_safety), color = themeColors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Surface(color = themeColors.textPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AuxMappingLine(stringResource(R.string.joystick_label_arm), state.mappingArm, "arm", state.isAutoBinding, onStartBinding, onToggleInvert, onManualBind, state.inputMode)
                                
                                // [v1.5.2] 解除機型限制，永遠顯示熄火開關並加註警告
                                Column {
                                    AuxMappingLine(stringResource(R.string.joystick_label_hold), state.mappingHold, "hold", state.isAutoBinding, onStartBinding, onToggleInvert, onManualBind, state.inputMode)
                                    Text(
                                        text = stringResource(R.string.joystick_aux_heli_only), 
                                        color = themeColors.accent.copy(alpha = 0.6f), 
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(start = 45.dp, top = 2.dp)
                                    )
                                }

                                // [v1.5.2] 新增：飛行模式映射 (預留定位/姿態切換)
                                Column {
                                    AuxMappingLine(stringResource(R.string.joystick_label_flight_mode), state.mappingFlightMode, "flightMode", state.isAutoBinding, onStartBinding, onToggleInvert, onManualBind, state.inputMode)
                                    Text(
                                        text = stringResource(R.string.joystick_aux_future_mode), 
                                        color = themeColors.textSecondary.copy(alpha = 0.8f), 
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(start = 45.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 右側：站位與視角
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.joystick_aux_camera), color = themeColors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Surface(color = themeColors.textPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AuxMappingLine(stringResource(R.string.joystick_label_obs_height), state.mappingObsHeight, "obsHeight", state.isAutoBinding, onStartBinding, onToggleInvert, onManualBind, state.inputMode)
                                AuxMappingLine(stringResource(R.string.joystick_label_obs_tilt), state.mappingObsTilt, "obsTilt", state.isAutoBinding, onStartBinding, onToggleInvert, onManualBind, state.inputMode)
                                AuxMappingLine(stringResource(R.string.joystick_label_fpv_tilt), state.mappingFpvTilt, "fpvTilt", state.isAutoBinding, onStartBinding, onToggleInvert, onManualBind, state.inputMode)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss, 
                    modifier = Modifier.fillMaxWidth().height(44.dp), 
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Text(stringResource(R.string.action_finish), color = if(themeColors.isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun AuxMappingLine(label: String, mapping: ChannelMapping, key: String, isBinding: String?, onStartBinding: (String) -> Unit, onToggleInvert: (String) -> Unit, onManualBind: (String, Int) -> Unit, inputMode: Int) {
    CompactMappingRow(label, mapping, key, isBinding, onStartBinding, onToggleInvert, onManualBind, inputMode, labelWidth = 65.dp)
}
