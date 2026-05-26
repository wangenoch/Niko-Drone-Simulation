package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.CrashReason
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.2.68] 發生碰撞或電量耗盡後的覆蓋層
 */
@Composable
fun CollisionOverlay(
    modifier: Modifier = Modifier,
    reason: CrashReason = CrashReason.IMPACT,
    flightTime: Float = 0f,
    onReset: () -> Unit
) {
    val themeColors = NikoTheme.colors
    val isBatteryLow = reason == CrashReason.BATTERY_LOW
    val isOutOfBounds = reason == CrashReason.OUT_OF_BOUNDS
    
    val title = when {
        isBatteryLow -> "🔋 電量耗盡"
        isOutOfBounds -> stringResource(R.string.collision_title_out_of_bounds)
        else -> stringResource(R.string.collision_title)
    }
    val titleColor = if (isBatteryLow) themeColors.safety else Color.Red
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)) // 使用更深的背景增強沈浸感
            .clickable(enabled = false) {}, 
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold
            )
            
            if (isBatteryLow) {
                val infiniteTransition = rememberInfiniteTransition(label = "tip_anim")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = ""
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "飛太久了，該休息一下囉！",
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 飛行統計資訊
            val minutes = (flightTime / 60).toInt().toString()
            val seconds = (flightTime % 60).toInt().toString()
            Text(
                text = stringResource(R.string.stats_practice_duration, minutes, seconds),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = { onReset() },
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(220.dp)
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.collision_btn_restart),
                    color = if(themeColors.isLight) Color.White else Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
