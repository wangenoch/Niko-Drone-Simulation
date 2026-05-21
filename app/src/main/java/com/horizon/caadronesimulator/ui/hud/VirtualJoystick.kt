package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.horizon.caadronesimulator.ui.theme.NikoTheme
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun VirtualJoystick(
    stickX: Float,
    stickY: Float,
    onDragStateChange: (Boolean) -> Unit = {},
    onValueChange: (Float, Float) -> Unit
) {
    val themeColors = NikoTheme.colors
    val maxRadius = 70.dp
    val density = LocalDensity.current
    val maxPx = with(density) { maxRadius.toPx() }
    
    var internalOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(stickX, stickY) {
        if (!isDragging) {
            internalOffset = Offset(stickX * maxPx, -stickY * maxPx)
        }
    }

    Box(
        modifier = Modifier
            .size(150.dp)
            .background(themeColors.textPrimary.copy(alpha = 0.1f), CircleShape)
            .border(2.dp, themeColors.textPrimary.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(internalOffset.x.roundToInt(), internalOffset.y.roundToInt()) }
                .size(60.dp)
                .background(themeColors.textPrimary.copy(alpha = 0.4f), CircleShape)
                .shadow(4.dp, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true; onDragStateChange(true) },
                        onDragEnd = { isDragging = false; onDragStateChange(false); internalOffset = Offset.Zero; onValueChange(0f, 0f) },
                        onDragCancel = { isDragging = false; onDragStateChange(false); internalOffset = Offset.Zero; onValueChange(0f, 0f) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = internalOffset.x + dragAmount.x
                            val newY = internalOffset.y + dragAmount.y
                            val dist = sqrt(newX * newX + newY * newY)
                            val finalOffset = if (dist > maxPx) Offset(newX * maxPx / dist, newY * maxPx / dist) else Offset(newX, newY)
                            internalOffset = finalOffset
                            val d = sqrt(finalOffset.x * finalOffset.x + finalOffset.y * finalOffset.y)
                            val normalizedD = d / maxPx
                            val deadzone = 0.05f
                            val (outX, outY) = if (normalizedD < deadzone) 0f to 0f else {
                                val factor = (normalizedD - deadzone) / (1f - deadzone) / normalizedD
                                (finalOffset.x / maxPx * factor) to (-finalOffset.y / maxPx * factor)
                            }
                            onValueChange(outX, outY)
                        }
                    )
                }
        )
    }
}

@Composable
fun MiniStickVisual(joystickMode: Int, isLeft: Boolean, stickX: Float, stickY: Float) {
    val themeColors = NikoTheme.colors
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(themeColors.textPrimary.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, themeColors.divider, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(x = (stickX * 20).dp, y = -(stickY * 20).dp)
                .size(14.dp)
                .background(themeColors.primary, CircleShape)
        )
    }
}
