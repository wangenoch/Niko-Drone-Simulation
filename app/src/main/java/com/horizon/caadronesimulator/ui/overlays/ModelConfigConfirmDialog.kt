package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.ui.common.NikoOverlayCard

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.ui.theme.NikoTheme

@Composable
fun ModelConfigConfirmDialog(
    droneName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // [v1.7.9.15] 改為使用 NikoOverlayCard 以維持沈浸模式
    NikoOverlayCard(
        title = stringResource(R.string.model_config_title),
        onDismiss = onDismiss
    ) {
        Column {
            Text(
                text = stringResource(R.string.model_config_desc, droneName),
                color = NikoTheme.colors.textPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = NikoTheme.colors.textSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = NikoTheme.colors.primary),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_enter_settings), color = if(NikoTheme.colors.isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
