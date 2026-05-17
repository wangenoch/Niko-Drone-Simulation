package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Anemometer(level: Int, direction: String) {
    Surface(
        color = Color(0xAA000000),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
        modifier = Modifier.width(120.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("風速計", color = Color.LightGray, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(direction, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                val rotation = when(direction) {
                    "北風" -> 180f; "南風" -> 0f; "東風" -> 270f; "西風" -> 90f; else -> 0f
                }
                if (direction != "無" && direction != "亂吹") {
                    Text("↓", color = Color.Cyan, modifier = Modifier.rotate(rotation), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                } else if (direction == "亂吹") {
                    Text("🌀", fontSize = 12.sp)
                }
            }
            Text("$level 級", color = if(level > 3) Color.Red else Color.Green, fontSize = 18.sp, fontWeight = FontWeight.Black)
            
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.Gray)) {
                Box(modifier = Modifier.fillMaxWidth(level / 5f).fillMaxHeight().background(if(level > 3) Color.Red else Color.Cyan))
            }
        }
    }
}
