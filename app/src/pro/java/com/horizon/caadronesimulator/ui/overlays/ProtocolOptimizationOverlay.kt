package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.horizon.caadronesimulator.logic.HardwareRegistry
import com.horizon.caadronesimulator.model.CommDecisionState
import com.horizon.caadronesimulator.model.DroneState

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.5.9] 協議優化引導對話框 (重構版)
 */
@Composable
fun ProtocolOptimizationOverlay(
    state: DroneState,
    onApply: (String, Int) -> Unit,
    onIgnore: (Boolean) -> Unit
) {
    if (state.commDecisionState != CommDecisionState.ENGAGED) return
    
    // [安全閥] 若已鎖定外接 HID 模式，則自動取消該提示
    if (state.inputMode == 0) {
        onIgnore(false)
        return
    }

    val profile = HardwareRegistry.detectHardware()

    Dialog(onDismissRequest = { onIgnore(false) }) {
        Surface(
            color = Color(0xFF1B2535),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.width(360.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SettingsSuggest, null, tint = Color.Cyan, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.optimization_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.optimization_desc, profile.brandName),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onIgnore(false) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text(stringResource(R.string.action_ignore_temp), color = Color.White, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { 
                            onApply(profile.id, profile.driver?.recommendedBaudRate ?: 115200)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                    ) {
                        Text(stringResource(R.string.action_apply), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(onClick = { onIgnore(true) }) {
                    Text(stringResource(R.string.action_ignore_perm), color = Color.White.copy(0.4f), fontSize = 11.sp)
                }
            }
        }
    }
}
