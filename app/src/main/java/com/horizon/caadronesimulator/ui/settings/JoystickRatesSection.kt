package com.horizon.caadronesimulator.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.ui.theme.NikoTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun HandfeelTuningOverlay(
    title: String, subtitle: String = "",
    rateT: Float = 1.0f, rateT_Up: Float = 1.0f, rateT_Down: Float = 1.0f, expoT: Float,
    rateY: Float, expoY: Float,
    rateP: Float, expoP: Float,
    rateR: Float, expoR: Float,
    onUpdateRate: (String, Float) -> Unit, onUpdateExpo: (String, Float) -> Unit,
    onResetAll: () -> Unit, onClose: () -> Unit, joystickMode: Int = 2,
    isGeneMode: Boolean = false // 是否為機種基因模式 (顯示非對稱油門)
) {
    val themeColors = NikoTheme.colors
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable(enabled = false) {}.zIndex(1000f)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Text(title, color = themeColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(16.dp))
                        TextButton(onClick = onResetAll) { 
                            Icon(Icons.Default.SettingsBackupRestore, null, tint = themeColors.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_reset_all), color = themeColors.primary, fontSize = 12.sp) 
                        } 
                    }
                    Text(subtitle, color = themeColors.textSecondary, fontSize = 11.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = themeColors.textPrimary) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 油門卡片：根據模式決定顯示單一還是非對稱
                    RateCard(
                        key = "T", label = "🚀 " + stringResource(R.string.joystick_label_throttle), 
                        rateUp = if(isGeneMode) rateT_Up else rateT, 
                        rateDown = if(isGeneMode) rateT_Down else rateT, 
                        expo = expoT, onUpdateRate = onUpdateRate, onUpdateExpo = onUpdateExpo,
                        showAsymmetrical = isGeneMode
                    )
                    RateCard("Y", "🔄 " + stringResource(R.string.joystick_label_yaw), rateY, rateY, expoY, onUpdateRate, onUpdateExpo)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RateCard("P", "📐 " + stringResource(R.string.joystick_label_pitch), rateP, rateP, expoP, onUpdateRate, onUpdateExpo)
                    RateCard("R", "⚖️ " + stringResource(R.string.joystick_label_roll), rateR, rateR, expoR, onUpdateRate, onUpdateExpo)
                }
            }
            
            val modeName = when(joystickMode) {
                1 -> stringResource(R.string.joystick_mode_1)
                2 -> stringResource(R.string.joystick_mode_2)
                3 -> stringResource(R.string.joystick_mode_3)
                4 -> stringResource(R.string.joystick_mode_4)
                else -> ""
            }
            Text(
                text = stringResource(R.string.joystick_mode_hint, joystickMode, modeName), 
                color = themeColors.primary.copy(0.5f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun RateCard(
    key: String, label: String, rateUp: Float, rateDown: Float, expo: Float, 
    onUpdateRate: (String, Float) -> Unit, onUpdateExpo: (String, Float) -> Unit, 
    isSmall: Boolean = false, showAsymmetrical: Boolean = false
) {
    val themeColors = NikoTheme.colors
    Surface(color = themeColors.textPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, themeColors.divider)) {
        Row(modifier = Modifier.padding(if(isSmall) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(if(isSmall) 50.dp else 65.dp).background(themeColors.background, RoundedCornerShape(4.dp))) {
                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    val w = size.width; val h = size.height; drawLine(themeColors.divider, Offset(w/2, 0f), Offset(w/2, h), 1f); drawLine(themeColors.divider, Offset(0f, h/2), Offset(w, h/2), 1f)
                    val path = Path(); for (i in 0..20) { val x = (i / 10f) - 1f; val absX = abs(x); val r = if (x >= 0) rateUp else rateDown; val y = sign(x) * ((1f - expo) * absX + expo * absX * absX * absX) * (r / 2f); val sx = (x + 1f) / 2f * w; val sy = (1f - (y + 1f) / 2f) * h; if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy) }; drawPath(path, themeColors.primary, style = Stroke(1.5.dp.toPx()))
                }
            }
            Spacer(modifier = Modifier.width(if(isSmall) 10.dp else 14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if(showAsymmetrical) 2.dp else 4.dp)) {
                Text(label, color = themeColors.textPrimary, fontSize = if(isSmall) 11.sp else 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                if (showAsymmetrical) {
                    AsymmetricalSlider(stringResource(R.string.joystick_rate_up), rateUp, { onUpdateRate("${key}_Up", it) }, isSmall)
                    AsymmetricalSlider(stringResource(R.string.joystick_rate_down), rateDown, { onUpdateRate("${key}_Down", it) }, isSmall)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Text(stringResource(R.string.hud_rate_short) + ":" + "%.1f".format(Locale.US, rateUp), color = themeColors.primary.copy(0.7f), fontSize = if(isSmall) 8.sp else 9.sp, modifier = Modifier.width(if(isSmall) 30.dp else 38.dp))
                        Slider(value = rateUp, onValueChange = { onUpdateRate(key, it) }, valueRange = 0.1f..2.0f, modifier = Modifier.height(24.dp), colors = SliderDefaults.colors(thumbColor = themeColors.primary, activeTrackColor = themeColors.primary, inactiveTrackColor = themeColors.divider)) 
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Text(stringResource(R.string.hud_expo_short) + ":" + "%.1f".format(Locale.US, expo), color = themeColors.textSecondary, fontSize = if(isSmall) 8.sp else 9.sp, modifier = Modifier.width(if(isSmall) 30.dp else 38.dp))
                    Slider(value = expo, onValueChange = { onUpdateExpo(key, it) }, valueRange = 0.0f..1.0f, modifier = Modifier.height(24.dp), colors = SliderDefaults.colors(thumbColor = themeColors.primary, activeTrackColor = themeColors.primary, inactiveTrackColor = themeColors.divider)) 
                }
            }
        }
    }
}

@Composable
fun AsymmetricalSlider(label: String, value: Float, onValueChange: (Float) -> Unit, isSmall: Boolean) {
    val themeColors = NikoTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = themeColors.textSecondary, fontSize = if(isSmall) 7.sp else 8.sp, modifier = Modifier.width(if(isSmall) 28.dp else 34.dp))
        // [v1.6.1] 加入精確數值顯示，方便教官進行量化微調
        Text(stringResource(R.string.hud_rate_short) + ":%.1f".format(java.util.Locale.US, value), color = themeColors.primary.copy(0.7f), fontSize = if(isSmall) 7.sp else 8.sp, modifier = Modifier.width(if(isSmall) 28.dp else 32.dp))
        Slider(value = value, onValueChange = onValueChange, valueRange = 0.1f..2.0f, modifier = Modifier.height(20.dp), colors = SliderDefaults.colors(thumbColor = themeColors.primary, activeTrackColor = themeColors.primary.copy(0.5f), inactiveTrackColor = themeColors.divider))
    }
}
