package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect

@Composable
fun ClimateSettingsScreen(
    windLevel: Int,
    windDirection: String,
    windVariation: Int,
    windDirVariation: Int,
    timeOfDay: String,
    shadowIntensity: Float,
    enableAirPressure: Boolean,
    onUpdateWindLevel: (Int) -> Unit,
    onUpdateWindDirection: (String) -> Unit,
    onUpdateWindVariation: (Int) -> Unit,
    onUpdateWindDirVariation: (Int) -> Unit,
    onUpdateTimeOfDay: (String) -> Unit,
    onUpdateShadowIntensity: (Float) -> Unit,
    onToggleAirPressure: (Boolean) -> Unit,
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE111111))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header - 縮小高度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("環境與氣候設定", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 左側：風力 (權重調整)
                Surface(
                    modifier = Modifier.weight(1.8f),
                    color = Color(0x1AFFFFFF),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("風力與方向", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("氣壓干擾", color = Color.Gray, fontSize = 10.sp)
                            Switch(
                                checked = enableAirPressure,
                                onCheckedChange = onToggleAirPressure,
                                modifier = Modifier.scale(0.5f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.onGloballyPositioned { onTargetPositioned("wind_level", it.boundsInRoot()) }
                        ) {
                            Text("等級: $windLevel", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                (0..5).forEach { level ->
                                    CompactChip(
                                        text = "${level}級",
                                        selected = windLevel == level,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onUpdateWindLevel(level) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("風向選擇", color = Color.White, fontSize = 11.sp)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.onGloballyPositioned { onTargetPositioned("wind_dir", it.boundsInRoot()) }
                        ) {
                            val dirs = listOf("無", "北風", "南風", "東風", "西風", "隨機")
                            dirs.chunked(3).forEach { rowDirs ->
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    rowDirs.forEach { dir ->
                                        CompactChip(
                                            text = dir,
                                            selected = windDirection == dir,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onUpdateWindDirection(dir) }
                                        )
                                    }
                                }
                            }
                        }

                        // 滑桿區優化：改為水平排列節省空間
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(modifier = Modifier.onGloballyPositioned { onTargetPositioned("wind_var", it.boundsInRoot()) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
                                Text("風速激烈", color = Color.White, fontSize = 10.sp, modifier = Modifier.width(50.dp))
                                Slider(
                                    value = windVariation.toFloat(),
                                    onValueChange = { onUpdateWindVariation(it.toInt()) },
                                    valueRange = 0f..5f,
                                    steps = 4,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)
                                )
                            }
                            
                            if (windDirection == "隨機") {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
                                    Text("風向激烈", color = Color.Cyan, fontSize = 10.sp, modifier = Modifier.width(50.dp))
                                    Slider(
                                        value = windDirVariation.toFloat(),
                                        onValueChange = { onUpdateWindDirVariation(it.toInt()) },
                                        valueRange = 0f..5f,
                                        steps = 4,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)
                                    )
                                }
                            }
                        }
                    }
                }

                // 右側：光照
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0x1AFFFFFF),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("環境光照", color = Color.Yellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(modifier = Modifier.onGloballyPositioned { onTargetPositioned("time", it.boundsInRoot()) }) {
                            listOf("早晨", "中午", "下午").forEach { time ->
                                CompactChip(
                                    text = time,
                                    selected = timeOfDay == time,
                                    selectedColor = Color(0xFFFFEB3B),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).height(28.dp),
                                    onClick = { onUpdateTimeOfDay(time) }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("機身陰影深淺", color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = shadowIntensity,
                            onValueChange = onUpdateShadowIntensity,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth().height(24.dp).onGloballyPositioned { onTargetPositioned("shadow", it.boundsInRoot()) },
                            colors = SliderDefaults.colors(thumbColor = Color.Yellow, activeTrackColor = Color.Yellow)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color = Color.Cyan,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) selectedColor else Color(0x33FFFFFF),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .height(22.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) Color.Black else Color.White,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}
