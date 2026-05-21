package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import kotlinx.coroutines.delay

/**
 * [v1.5.2] 系統狀態覆蓋層 (動態訊息中心)
 * 職責：顯示來自 SafetyManager 或 ViewModel 的重要警報與通知。
 */
@Composable
fun SystemStatusOverlay(
    state: DroneState,
    stickState: StickInputState
) {
    val rawMsg = state.systemMessage
    if (rawMsg.isNullOrBlank()) return

    // 1. 動態轉譯邏輯
    val parts = rawMsg.split("|")
    val msgId = parts[0]
    val param = parts.getOrNull(1)?.toFloatOrNull() ?: 0f

    val displayMessage = when(msgId) {
        "SAFETY_PASS" -> stringResource(R.string.safety_msg_pass)
        "SAFETY_WARN_OFF" -> stringResource(R.string.safety_msg_warn_off)
        "SAFETY_WARN_THROTTLE" -> stringResource(R.string.safety_msg_warn_throttle, param)
        "SAFETY_WARN_HOLD" -> stringResource(R.string.safety_msg_warn_hold)
        "SAFETY_HOLD_READY" -> stringResource(R.string.safety_msg_hold_ready)
        "FLIGHT_MODE_SWITCH" -> {
            val modeName = when(parts.getOrNull(1)) {
                "ACRO" -> stringResource(R.string.safety_flight_mode_acro)
                "POS" -> stringResource(R.string.safety_flight_mode_pos)
                else -> stringResource(R.string.safety_flight_mode_atti)
            }
            stringResource(R.string.safety_flight_mode_prefix, modeName)
        }
        "SYS_CANCEL" -> stringResource(R.string.sys_msg_cancel)
        "LOGCAT_REQ" -> stringResource(R.string.sys_msg_logcat_required)
        "PERM_REQ" -> stringResource(R.string.sys_msg_perm_required)
        "INIT_DATA" -> stringResource(R.string.sys_msg_init_data)
        "INTERNAL_HW" -> stringResource(R.string.sys_msg_internal_hw)
        "HID_FALLBACK" -> stringResource(R.string.sys_msg_hid_fallback)
        "HEAVY_LANDING" -> stringResource(R.string.sys_msg_heavy_landing, param)
        "ALT_LIMIT" -> stringResource(R.string.sys_msg_alt_limit)
        "WIZARD_DONE" -> stringResource(R.string.sys_msg_wizard_done)
        "APP_NOT_INSTALLED" -> stringResource(R.string.status_app_not_installed)
        "APP_NOT_SUPPORTED" -> stringResource(R.string.status_app_not_supported)
        "EXPERT_ACTIVE" -> stringResource(R.string.settings_expert_active)
        "CRASH_EXTREME" -> stringResource(R.string.sys_msg_crash_extreme, param)
        "CRASH_STRUCTURAL" -> stringResource(R.string.sys_msg_crash_structural, param)
        "RESTORE_DONE" -> stringResource(R.string.sys_msg_restore_done)
        "PERM_REQUIRED" -> stringResource(R.string.sys_msg_perm_required)
        "SAFETY_ARMED" -> stringResource(R.string.safety_msg_armed)
        "SAFETY_DISARMED" -> stringResource(R.string.safety_msg_disarmed)
        "SAFETY_AUTO_DISARM" -> stringResource(R.string.safety_msg_auto_disarm)
        else -> rawMsg
    }

    // 2. 自動消失邏輯
    LaunchedEffect(rawMsg) {
        delay(3000)
        if (state.systemMessage == rawMsg) {
            state.systemMessage = null
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        SurfaceWrapper(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isWarn = rawMsg.contains("⚠️") || rawMsg.contains("SAFETY_WARN") || rawMsg.contains("HEAVY_LANDING")
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isWarn) Color.Red else Color.Cyan, RoundedCornerShape(50))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = displayMessage,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SurfaceWrapper(
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
    border: BorderStroke,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(color, shape)
            .border(border, shape)
    ) {
        content()
    }
}
