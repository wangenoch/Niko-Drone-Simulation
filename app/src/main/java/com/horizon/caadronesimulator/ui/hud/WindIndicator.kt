package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.7.7] 1:1 復刻 v1.6.3 經典導航箭頭樣式
 * 職責：精確重現「匿蹤式箭頭 (Notched Arrow)」，與截圖完全一致。
 */
@Composable
fun WindIndicator(
    level: Int,
    direction: String,
    currentAngle: Float = 0f,
    modifier: Modifier = Modifier
) {
    val theme = NikoTheme
    val isNoWind = level == 0
    
    // [主題適配] 根據明暗模式自動切換顏色，確保在白色背景下依然清晰
    val indicatorColor = if (isNoWind) theme.colors.textSecondary.copy(alpha = 0.5f) else theme.colors.primary
    val textColor = theme.colors.textPrimary
    val borderColor = theme.colors.textPrimary.copy(alpha = 0.2f)

    // [v1.7.7] 物理極性校正：
    // 物理引擎輸出的 currentAngle 是風的「流向」(Flow Direction)
    // 東風 (From East) 的流向是 270 度。
    // 指標箭頭應直接指向流向，文字盤則需與之鏡像對稱
    val rotation = if (direction == AppConfig.WIND_DIR_RANDOM) currentAngle - 180f
                   else getStaticAngle(direction) + 180f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        // --- 核心組件：圓框 + 匿蹤式箭頭 ---
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(1.dp, borderColor, CircleShape)
                .background(theme.colors.panel.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!isNoWind) {
                // 1:1 復刻 1.6.3 帶有內凹背部的箭頭
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.65f)
                        .rotate(rotation),
                    contentAlignment = Alignment.Center
                ) {
                    val stealthArrowShape = GenericShape { size, _ ->
                        val w = size.width
                        val h = size.height
                        moveTo(w / 2f, 0f)
                        lineTo(w, h)
                        lineTo(w / 2f, h * 0.75f)
                        lineTo(0f, h)
                        close()
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(stealthArrowShape)
                            .background(indicatorColor)
                    )
                }
            } else {
                Box(modifier = Modifier.size(2.dp).background(theme.colors.textSecondary.copy(alpha = 0.5f), CircleShape))
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // --- 文字資訊：1:1 復刻 L + 等級 佈局 (主題同步色) ---
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = if (isNoWind) stringResource(R.string.hud_no_wind) else "L$level",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 13.sp
            )
            if (!isNoWind) {
                val dirLabel = getDirLabel(direction)
                Text(
                    text = dirLabel,
                    color = textColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

private fun getStaticAngle(direction: String): Float = when (direction) {
    AppConfig.WIND_DIR_N -> 0f
    AppConfig.WIND_DIR_NE -> 45f
    AppConfig.WIND_DIR_E -> 90f
    AppConfig.WIND_DIR_SE -> 135f
    AppConfig.WIND_DIR_S -> 180f
    AppConfig.WIND_DIR_SW -> 225f
    AppConfig.WIND_DIR_W -> 270f
    AppConfig.WIND_DIR_NW -> 315f
    else -> 0f
}

@Composable
private fun getDirLabel(direction: String): String = when (direction) {
    AppConfig.WIND_DIR_N -> stringResource(R.string.climate_dir_n)
    AppConfig.WIND_DIR_NE -> stringResource(R.string.climate_dir_ne)
    AppConfig.WIND_DIR_E -> stringResource(R.string.climate_dir_e)
    AppConfig.WIND_DIR_SE -> stringResource(R.string.climate_dir_se)
    AppConfig.WIND_DIR_S -> stringResource(R.string.climate_dir_s)
    AppConfig.WIND_DIR_SW -> stringResource(R.string.climate_dir_sw)
    AppConfig.WIND_DIR_W -> stringResource(R.string.climate_dir_w)
    AppConfig.WIND_DIR_NW -> stringResource(R.string.climate_dir_nw)
    AppConfig.WIND_DIR_RANDOM -> "🎲"
    else -> ""
}
