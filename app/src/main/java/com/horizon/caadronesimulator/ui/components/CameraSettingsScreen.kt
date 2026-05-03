package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 獨立的相機與視覺設定頁面
 */
@Composable
fun CameraSettingsScreen(
    cameraMode: String,
    zoomFactor: Float,
    cameraTilt: Float,
    onUpdateCameraMode: (String) -> Unit,
    onUpdateZoom: (Float) -> Unit,
    onUpdateTilt: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE111111))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("相機與視覺設定", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 左側：視角模式
                Surface(
                    modifier = Modifier.weight(1.2f),
                    color = Color(0x1AFFFFFF),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("視角切換", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val modes = listOf("站位視角 (追蹤)", "站位視角 (固定)", "跟隨視角", "FPV 視角")
                        modes.forEach { mode ->
                            CompactChip(
                                text = mode,
                                selected = cameraMode == mode,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).height(32.dp),
                                onClick = { onUpdateCameraMode(mode) }
                            )
                        }
                    }
                }

                // 右側：倍率與仰角
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("視野倍率 (Zoom)", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format(Locale.US, "%.1f", zoomFactor)}x", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = zoomFactor,
                                onValueChange = onUpdateZoom,
                                valueRange = 1.0f..3.0f,
                                modifier = Modifier.height(30.dp),
                                colors = SliderDefaults.colors(thumbColor = Color.Cyan)
                            )
                        }
                    }

                    Surface(
                        color = Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("FPV 鏡頭仰角", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${cameraTilt.toInt()}°", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = cameraTilt,
                                onValueChange = onUpdateTilt,
                                valueRange = -30f..45f,
                                modifier = Modifier.height(30.dp),
                                colors = SliderDefaults.colors(thumbColor = Color.Cyan)
                            )
                        }
                    }
                }
            }
        }
    }
}
