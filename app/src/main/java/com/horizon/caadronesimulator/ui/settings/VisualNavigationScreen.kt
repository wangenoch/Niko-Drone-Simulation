package com.horizon.caadronesimulator.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.5.9] 視訊導航與介面配置頁面
 */
@Composable
fun VisualNavigationScreen(
    cameraMode: String, // 現在是 ID
    mainFOV: Float,
    zoomFactor: Float,
    showSpecialTitle: Boolean,
    currentTitleText: String,
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
    onUpdateSpecialTitle: (String) -> Unit,
    onToggleSideSliders: (Boolean) -> Unit,
    onToggleSideRulers: (Boolean) -> Unit,
    onToggleReverseSliders: (Boolean) -> Unit,
    onToggleGroundAnchor: (Boolean) -> Unit,
    onTogglePiPRelocate: (Boolean) -> Unit,
    onToggleZoomAssistant: (Boolean) -> Unit,
    onSave: () -> Unit = {},
    onManualInteraction: () -> Unit = {}
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
            color = NikoTheme.colors.textPrimary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(stringResource(R.string.visual_camera_section), color = NikoTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(stringResource(R.string.menu_camera_mode), color = NikoTheme.colors.textSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                
                var expanded by remember { mutableStateOf(false) }
                val modes = listOf(
                    AppConfig.CAM_MODE_STATION_TRACK to stringResource(R.string.visual_cam_mode_station_track),
                    AppConfig.CAM_MODE_STATION_SMART to stringResource(R.string.visual_cam_mode_station_smart),
                    AppConfig.CAM_MODE_STATION_FIXED to stringResource(R.string.visual_cam_mode_station_fixed),
                    AppConfig.CAM_MODE_FOLLOW to stringResource(R.string.visual_cam_mode_follow),
                    AppConfig.CAM_MODE_FPV to stringResource(R.string.visual_cam_mode_fpv),
                    AppConfig.CAM_MODE_OBS to stringResource(R.string.visual_cam_mode_obs)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable { expanded = true },
                        color = NikoTheme.colors.textPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NikoTheme.colors.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val currentLabel = modes.find { it.first == cameraMode }?.second ?: cameraMode
                            Text(text = currentLabel, color = NikoTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, null, tint = NikoTheme.colors.primary, modifier = Modifier.size(20.dp))
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .background(NikoTheme.colors.panel)
                            .border(1.dp, NikoTheme.colors.divider, RoundedCornerShape(8.dp))
                    ) {
                        modes.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = if(cameraMode == id) NikoTheme.colors.primary else NikoTheme.colors.textPrimary, fontSize = 13.sp) },
                                onClick = {
                                    onUpdateCameraMode(id)
                                    expanded = false
                                    onSave()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.visual_label_fov), color = NikoTheme.colors.textPrimary, fontSize = 11.sp)
                        Text("${mainFOV.toInt()}°", color = NikoTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = mainFOV,
                        onValueChange = { onUpdateFOV(it); onManualInteraction() },
                        onValueChangeFinished = onSave,
                        valueRange = 30f..110f,
                        modifier = Modifier.weight(2f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = NikoTheme.colors.primary, activeTrackColor = NikoTheme.colors.primary, inactiveTrackColor = NikoTheme.colors.divider)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.visual_label_zoom), color = NikoTheme.colors.textPrimary, fontSize = 11.sp)
                        Text("${String.format(Locale.US, "%.1f", zoomFactor)}x", color = NikoTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = zoomFactor,
                        onValueChange = { onUpdateZoom(it); onManualInteraction() },
                        onValueChangeFinished = onSave,
                        valueRange = 0.5f..4.0f,
                        modifier = Modifier.weight(2f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = NikoTheme.colors.primary, activeTrackColor = NikoTheme.colors.primary, inactiveTrackColor = NikoTheme.colors.divider)
                    )
                }
            }
        }

        // 2. 🖥️ HUD 介面元素
        Surface(
            modifier = Modifier.weight(1f),
            color = NikoTheme.colors.textPrimary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(stringResource(R.string.visual_hud_section), color = Color(0xFF2196F3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(32.dp)) {
                    Text(stringResource(R.string.visual_special_title), color = NikoTheme.colors.textPrimary.copy(0.9f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    if (showSpecialTitle) {
                        Icon(Icons.Default.Edit, null, tint = NikoTheme.colors.safety.copy(0.6f), modifier = Modifier.size(12.dp).padding(end = 4.dp).clickable { showTitleDialog = true })
                    }
                    Switch(
                        checked = showSpecialTitle,
                        onCheckedChange = { 
                            onToggleSpecialTitle(it)
                            if (it) showTitleDialog = true 
                            onSave()
                        },
                        modifier = Modifier.scale(0.55f),
                        colors = SwitchDefaults.colors(checkedThumbColor = NikoTheme.colors.primary)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(24.dp)) {
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.visual_label_zoom_short), color = NikoTheme.colors.textSecondary, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onToggleSpecialTitle(true); showTitleDialog = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                        Text(currentTitleText.ifBlank { stringResource(R.string.visual_label_auto) }, color = NikoTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                VisualSwitchItem(stringResource(R.string.visual_side_sliders), showSideSliders, onToggleSideSliders, onSave)
                VisualSwitchItem(stringResource(R.string.visual_side_rulers), showSideRulers, onToggleSideRulers, onSave)
                VisualSwitchItem(stringResource(R.string.visual_reverse_sliders), reverseSliderSides, onToggleReverseSliders, onSave)
                
                Spacer(modifier = Modifier.height(12.dp))
                VisualSwitchItem(stringResource(R.string.visual_enable_zoom_assistant), enableZoomAssistant, onToggleZoomAssistant, onSave)
            }
        }

        // 3. 🛰️ 導航輔助
        Surface(
            modifier = Modifier.weight(0.9f),
            color = NikoTheme.colors.textPrimary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(stringResource(R.string.visual_nav_section), color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                VisualSwitchItem(stringResource(R.string.visual_ground_anchor), showGroundAnchor, onToggleGroundAnchor, onSave)
                VisualSwitchItem(stringResource(R.string.visual_pip_relocate), autoPiPRelocate, onTogglePiPRelocate, onSave)
                
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.visual_nav_hint), color = NikoTheme.colors.textSecondary, fontSize = 9.sp)
            }
        }
    }

    // 編輯對話框
    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text(stringResource(R.string.visual_edit_title), color = NikoTheme.colors.textPrimary, fontSize = 16.sp) },
            text = {
                Column {
                    Text(stringResource(R.string.visual_edit_title_desc), color = NikoTheme.colors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentTitleText,
                        onValueChange = { onUpdateSpecialTitle(it) },
                        placeholder = { Text(stringResource(R.string.visual_edit_title_placeholder), color = NikoTheme.colors.textSecondary.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NikoTheme.colors.primary, 
                            unfocusedBorderColor = NikoTheme.colors.divider,
                            focusedTextColor = NikoTheme.colors.textPrimary,
                            unfocusedTextColor = NikoTheme.colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showTitleDialog = false; onSave() }, colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.primary)) {
                    Text(stringResource(R.string.action_apply), color = if(NikoTheme.colors.isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = NikoTheme.colors.panel
        )
    }
}

@Composable
fun VisualSwitchItem(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(32.dp)) {
        Text(label, color = NikoTheme.colors.textPrimary.copy(0.9f), fontSize = 11.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it); onSave() },
            modifier = Modifier.scale(0.55f),
            colors = SwitchDefaults.colors(checkedThumbColor = NikoTheme.colors.primary)
        )
    }
}
