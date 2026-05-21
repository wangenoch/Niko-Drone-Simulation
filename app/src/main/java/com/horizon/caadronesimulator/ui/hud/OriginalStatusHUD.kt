package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneRegistry
import java.util.Locale
import kotlin.math.*

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.1 原始整合風格] 底部飛行狀態欄
 */
@Composable
fun OriginalStatusHUD(
    state: DroneState,
    isVisible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) {
        // [修正] 當隱藏時顯示小展開按鈕，確保使用者能再次開啟數據欄
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(44.dp, 20.dp)
                    .background(NikoTheme.colors.panel.copy(alpha = 0.8f), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .border(1.dp, NikoTheme.colors.divider, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .clickable { onToggleVisible() },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.menu_expand),
                    tint = NikoTheme.colors.textPrimary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding() 
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(NikoTheme.colors.panel.copy(alpha = 0.85f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .border(1.dp, NikoTheme.colors.divider, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable { onToggleVisible() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val spec = DroneRegistry.getSpec(state.droneType)
            val relativeAltitude = (state.altitude - spec.groundOffset).coerceAtLeast(0f)
            
            // --- 1. 基本數據組 (左) ---
            OriginalStatusItem(stringResource(R.string.hud_altitude), String.format(Locale.US, "%.1f", relativeAltitude), stringResource(R.string.hud_unit_m), if(relativeAltitude >= 29.8f) NikoTheme.colors.warning else NikoTheme.colors.accent)
            OriginalStatusVerticalDivider()
            OriginalStatusItem(stringResource(R.string.hud_speed), String.format(Locale.US, "%.1f", state.speed), stringResource(R.string.hud_unit_ms), NikoTheme.colors.textPrimary)
            OriginalStatusVerticalDivider()
            
            // [v1.7.6] 統一距離顯示：使用全域校準後的 horizontalDist (基準點 H 坪 0,0)
            OriginalStatusItem(stringResource(R.string.hud_distance), String.format(Locale.US, "%.1f", state.horizontalDist), stringResource(R.string.hud_unit_m), if (state.isNearBoundary) NikoTheme.colors.warning else NikoTheme.colors.textPrimary)
            
            OriginalStatusVerticalDivider()

            // --- 2. 風向羅盤組 (中) ---
            WindIndicator(
                level = state.windLevel,
                direction = state.windDirection,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            OriginalStatusVerticalDivider()

            // --- 3. 電池電量組 (中右) ---
            BatteryIndicator(
                percent = state.batteryPercent,
                voltage = state.batteryVoltage,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            OriginalStatusVerticalDivider()

            // --- 4. 馬達狀態組 (右) ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.hud_motor), color = NikoTheme.colors.textSecondary, fontSize = 8.sp)
                Text(
                    text = if(state.isMotorLocked) stringResource(R.string.hud_motor_locked) else stringResource(R.string.hud_motor_active),
                    color = if(state.isMotorLocked) NikoTheme.colors.warning else NikoTheme.colors.status,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = NikoTheme.colors.textPrimary.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp).offset(y = 2.dp)
            )
        }
    }
}

@Composable
private fun OriginalStatusItem(label: String, value: String, unit: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(label, color = NikoTheme.colors.textSecondary, fontSize = 8.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(2.dp))
            Text(unit, color = NikoTheme.colors.textPrimary, fontSize = 8.sp)
        }
    }
}

@Composable
private fun OriginalStatusVerticalDivider() {
    Box(modifier = Modifier.width(1.dp).height(18.dp).background(NikoTheme.colors.divider))
}
