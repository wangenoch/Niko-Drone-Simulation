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

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

import com.horizon.caadronesimulator.model.AppConfig

@Composable
fun Anemometer(level: Int, direction: String) { // direction 是 ID
    val dirLabel = when(direction) {
        AppConfig.WIND_DIR_N -> stringResource(R.string.climate_dir_n)
        AppConfig.WIND_DIR_S -> stringResource(R.string.climate_dir_s)
        AppConfig.WIND_DIR_E -> stringResource(R.string.climate_dir_e)
        AppConfig.WIND_DIR_W -> stringResource(R.string.climate_dir_w)
        AppConfig.WIND_DIR_NE -> stringResource(R.string.climate_dir_ne)
        AppConfig.WIND_DIR_NW -> stringResource(R.string.climate_dir_nw)
        AppConfig.WIND_DIR_SE -> stringResource(R.string.climate_dir_se)
        AppConfig.WIND_DIR_SW -> stringResource(R.string.climate_dir_sw)
        AppConfig.WIND_DIR_RANDOM -> stringResource(R.string.climate_dir_random)
        else -> stringResource(R.string.hud_no_wind)
    }

    Surface(
        color = Color(0xAA000000),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
        modifier = Modifier.width(120.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.hud_environment), color = Color.LightGray, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dirLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                val rotation = when(direction) {
                    AppConfig.WIND_DIR_N -> 180f; AppConfig.WIND_DIR_S -> 0f; AppConfig.WIND_DIR_E -> 270f; AppConfig.WIND_DIR_W -> 90f; else -> 0f
                }
                if (direction != AppConfig.WIND_DIR_NONE && direction != AppConfig.WIND_DIR_RANDOM) {
                    Text("↓", color = Color.Cyan, modifier = Modifier.rotate(rotation), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                } else if (direction == AppConfig.WIND_DIR_RANDOM) {
                    Text("🌀", fontSize = 12.sp)
                }
            }
            Text(stringResource(R.string.hud_wind_level, level), color = if(level > 3) Color.Red else Color.Green, fontSize = 18.sp, fontWeight = FontWeight.Black)
            
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.Gray)) {
                Box(modifier = Modifier.fillMaxWidth(level / 5f).fillMaxHeight().background(if(level > 3) Color.Red else Color.Cyan))
            }
        }
    }
}
