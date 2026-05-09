package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import java.util.Locale
import kotlin.math.*

/**
 * [v1.2.68] 獨立的手感靈敏度與曲線設定區塊
 */
@Composable
fun JoystickRatesSection(
    useGlobalRates: Boolean,
    globalRate: Float,
    globalExpo: Float,
    rateLY: Float, expoLY: Float,
    rateLX: Float, expoLX: Float,
    rateRY: Float, expoRY: Float,
    rateRX: Float, expoRX: Float,
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
                    TextButton(onClick = onResetRates, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(20.dp)) {
                        Icon(Icons.Default.SettingsBackupRestore, null, tint = Color.Cyan, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("重置", color = Color.Cyan, fontSize = 9.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("全域", color = Color.Gray, fontSize = 9.sp)
                    Switch(checked = useGlobalRates, onCheckedChange = onToggleGlobalRates, modifier = Modifier.scale(0.45f))
                }
                if (useGlobalRates) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rate: ${String.format(Locale.US, "%.1f", globalRate)}", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(55.dp))
                        Slider(value = globalRate, onValueChange = onUpdateGlobalRate, valueRange = 0.1f..2.0f, modifier = Modifier.weight(1f).height(16.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Expo: ${String.format(Locale.US, "%.1f", globalExpo)}", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(55.dp))
                        Slider(value = globalExpo, onValueChange = onUpdateGlobalExpo, valueRange = 0.0f..1.0f, modifier = Modifier.weight(1f).height(16.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan))
                    }
                } else {
                    Button(onClick = { onToggleShowIndividual(true) }, modifier = Modifier.fillMaxWidth().height(22.dp), contentPadding = PaddingValues(0.dp)) { Text("進階設定 ➔", fontSize = 10.sp) }
                }
            }
        }

        if (showIndividualRates) {
            Popup(
                onDismissRequest = { onToggleShowIndividual(false) },
                properties = PopupProperties(focusable = true, dismissOnClickOutside = false)
            ) {
                IndividualRatesOverlay(
                    rateLY, expoLY, rateLX, expoLX, rateRY, expoRY, rateRX, expoRX,
                    onUpdateRate = onUpdateIndividualRate,
                    onUpdateExpo = onUpdateIndividualExpo,
                    onResetAll = onResetRates,
                    onClose = { onToggleShowIndividual(false) }
                )
            }
        }
    }
}

@Composable
fun IndividualRatesOverlay(
    rateLY: Float, expoLY: Float, rateLX: Float, expoLX: Float,
    rateRY: Float, expoRY: Float, rateRX: Float, expoRX: Float,
    onUpdateRate: (String, Float) -> Unit,
    onUpdateExpo: (String, Float) -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xF20A0E14)).clickable(enabled = false) {}.zIndex(200f)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("各別通道進階設定", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = onResetAll) { Icon(Icons.Default.SettingsBackupRestore, null, tint = Color.Cyan, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("全部還原預設值", color = Color.Cyan, fontSize = 12.sp) }
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RateCard("ly", "左垂直 (LY)", rateLY, expoLY, onUpdateRate, onUpdateExpo)
                    RateCard("lx", "左水平 (LX)", rateLX, expoLX, onUpdateRate, onUpdateExpo)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RateCard("ry", "右垂直 (RY)", rateRY, expoRY, onUpdateRate, onUpdateExpo)
                    RateCard("rx", "右水平 (RX)", rateRX, expoRX, onUpdateRate, onUpdateExpo)
                }
            }
        }
    }
}

@Composable
fun RateCard(key: String, label: String, rate: Float, expo: Float, onUpdateRate: (String, Float) -> Unit, onUpdateExpo: (String, Float) -> Unit) {
    Surface(color = Color(0x1AFFFFFF), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).background(Color.Black, RoundedCornerShape(4.dp))) {
                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    val w = size.width; val h = size.height
                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(w/2, 0f), Offset(w/2, h), 1f)
                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, h/2), Offset(w, h/2), 1f)
                    val path = Path()
                    for (i in 0..20) {
                        val x = (i / 10f) - 1f
                        val absX = abs(x)
                        val y = sign(x) * ((1f - expo) * absX + expo * absX.pow(3)) * (rate / 2f)
                        val screenX = (x + 1f) / 2f * w
                        val screenY = (1f - (y + 1f) / 2f) * h
                        if (i == 0) path.moveTo(screenX, screenY) else path.lineTo(screenX, screenY)
                    }
                    drawPath(path, Color.Cyan, style = Stroke(1.5.dp.toPx()))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R:${String.format(Locale.US, "%.1f", rate)}", color = Color.Gray, fontSize = 8.sp, modifier = Modifier.width(35.dp))
                    Slider(value = rate, onValueChange = { onUpdateRate(key, it) }, valueRange = 0.1f..2.0f, modifier = Modifier.height(14.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("E:${String.format(Locale.US, "%.1f", expo)}", color = Color.Gray, fontSize = 8.sp, modifier = Modifier.width(35.dp))
                    Slider(value = expo, onValueChange = { onUpdateExpo(key, it) }, valueRange = 0.0f..1.0f, modifier = Modifier.height(14.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan))
                }
            }
        }
    }
}
