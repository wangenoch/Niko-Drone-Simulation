package com.horizon.caadronesimulator.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.5.3] 統一風格確認對話框
 */
@Composable
fun NikoConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            color = NikoTheme.colors.panel,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, NikoTheme.colors.divider)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, color = NikoTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text(message, color = NikoTheme.colors.textSecondary, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = NikoTheme.colors.textSecondary, fontSize = 15.sp) }
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = onConfirm) { 
                        Text(stringResource(R.string.action_ok), color = NikoTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
