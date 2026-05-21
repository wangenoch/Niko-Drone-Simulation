package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.2.81 階段三] 智慧故障排除引導視窗
 */
@Composable
fun TroubleshootingOverlay(
    onOpenFactoryApp: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.Yellow,
                    modifier = Modifier.size(40.dp)
                )
                
                Text(
                    stringResource(R.string.trouble_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Text(
                    stringResource(R.string.trouble_desc),
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 步驟一
                TroubleStep(1, stringResource(R.string.trouble_step1_t), stringResource(R.string.trouble_step1_d))
                
                // 步驟二
                TroubleStep(2, stringResource(R.string.trouble_step2_t), stringResource(R.string.trouble_step2_d))
                
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconStateSample(true, stringResource(R.string.trouble_status_ok))
                    IconStateSample(false, stringResource(R.string.trouble_status_err))
                }
                
                Text(
                    stringResource(R.string.trouble_warn_reboot),
                    color = Color(0xFFFFCC00),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 步驟三
                TroubleStep(3, stringResource(R.string.trouble_step3_t), stringResource(R.string.trouble_step3_d))

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.Gray)
                    ) {
                        Text(stringResource(R.string.action_cancel), color = Color.White)
                    }
                    Button(
                        onClick = onOpenFactoryApp,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                    ) {
                        Text(stringResource(R.string.action_open_factory_app), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TroubleStep(number: Int, title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.Cyan, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(number.toString(), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(desc, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(start = 28.dp, top = 4.dp))
    }
}

@Composable
private fun IconStateSample(isLit: Boolean, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp, 40.dp)
                .background(if (isLit) Color(0xFF2A2A2A) else Color(0xFF111111), RoundedCornerShape(4.dp))
                .border(1.dp, if (isLit) Color.Cyan.copy(0.5f) else Color.White.copy(0.1f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            // 這裡模擬 Icon 視覺
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(16.dp).background(if (isLit) Color.Cyan else Color.DarkGray, RoundedCornerShape(4.dp)))
                Box(Modifier.size(16.dp).background(if (isLit) Color.Cyan else Color.DarkGray, RoundedCornerShape(2.dp)))
            }
        }
        Text(label, color = if (isLit) Color.Cyan else Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
