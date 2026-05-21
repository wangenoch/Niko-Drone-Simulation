package com.horizon.caadronesimulator.ui.overlays

// Android 系統與 Activity 支援
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.ui.theme.NikoTheme
import kotlinx.coroutines.delay

/**
 * 模擬器啟動畫面組件
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier, onTimeout: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val themeColors = NikoTheme.colors
    
    LaunchedEffect(Unit) {
        visible = true
        delay(2000)
        visible = false
        delay(600)
        onTimeout()
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800),
        label = "splash_alpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Niko Drone Simulator",
            color = themeColors.primary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(alpha)
        )
    }
}
