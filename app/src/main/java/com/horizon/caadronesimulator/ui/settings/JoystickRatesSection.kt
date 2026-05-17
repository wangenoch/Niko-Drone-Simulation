package com.horizon.caadronesimulator.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneRegistry
import java.util.Locale
import kotlin.math.*

/**
 * [v1.5.5] 靈敏度控制區塊 - 極簡油門版
 */
@Composable
fun JoystickRatesSection(
    state: DroneState,
    useGlobalRates: Boolean,
    globalRate: Float,
    globalExpo: Float,
    rateT: Float, expoT: Float,
    rateY: Float, expoY: Float,
    rateP: Float, expoP: Float,
    rateR: Float, expoR: Float,
    showIndividualRates: Boolean,
    onToggleGlobalRates: (Boolean) -> Unit,
    onUpdateGlobalRate: (Float) -> Unit,
    onUpdateGlobalExpo: (Float) -> Unit,
    onUpdateIndividualRate: (String, Float) -> Unit,
    onUpdateIndividualExpo: (String, Float) -> Unit,
    onToggleShowIndividual: (Boolean) -> Unit,
    onResetRates: () -> Unit,
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.onGloballyPositioned { onTargetPositioned("rates", it.boundsInRoot()) }) {
        Surface(color = Color(0x0AFFFFFF), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("靈敏度/曲線", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onResetRates, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(20.dp)) { Icon(Icons.Default.SettingsBackupRestore, null, tint = Color.Cyan, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("重置", color = Color.Cyan, fontSize = 9.sp) }
                    Spacer(modifier = Modifier.width(4.dp)); Text("全域", color = Color.Gray, fontSize = 9.sp); Switch(checked = useGlobalRates, onCheckedChange = onToggleGlobalRates, modifier = Modifier.scale(0.45f))
                }
                if (useGlobalRates) {
                    val module = DroneRegistry.getModule(state.droneType)
                    val isModelOverridden = module.baseRate != 1.0f || module.baseExpo != 0.0f
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Rate: " + "%.1f".format(Locale.US, globalRate), color = if(isModelOverridden) Color.Cyan else Color.Gray, fontSize = 9.sp, modifier = Modifier.width(55.dp)); Slider(value = globalRate, onValueChange = onUpdateGlobalRate, valueRange = 0.1f..2.0f, modifier = Modifier.weight(1f).height(16.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan)) }
                    if (isModelOverridden) { Text("⚠️ 目前已由 [${state.droneType}] 專屬基礎手感疊加", color = Color.Cyan.copy(alpha = 0.6f), fontSize = 8.sp, modifier = Modifier.padding(start = 55.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Expo: " + "%.1f".format(Locale.US, globalExpo), color = if(isModelOverridden) Color.Cyan else Color.Gray, fontSize = 9.sp, modifier = Modifier.width(55.dp)); Slider(value = globalExpo, onValueChange = onUpdateGlobalExpo, valueRange = 0.0f..1.0f, modifier = Modifier.weight(1f).height(16.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan)) }
                } else { Button(onClick = { onToggleShowIndividual(true) }, modifier = Modifier.fillMaxWidth().height(22.dp), contentPadding = PaddingValues(0.dp)) { Text("進階設定 ➔", fontSize = 10.sp) } }
            }
        }
    }
}

/**
 * [通用調整 Overlay]
 * 支援單一 Rate (使用者) 或非對稱 Rate (機種基因)
 */
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
    Box(modifier = Modifier.fillMaxSize().background(Color(0xF20A0E14)).clickable(enabled = false) {}.zIndex(1000f)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(16.dp)); TextButton(onClick = onResetAll) { Icon(Icons.Default.SettingsBackupRestore, null, tint = Color.Cyan, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("全部還原預設值", color = Color.Cyan, fontSize = 12.sp) } }
                    Text(subtitle, color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 油門卡片：根據模式決定顯示單一還是非對稱
                    RateCard(
                        key = "T", label = "🚀 油門 (Throttle)", 
                        rateUp = if(isGeneMode) rateT_Up else rateT, 
                        rateDown = if(isGeneMode) rateT_Down else rateT, 
                        expo = expoT, onUpdateRate = onUpdateRate, onUpdateExpo = onUpdateExpo,
                        showAsymmetrical = isGeneMode
                    )
                    RateCard("Y", "🔄 航向 (Yaw)", rateY, rateY, expoY, onUpdateRate, onUpdateExpo)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RateCard("P", "📐 俯仰 (Pitch)", rateP, rateP, expoP, onUpdateRate, onUpdateExpo)
                    RateCard("R", "⚖️ 橫滾 (Roll)", rateR, rateR, expoR, onUpdateRate, onUpdateExpo)
                }
            }
            Text(text = "當前遙控器模式：[Mode $joystickMode ${getModeName(joystickMode)}]", color = Color.Cyan.copy(0.5f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 8.dp))
        }
    }
}

