package com.horizon.caadronesimulator.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.AppConfig

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.6.1] 氣候與物理設定分頁 (佈局優化版)
 */
@Composable
fun ClimateSettingsScreen(
    windLevel: Int,
    windDirection: String, // ID
    windVariation: Int,
    windDirVariation: Int,
    timeOfDay: String, // ID
    shadowIntensity: Float,
    enableVerticalDraft: Boolean,
    useHardcorePhysics: Boolean, 
    isSunSimEnabled: Boolean,
    sunPosition: Float,
    showClouds: Boolean,
    cloudDensity: Float,
    weatherMode: Int,
    onUpdateWindLevel: (Int) -> Unit,
    onUpdateWindDirection: (String) -> Unit,
    onUpdateWindVariation: (Int) -> Unit,
    onUpdateWindDirVariation: (Int) -> Unit,
    onUpdateTimeOfDay: (String) -> Unit,
    onUpdateShadowIntensity: (Float) -> Unit,
    onToggleVerticalDraft: (Boolean) -> Unit,
    onToggleHardcorePhysics: (Boolean) -> Unit,
    onToggleSunSim: (Boolean) -> Unit,
    onUpdateSunPosition: (Float) -> Unit,
    onToggleClouds: (Boolean) -> Unit,
    onUpdateCloudDensity: (Float) -> Unit,
    onUpdateWeatherMode: (Int) -> Unit,
    showMountains: Boolean,
    onToggleMountains: (Boolean) -> Unit,
    onSave: () -> Unit = {},
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> },
    onRerollWind: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 左側：【物理氣流專區】
        Surface(
            modifier = Modifier.weight(1.2f),
            color = Color(0x1AFFFFFF),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.climate_wind_physics), color = Color.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(8.dp))
                
                // 風力等級
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onGloballyPositioned { onTargetPositioned("wind_level", it.boundsInRoot()) }
                ) {
                    Text("${stringResource(R.string.climate_label_level)}: $windLevel", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (0..5).forEach { level ->
                            CompactChip(
                                text = "$level", 
                                selected = windLevel == level, 
                                modifier = Modifier.weight(1f),
                                onClick = { 
                                    onUpdateWindLevel(level)
                                    if (level == 0) onUpdateWindDirection("無")
                                    onSave() 
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // [智慧連動] 僅在風力 > 0 時顯示方位九宮格與拉桿
                AnimatedVisibility(visible = windLevel > 0) {
                    Column {
                        Text(stringResource(R.string.climate_wind_directions), color = Color.White.copy(0.7f), fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                        
                        // 3x3 九宮格佈局
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.onGloballyPositioned { onTargetPositioned("wind_dir", it.boundsInRoot()) }
                        ) {
                            val rows = listOf(
                                listOf(AppConfig.WIND_DIR_NW to stringResource(R.string.climate_dir_nw), AppConfig.WIND_DIR_N to stringResource(R.string.climate_dir_n), AppConfig.WIND_DIR_NE to stringResource(R.string.climate_dir_ne)),
                                listOf(AppConfig.WIND_DIR_W to stringResource(R.string.climate_dir_w), AppConfig.WIND_DIR_RANDOM to stringResource(R.string.climate_dir_random), AppConfig.WIND_DIR_E to stringResource(R.string.climate_dir_e)),
                                listOf(AppConfig.WIND_DIR_SW to stringResource(R.string.climate_dir_sw), AppConfig.WIND_DIR_S to stringResource(R.string.climate_dir_s), AppConfig.WIND_DIR_SE to stringResource(R.string.climate_dir_se))
                            )
                            rows.forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    rowItems.forEach { (id, label) ->
                                        CompactChip(
                                            text = if(id == AppConfig.WIND_DIR_RANDOM) "🎲" else label.replace("風", ""),
                                            selected = windDirection == id,
                                            modifier = Modifier.weight(1f),
                                            selectedColor = if(id == AppConfig.WIND_DIR_RANDOM) Color.Yellow else Color.Cyan,
                                            onClick = { 
                                                if (id == AppConfig.WIND_DIR_RANDOM) onRerollWind()
                                                onUpdateWindDirection(id); onSave() 
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // 氣流微調區 (僅在有風向時顯示)
                        if (windDirection != AppConfig.WIND_DIR_NONE) {
                            Text(stringResource(R.string.climate_wind_tuning), color = Color.White.copy(0.7f), fontSize = 10.sp)
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .onGloballyPositioned { onTargetPositioned("wind_var", it.boundsInRoot()) }
                            ) {
                                WindSliderRow(stringResource(R.string.climate_wind_var), windVariation.toFloat(), { onUpdateWindVariation(it.toInt()) }, onSave)
                                WindSliderRow(stringResource(R.string.climate_dir_var), windDirVariation.toFloat(), { onUpdateWindDirVariation(it.toInt()) }, onSave)
                            }
                        }
                    }
                }

                // [v1.6.1] 物理核心開關組：獨立於風力等級之外，始終顯示
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0x11FFFFFF))
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WindToggleRow(stringResource(R.string.climate_vertical_draft), enableVerticalDraft, onToggleVerticalDraft, onSave)
                    WindToggleRow(stringResource(R.string.climate_hardcore_physics), useHardcorePhysics, onToggleHardcorePhysics, onSave)
                }
            }
        }

        // 右側：【視覺氣氛專區】
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0x1AFFFFFF),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.climate_visual_details), color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                // 太陽方位
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.climate_sun_pos), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Switch(checked = isSunSimEnabled, onCheckedChange = { onToggleSunSim(it); onSave() }, modifier = Modifier.scale(0.5f))
                }
                if (isSunSimEnabled) {
                    Slider(value = sunPosition, onValueChange = onUpdateSunPosition, onValueChangeFinished = onSave, valueRange = 0f..1f, modifier = Modifier.height(24.dp), colors = SliderDefaults.colors(thumbColor = Color.Yellow, activeTrackColor = Color.Yellow.copy(0.5f)))
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { onTargetPositioned("time", it.boundsInRoot()) },
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val times = listOf(
                            AppConfig.TIME_MORNING to stringResource(R.string.climate_time_morning),
                            AppConfig.TIME_NOON to stringResource(R.string.climate_time_noon),
                            AppConfig.TIME_AFTERNOON to stringResource(R.string.climate_time_afternoon)
                        )
                        times.forEach { (id, label) ->
                            CompactChip(text = label, selected = timeOfDay == id, modifier = Modifier.weight(1f), selectedColor = Color.Yellow, onClick = { onUpdateTimeOfDay(id); onSave() })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                
                // 陰影深淺
                Text(stringResource(R.string.climate_shadow_intensity, shadowIntensity), color = Color.White, fontSize = 10.sp)
                Slider(
                    value = shadowIntensity, 
                    onValueChange = onUpdateShadowIntensity, 
                    onValueChangeFinished = onSave, 
                    modifier = Modifier
                        .height(24.dp)
                        .onGloballyPositioned { onTargetPositioned("shadow", it.boundsInRoot()) }, 
                    colors = SliderDefaults.colors(thumbColor = Color.Yellow)
                )

                HorizontalDivider(color = Color(0x11FFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                // 氣象預設
                Text(stringResource(R.string.climate_weather_preset), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val weathers = listOf(stringResource(R.string.climate_weather_clear), stringResource(R.string.climate_weather_cirrus), stringResource(R.string.climate_weather_cumulus), stringResource(R.string.climate_weather_stratus))
                    weathers.forEachIndexed { idx, name ->
                        CompactChip(
                            text = name, 
                            selected = weatherMode == idx, 
                            modifier = Modifier.weight(1f),
                            selectedColor = Color(0xFFFFC107),
                            onClick = { 
                                onUpdateWeatherMode(idx)
                                val hasCloud = idx > 0
                                onToggleClouds(hasCloud)
                                if (hasCloud) {
                                    val density = when(idx) { 1 -> 0.3f; 2 -> 0.6f; 3 -> 0.9f; else -> 0.5f }
                                    onUpdateCloudDensity(density)
                                }
                                onSave() 
                            }
                        )
                    }
                }

                // 雲層密度
                AnimatedVisibility(visible = weatherMode > 0) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.climate_cloud_density), color = Color.Gray, fontSize = 10.sp)
                        Slider(value = cloudDensity, onValueChange = onUpdateCloudDensity, onValueChangeFinished = onSave, valueRange = 0.1f..1f, modifier = Modifier.height(24.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 功能開關組 (視覺類)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.climate_show_mountains), color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Switch(checked = showMountains, onCheckedChange = { onToggleMountains(it); onSave() }, modifier = Modifier.scale(0.5f))
                }
            }
        }
    }
}

@Composable
private fun WindSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(22.dp)) {
        Text(label, color = Color.White.copy(0.6f), fontSize = 9.sp, modifier = Modifier.width(45.dp))
        Slider(
            value = value, onValueChange = onValueChange, onValueChangeFinished = onSave, 
            valueRange = 0f..5f, steps = 4, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan.copy(0.4f))
        )
    }
}

@Composable
private fun WindToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(26.dp)) {
        Text(label, color = Color.White.copy(0.8f), fontSize = 10.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, 
            onCheckedChange = { onToggle(it); onSave() }, 
            modifier = Modifier.scale(0.45f),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan)
        )
    }
}

@Composable
fun CompactChip(text: String, selected: Boolean, modifier: Modifier = Modifier, selectedColor: Color = Color.Cyan, onClick: () -> Unit) {
    Surface(
        color = if (selected) selectedColor else Color(0x22FFFFFF),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.height(24.dp).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, color = if (selected) Color.Black else Color.White, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
