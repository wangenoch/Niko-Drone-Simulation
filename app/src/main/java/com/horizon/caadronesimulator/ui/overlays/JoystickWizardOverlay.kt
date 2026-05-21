package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.2.68] 獨立的搖桿引導設定 (Wizard) 圖層
 */
@Composable
fun JoystickWizardOverlay(
    setupWizardStep: Int,
    isWizardWaiting: Boolean,
    wizardCountdown: Int,
    stickLX: Float,
    stickLY: Float,
    stickRX: Float,
    stickRY: Float,
    onCancelWizard: () -> Unit
) {
    if (setupWizardStep <= 0) return
    val themeColors = NikoTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .zIndex(1000f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.width(320.dp).wrapContentHeight(),
            color = themeColors.panel,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, themeColors.primary.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.joystick_wizard_title), color = themeColors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                val instr = if (isWizardWaiting) stringResource(R.string.joystick_wizard_waiting, wizardCountdown) else when(setupWizardStep) {
                    1 -> stringResource(R.string.joystick_wizard_step1)
                    2 -> stringResource(R.string.joystick_wizard_step2)
                    3 -> stringResource(R.string.joystick_wizard_step3)
                    4 -> stringResource(R.string.joystick_wizard_step4)
                    else -> ""
                }
                
                Text(
                    text = instr,
                    color = themeColors.textPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
                
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    WizardStickIndicator(
                        isActive = setupWizardStep <= 2,
                        targetX = if (setupWizardStep == 2) 1f else 0f,
                        targetY = if (setupWizardStep == 1) 1f else 0f,
                        currentX = stickLX,
                        currentY = stickLY
                    )
                    WizardStickIndicator(
                        isActive = setupWizardStep >= 3,
                        targetX = if (setupWizardStep == 4) 1f else 0f,
                        targetY = if (setupWizardStep == 3) 1f else 0f,
                        currentX = stickRX,
                        currentY = stickRY
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                TextButton(onClick = onCancelWizard) {
                    Text(stringResource(R.string.action_cancel_wizard), color = themeColors.textSecondary.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun WizardStickIndicator(isActive: Boolean, targetX: Float, targetY: Float, currentX: Float, currentY: Float) {
    val themeColors = NikoTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "wizard_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse"
    )
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "move"
    )
    
    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, if (isActive) themeColors.primary.copy(alpha = 0.5f) else themeColors.textSecondary.copy(alpha = 0.2f), CircleShape)
        )
        if (isActive) {
            Box(modifier = Modifier.fillMaxSize().scale(pulse).border(1.dp, themeColors.primary.copy(alpha = 0.3f), CircleShape))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .offset(x = (targetX * animProgress * 35).dp, y = -(targetY * animProgress * 35).dp)
                    .background(themeColors.textPrimary.copy(alpha = 0.3f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .offset(x = (targetX * 35).dp, y = -(targetY * 35).dp)
                    .background(themeColors.primary, CircleShape)
                    .border(2.dp, themeColors.primary.copy(alpha = 0.3f), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .offset(x = (currentX * 35).dp, y = -(currentY * 35).dp)
                .background(themeColors.textPrimary, CircleShape)
                .border(1.dp, themeColors.textSecondary, CircleShape)
        )
    }
}
