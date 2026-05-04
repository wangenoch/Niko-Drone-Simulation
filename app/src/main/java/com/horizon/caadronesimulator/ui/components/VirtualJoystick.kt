package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun VirtualJoystick(
    stickX: Float,
    stickY: Float,
    onDragStateChange: (Boolean) -> Unit = {},
    onValueChange: (Float, Float) -> Unit
) {
    val maxRadius = 70.dp
    val density = LocalDensity.current
    val maxPx = with(density) { maxRadius.toPx() }
    
    // 內部狀態：負責 60FPS 的即時渲染
    var internalOffset by remember { mutableStateOf(Offset.Zero) }
    // 標記位：防止拖曳時被外部 State 回傳值干擾導致抖動
    var isDragging by remember { mutableStateOf(false) }

    // 只有在非拖曳狀態下 (例如實體遙控器在動)，才接受外部位置同步
    LaunchedEffect(stickX, stickY) {
        if (!isDragging) {
            internalOffset = Offset(stickX * maxPx, -stickY * maxPx)
        }
    }

    Box(
        modifier = Modifier
            .size(150.dp)
            .background(Color(0x44FFFFFF), CircleShape)
            .border(2.dp, Color(0x66FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                // 關鍵優化：使用 lambda block 形式的 offset 避免觸發 Recomposition
                .offset { IntOffset(internalOffset.x.roundToInt(), internalOffset.y.roundToInt()) }
                .size(60.dp)
                .background(Color(0xAAFFFFFF), CircleShape)
                .shadow(4.dp, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            isDragging = true
                            onDragStateChange(true)
                        },
                        onDragEnd = { 
                            isDragging = false
                            onDragStateChange(false)
                            internalOffset = Offset.Zero
                            onValueChange(0f, 0f) 
                        },
                        onDragCancel = { 
                            isDragging = false
                            onDragStateChange(false)
                            internalOffset = Offset.Zero
                            onValueChange(0f, 0f) 
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            
                            val newX = internalOffset.x + dragAmount.x
                            val newY = internalOffset.y + dragAmount.y
                            
                            val dist = sqrt(newX * newX + newY * newY)
                            val finalOffset = if (dist > maxPx) {
                                Offset(newX * maxPx / dist, newY * maxPx / dist)
                            } else {
                                Offset(newX, newY)
                            }
                            
                            internalOffset = finalOffset
                            
                            // [v1.2.95] 向量死區處理與平滑過渡
                            val d = sqrt(finalOffset.x * finalOffset.x + finalOffset.y * finalOffset.y)
                            val normalizedD = d / maxPx
                            val deadzone = 0.05f
                            
                            val (outX, outY) = if (normalizedD < deadzone) {
                                0f to 0f
                            } else {
                                // 模長平滑過渡公式：(normalizedD - deadzone) / (1 - deadzone)
                                val factor = (normalizedD - deadzone) / (1f - deadzone) / normalizedD
                                (finalOffset.x / maxPx * factor) to (-finalOffset.y / maxPx * factor)
                            }
                            
                            // 即時回傳數值給飛行引擎
                            onValueChange(outX, outY)
                        }
                    )
                }
        )
    }
}
