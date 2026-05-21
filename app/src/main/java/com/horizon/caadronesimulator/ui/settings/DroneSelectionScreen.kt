package com.horizon.caadronesimulator.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.DroneRegistry

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.5.3] 機型選擇分頁 - 100% 數據驅動
 */
@Composable
fun DroneSelectionScreen(
    currentType: String,
    onTypeSelected: (String) -> Unit,
    onLongPressType: (String) -> Unit = {},
    state: com.horizon.caadronesimulator.model.DroneState? = null
) {
    val config = LocalConfiguration.current
    val isSmallDevice = config.screenWidthDp < 500 && config.screenHeightDp < 300

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
            val models = DroneRegistry.getAllSpecs()
            models.forEach { spec ->
                val localizedName = when(spec.id) {
                    "QUAD_STANDARD" -> stringResource(R.string.model_name_small)
                    "T4_HEAVY_LIFT" -> stringResource(R.string.model_name_heavy)
                    "HELI_900" -> stringResource(R.string.model_name_heli)
                    else -> spec.name
                }
                DroneTypeCard(
                    title = localizedName,
                    type = spec.id,
                    isSelected = currentType == spec.id,
                    onClick = { onTypeSelected(spec.id) },
                    onLongClick = { onLongPressType(spec.id) },
                    isHoldSupported = spec.isHoldSupported,
                    isHoldEnabled = state?.isThrottleHoldEnabled ?: false,
                    isHoldMapped = (state?.mappingHold?.axis ?: -1) != -1,
                    onToggleHold = { state?.isThrottleHoldEnabled = it }
                )
            }
        }
        
        if (!isSmallDevice) {
            Surface(
                color = Color(0x22FFFFFF),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    val spec = DroneRegistry.getSpec(currentType)
                    val module = DroneRegistry.getModule(currentType)
                    val localizedName = when(spec.id) {
                        "QUAD_STANDARD" -> stringResource(R.string.model_name_small)
                        "T4_HEAVY_LIFT" -> stringResource(R.string.model_name_heavy)
                        "HELI_900" -> stringResource(R.string.model_name_heli)
                        else -> spec.name
                    }
                    
                    Text(
                        text = stringResource(R.string.drone_selection_detail_title, localizedName),
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = module.getFormattedSpecs(),
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0x1AFFFFFF))
                    Text(
                        text = module.hardwareSpecs.description,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.DroneTypeCard(
    title: String,
    type: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isHoldSupported: Boolean = false,
    isHoldEnabled: Boolean = false,
    isHoldMapped: Boolean = false,
    onToggleHold: (Boolean) -> Unit = {}
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
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick() }
            )
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
                // [v1.5.3] 委派至機型模組繪製專屬圖示，徹底移除 ID 判斷
                val module = DroneRegistry.getModule(type)
                module.RenderIcon(modifier = Modifier.size(40.dp), isSelected = isSelected)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            if (isSelected) {
                Text(stringResource(R.string.drone_selection_current), color = Color.Cyan, fontSize = 11.sp)
                
                if (isHoldSupported && isHoldMapped) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp).clickable { onToggleHold(!isHoldEnabled) }
                    ) {
                        Switch(
                            checked = isHoldEnabled,
                            onCheckedChange = { onToggleHold(it) },
                            modifier = Modifier.scale(0.6f).height(20.dp),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Cyan)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.drone_selection_hold), color = if(isHoldEnabled) Color.Cyan else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 由於 Icon Composable 被多個機型檔案引用，暫時保留在此，未來可移至 ui/common
@Composable
fun HelicopterIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f
        drawRect(Color(0xFF222222), Offset(cx - w*0.3f, cy - h*0.2f), Size(w*0.5f, h*0.4f))
        drawLine(Color(0xFF222222), Offset(cx - w*0.3f, cy), Offset(cx - w*0.45f, cy), strokeWidth = 4.dp.toPx())
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
