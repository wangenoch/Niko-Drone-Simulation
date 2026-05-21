package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.ui.hud.MiniStickVisual

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.2.68] 獨立的搖桿校準 (Calibration) 圖層
 */
@Composable
fun JoystickCalibrationOverlay(
    isCalibrating: Boolean,
    calibrationStep: Int,
    joystickMode: Int,
    stickLX: Float,
    stickLY: Float,
    stickRX: Float,
    stickRY: Float,
    onNextStep: () -> Unit,
    onFinish: () -> Unit
) {
    if (!isCalibrating) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .zIndex(1100f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF222222),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF4CAF50))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.joystick_calib_title, calibrationStep),
                    color = Color(0xFF4CAF50),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    // 這裡呼叫原本在 JoystickMappingScreen 中的 MiniStickVisual
                    MiniStickVisual(joystickMode, true, stickLX, stickLY)
                    MiniStickVisual(joystickMode, false, stickRX, stickRY)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { if (calibrationStep == 1) onNextStep() else onFinish() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text(if (calibrationStep == 1) stringResource(R.string.action_confirm_neutral) else stringResource(R.string.action_save_finish))
                }
            }
        }
    }
}
