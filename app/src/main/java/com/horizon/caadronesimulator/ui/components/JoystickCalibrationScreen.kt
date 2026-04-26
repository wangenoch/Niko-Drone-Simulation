package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JoystickCalibrationScreen(
    onStartCalibration: () -> Unit,
    onFinishCalibration: () -> Unit,
    isCalibrating: Boolean,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE111111))
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("搖桿校準", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onBack, 
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("返回", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(0.85f),
                color = Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!isCalibrating) {
                        Text("校準說明", color = Color.Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "1. 點擊「開始校準」\n" +
                            "2. 將左右搖桿向所有極限角落繞圈\n" +
                            "3. 放開搖桿讓其回歸中位\n" +
                            "4. 點擊「完成校準」",
                            color = Color.White,
                            lineHeight = 20.sp,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onStartCalibration,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("開始校準", fontSize = 15.sp)
                        }
                    } else {
                        CircularProgressIndicator(color = Color.Cyan, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("正在記錄極限值與中位點...", color = Color.White, fontSize = 15.sp)
                        Text("請繼續撥動搖桿，最後放手回正", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onFinishCalibration,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
                        ) {
                            Text("完成校準並儲存", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