private fun getModeName(m: Int) = when(m) { 1 -> "日本手"; 2 -> "美國手"; 3 -> "反向手"; 4 -> "特別手"; else -> "" }

@Composable
fun RateCard(
    key: String, label: String, rateUp: Float, rateDown: Float, expo: Float, 
    onUpdateRate: (String, Float) -> Unit, onUpdateExpo: (String, Float) -> Unit, 
    isSmall: Boolean = false, showAsymmetrical: Boolean = false
) {
    Surface(color = Color(0x1AFFFFFF), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
        Row(modifier = Modifier.padding(if(isSmall) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(if(isSmall) 50.dp else 65.dp).background(Color.Black, RoundedCornerShape(4.dp))) {
                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    val w = size.width; val h = size.height; drawLine(Color.White.copy(0.1f), Offset(w/2, 0f), Offset(w/2, h), 1f); drawLine(Color.White.copy(0.1f), Offset(0f, h/2), Offset(w, h/2), 1f)
                    val path = Path(); for (i in 0..20) { val x = (i / 10f) - 1f; val absX = abs(x); val r = if (x >= 0) rateUp else rateDown; val y = sign(x) * ((1f - expo) * absX + expo * absX * absX * absX) * (r / 2f); val sx = (x + 1f) / 2f * w; val sy = (1f - (y + 1f) / 2f) * h; if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy) }; drawPath(path, Color.Cyan, style = Stroke(1.5.dp.toPx()))
                }
            }
            Spacer(modifier = Modifier.width(if(isSmall) 10.dp else 14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if(showAsymmetrical) 2.dp else 4.dp)) {
                Text(label, color = Color.White, fontSize = if(isSmall) 11.sp else 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                if (showAsymmetrical) {
                    AsymmetricalSlider("上昇", rateUp, { onUpdateRate("${key}_Up", it) }, isSmall)
                    AsymmetricalSlider("下降", rateDown, { onUpdateRate("${key}_Down", it) }, isSmall)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Text("R:" + "%.1f".format(Locale.US, rateUp), color = Color.Cyan.copy(0.7f), fontSize = if(isSmall) 8.sp else 9.sp, modifier = Modifier.width(if(isSmall) 30.dp else 38.dp))
                        Slider(value = rateUp, onValueChange = { onUpdateRate(key, it) }, valueRange = 0.1f..2.0f, modifier = Modifier.height(24.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan)) 
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Text("E:" + "%.1f".format(Locale.US, expo), color = Color.Gray, fontSize = if(isSmall) 8.sp else 9.sp, modifier = Modifier.width(if(isSmall) 30.dp else 38.dp))
                    Slider(value = expo, onValueChange = { onUpdateExpo(key, it) }, valueRange = 0.0f..1.0f, modifier = Modifier.height(24.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan)) 
                }
            }
        }
    }
}

@Composable
fun AsymmetricalSlider(label: String, value: Float, onValueChange: (Float) -> Unit, isSmall: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.Gray, fontSize = if(isSmall) 7.sp else 8.sp, modifier = Modifier.width(if(isSmall) 28.dp else 34.dp))
        Slider(value = value, onValueChange = onValueChange, valueRange = 0.1f..2.0f, modifier = Modifier.height(20.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan.copy(0.5f)))
    }
}
