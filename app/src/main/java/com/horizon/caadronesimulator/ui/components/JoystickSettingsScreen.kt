package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

@Composable
fun JoystickSettingsScreen(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenCalibration: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE111111))
            .clickable(enabled = false) {} // 阻止點擊穿透
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "搖桿模式設定",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    Button(
                        onClick = onOpenCalibration,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("搖桿校準")
                    }
                    Button(
                        onClick = onOpenAdvanced,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFFF).copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("實體搖桿高級映射")
                    }
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("返回")
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModeCard("Mode 1 (日本手)", "左桿：升降/航向\n右桿：油門/側飛", 1, currentMode == 1) { onModeSelected(1) }
                ModeCard("Mode 2 (美國手)", "左桿：油門/航向\n右桿：升降/側飛", 2, currentMode == 2) { onModeSelected(2) }
                ModeCard("Mode 3 (中國手)", "左桿：升降/側飛\n右桿：油門/航向", 3, currentMode == 3) { onModeSelected(3) }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Surface(
                color = Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("提示：", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Text(
                        "切換模式會立即改變虛擬搖桿與實體手把的映射關係。建議在地面且馬達鎖定狀態下進行調整。",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.ModeCard(title: String, desc: String, mode: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(180.dp)
            .background(
                if (isSelected) Color(0xFF00BFFF).copy(alpha = 0.2f) else Color(0x22FFFFFF),
                RoundedCornerShape(16.dp)
            )
            .border(
                2.dp,
                if (isSelected) Color(0xFF00BFFF) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(desc, color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp)
            
            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    Text("已選擇", color = Color(0xFF00BFFF), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
