package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.2.68] 發生碰撞後的覆蓋層
 */
@Composable
fun CollisionOverlay(
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = NikoTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background.copy(alpha = 0.8f))
            .clickable(enabled = false) {}, 
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.collision_title),
                color = themeColors.warning,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = { onReset() },
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(200.dp)
                    .height(60.dp)
            ) {
                Text(
                    text = stringResource(R.string.collision_btn_restart),
                    color = if(themeColors.isLight) Color.White else Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
