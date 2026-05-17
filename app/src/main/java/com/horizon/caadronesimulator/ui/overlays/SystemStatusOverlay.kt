package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import kotlinx.coroutines.delay

/**
 * [v1.2.81 階段三修正] 絕對頂層系統警告通道 (Critical System HUD)
 */
@Composable
fun SystemStatusOverlay(state: DroneState, stickState: StickInputState) {
    val displayMessage = state.systemMessage ?: ""
    
    // 是否正在進行背景持續性任務
    val isTaskRunning = ((state.isHandshaking && stickState.packetsPerSecond == 0) || !state.isSettingsLoaded) && !state.showSettings

    // [v1.2.81 精確放行] 判定此訊息是否具備「穿透權限」(關鍵引導或致命警告)
    val isPriorityMessage = displayMessage.contains("📋") || 
                           displayMessage.contains("⏳") || 
                           displayMessage.contains("💥") || 
                           displayMessage.contains("✅") || 
                           displayMessage.contains("⚠️") ||
                           state.isCollision

    LaunchedEffect(displayMessage, isTaskRunning) {
        if (displayMessage.isNotEmpty() && !isTaskRunning) {
            // [v1.5.3] 顯示時間縮短為 1.8 秒，提升交互明快感
            delay(1800)
            if (state.systemMessage == displayMessage) {
                state.systemMessage = null
            }
        }
    }

    AnimatedVisibility(
        visible = displayMessage.isNotEmpty() && (!state.showSettings || isTaskRunning || isPriorityMessage),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = Modifier.zIndex(99f)
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .background(Color(0xCC330000), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.Red.copy(0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(displayMessage, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        
                        if (state.inputMode == 1 && state.isHandshaking && stickState.packetsPerSecond == 0) {
                            Spacer(modifier = Modifier.width(12.dp))
                            androidx.compose.material3.TextButton(
                                onClick = { 
                                    state.isHandshaking = false
                                    state.systemMessage = "連線已取消"
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("取消", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    if (((state.isHandshaking && stickState.packetsPerSecond == 0) || !state.isSettingsLoaded) && !state.showSettings) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.width(150.dp).height(4.dp),
                            color = Color.Red,
                            trackColor = Color.White.copy(0.2f)
                        )
                        Text(
                            if (!state.isSettingsLoaded) "載入設定中..." else "正在嘗試連線至內置系統...",
                            color = Color.Red.copy(0.8f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
