package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [v1.4.2] 機型選擇分頁 - 全域捲動優化版
 */
@Composable
fun DroneSelectionScreen(
    currentType: String,
    onTypeSelected: (String) -> Unit
) {
    val config = LocalConfiguration.current
    val isSmallDevice = config.screenWidthDp < 500 && config.screenHeightDp < 300

    // [v1.4.2] 移除 outer Box 與 fillMaxSize，由 UnifiedSettingsScreen 統籌捲動
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DroneTypeCard(
                title = "小型無人機",
                type = "QUAD_STANDARD",
                isSelected = currentType == "QUAD_STANDARD",
                icon = "🚁",
                onClick = { onTypeSelected("QUAD_STANDARD") }
            )
            
            DroneTypeCard(
                title = "JoyFlight T4",
                type = "HEAVY_LIFT",
                isSelected = currentType == "HEAVY_LIFT",
                icon = "🏗️",
                onClick = { onTypeSelected("HEAVY_LIFT") }
            )

            DroneTypeCard(
                title = "重型直昇機",
                type = "HELI_900",
                isSelected = currentType == "HELI_900",
                icon = "🚁",
                onClick = { onTypeSelected("HELI_900") }
            )
        }
        
        if (!isSmallDevice) {
            Surface(
                color = Color(0x22FFFFFF),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    val specTitle = when(currentType) {
                        "HEAVY_LIFT" -> "機型詳細規格 (JoyFlight T4)"
                        "HELI_900" -> "機型詳細規格 (重型直昇機)"
                        else -> "機型詳細規格 (小型練習機)"
                    }
                    val specDetail = when(currentType) {
                        "HEAVY_LIFT" -> "軸距 1100mm / 槳距 24inch / 螺距 8inch / 馬達 150KV\n最大起飛重量 14.9kg / 最大酬載 1kg / 滯空時間約 20min"
                        "HELI_900" -> "軸距 900mm / 槳距 22inch / 螺距 7inch / 馬達 280KV\n機身重量 10kg / 定速飛行模式 / 滯空時間約 15min"
                        else -> "軸距 210mm / 槳距 5inch / 螺距 4inch / 馬達 2450KV\n最大起飛重量 0.6kg / 競速等級動力 / 滯空時間約 5-8min"
                    }
                    val specDesc = when(currentType) {
                        "HEAVY_LIFT" -> "說明：大型負重機型具有更長的力臂與獨特的起落架設計，適合進行進階飛行練習與專業考照模擬。"
                        "HELI_900" -> "說明：採用高重心設計與 22 吋大型旋翼，具備極高的飛行穩定性與獨特的偏航扭力特性。"
                        else -> "說明：高機動性機型，反應極其靈敏，適合練習精準穿越與反應速度，是初學者建立感官回饋的最佳選擇。"
                    }

                    Text(
                        text = specTitle,
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = specDetail,
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0x1AFFFFFF))
                    Text(
                        text = specDesc,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun RowScope.DroneTypeCard(
    title: String,
    type: String,
    isSelected: Boolean,
    icon: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(170.dp)
            .background(
                if (isSelected) Color(0xFF00BFFF).copy(alpha = 0.2f) else Color(0x1AFFFFFF),
                RoundedCornerShape(16.dp)
            )
            .border(
                2.dp,
                if (isSelected) Color(0xFF00BFFF) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(if (isSelected) Color.Cyan else Color.DarkGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (type == "HEAVY_LIFT") {
                    HeavyLiftIcon(modifier = Modifier.size(40.dp))
                } else if (type == "QUAD_STANDARD") {
                    SmallDroneIcon(modifier = Modifier.size(40.dp))
                } else if (type == "HELI_900") {
                    HelicopterIcon(modifier = Modifier.size(40.dp))
                } else {
                    Text(icon, fontSize = 32.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            if (isSelected) {
                Text("當前選擇", color = Color.Cyan, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun HelicopterIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f
        // Main Body
        drawRect(Color(0xFF222222), Offset(cx - w*0.3f, cy - h*0.2f), Size(w*0.5f, h*0.4f))
        // Tail
        drawLine(Color(0xFF222222), Offset(cx - w*0.3f, cy), Offset(cx - w*0.45f, cy), strokeWidth = 4.dp.toPx())
        // Rotors
        drawLine(Color.Gray, Offset(cx - w*0.5f, cy - h*0.3f), Offset(cx + w*0.5f, cy - h*0.3f), strokeWidth = 2.dp.toPx())
        drawCircle(Color.Cyan, radius = 3.dp.toPx(), center = Offset(cx, cy - h*0.3f))
    }
}

@Composable
fun HeavyLiftIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f
        val armColor = Color(0xFF1A1A1A); val armStroke = 3.dp.toPx()
        drawLine(armColor, Offset(cx - w * 0.4f, cy - h * 0.3f), Offset(cx + w * 0.4f, cy + h * 0.3f), strokeWidth = armStroke)
        drawLine(armColor, Offset(cx + w * 0.4f, cy - h * 0.3f), Offset(cx - w * 0.4f, cy + h * 0.3f), strokeWidth = armStroke)
        val propColor = Color(0xAAEEEEEE); val propW = w * 0.35f; val propH = 2.dp.toPx()
        drawRect(propColor, Offset(cx - w * 0.4f - propW/2, cy - h * 0.3f - propH/2), Size(propW, propH))
        drawRect(propColor, Offset(cx + w * 0.4f - propW/2, cy - h * 0.3f - propH/2), Size(propW, propH))
        drawRect(propColor, Offset(cx - w * 0.4f - propW/2, cy + h * 0.3f - propH/2), Size(propW, propH))
        drawRect(propColor, Offset(cx + w * 0.4f - propW/2, cy + h * 0.3f - propH/2), Size(propW, propH))
        val legColor = Color(0xFF111111)
        drawLine(legColor, Offset(cx - w * 0.15f, cy), Offset(cx - w * 0.15f, cy + h * 0.4f), strokeWidth = 3.dp.toPx())
        drawLine(legColor, Offset(cx + w * 0.15f, cy), Offset(cx + w * 0.15f, cy + h * 0.4f), strokeWidth = 3.dp.toPx())
        drawCircle(Color.Red, radius = 3.dp.toPx(), center = Offset(cx - w * 0.15f, cy + h * 0.4f))
        drawCircle(Color(0xFF00FF00), radius = 3.dp.toPx(), center = Offset(cx + w * 0.15f, cy + h * 0.4f))
        drawRect(Color(0xFF121212), Offset(cx - w * 0.22f, cy - h * 0.15f), Size(w * 0.44f, h * 0.35f))
        drawRect(Color(0xFF0066FF), Offset(cx - w * 0.15f, cy - h * 0.22f), Size(w * 0.3f, h * 0.12f))
    }
}

@Composable
fun SmallDroneIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f
        val armColor = Color(0xFF222222)
        drawLine(armColor, Offset(cx - w * 0.35f, cy - h * 0.35f), Offset(cx + w * 0.35f, cy + h * 0.35f), strokeWidth = 4.dp.toPx())
        drawLine(armColor, Offset(cx + w * 0.35f, cy - h * 0.35f), Offset(cx - w * 0.35f, cy + h * 0.35f), strokeWidth = 4.dp.toPx())
        drawCircle(Color.Red, radius = 5.dp.toPx(), center = Offset(cx - w * 0.35f, cy - h * 0.35f))
        drawCircle(Color.Red, radius = 5.dp.toPx(), center = Offset(cx - w * 0.35f, cy + h * 0.35f))
        drawCircle(Color.Green, radius = 5.dp.toPx(), center = Offset(cx + w * 0.35f, cy - h * 0.35f))
        drawCircle(Color.Green, radius = 5.dp.toPx(), center = Offset(cx + w * 0.35f, cy + h * 0.35f))
        drawRect(Color(0xFF222222), Offset(cx - w * 0.15f, cy - h * 0.3f), Size(w * 0.3f, h * 0.6f))
        drawRect(Color(0xFFFF8C00), Offset(cx - w * 0.1f, cy - h * 0.35f), Size(w * 0.2f, h * 0.1f))
    }
}
