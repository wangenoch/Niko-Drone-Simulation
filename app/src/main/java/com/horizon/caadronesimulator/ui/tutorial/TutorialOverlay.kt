package com.horizon.caadronesimulator.ui.tutorial

import androidx.compose.animation.core.* // 動畫插值與循環工具 (tween, infiniteRepeatable)
import androidx.compose.foundation.* // 基礎 UI 元件 (Box, Background, Clickable)
import androidx.compose.foundation.layout.* // 佈局定位工具 (Box, Row, Column, Padding)
import androidx.compose.foundation.shape.RoundedCornerShape // 圓角矩形輪廓
import androidx.compose.material.icons.Icons // Material 系統圖示庫
import androidx.compose.material.icons.filled.ArrowDownward // 向下指示箭頭
import androidx.compose.material.icons.filled.ArrowUpward // 向上指示箭頭
import androidx.compose.material3.* // Material 3 UI 組件 (Surface, Text, Button)
import androidx.compose.runtime.* // 狀態管理與副作用處理 (remember, mutableIntStateOf)
import androidx.compose.ui.Alignment // 元件對齊規則
import androidx.compose.ui.Modifier // 組件修飾符
import androidx.compose.ui.geometry.Rect // 幾何矩形座標數據
import androidx.compose.ui.graphics.Color // 顏色類型與定義
import androidx.compose.ui.platform.LocalContext // 取得當前 Android Context
import androidx.compose.ui.platform.LocalDensity // 取得當前螢幕像素密度 (px to dp)
import androidx.compose.ui.text.font.FontWeight // 字體粗細定義
import androidx.compose.ui.unit.dp // 密度無關像素單位
import androidx.compose.ui.unit.sp // 文字縮放比例單位

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

data class TutorialStep(
    val title: String,
    val description: String,
    val alignment: Alignment,
    val modifier: Modifier = Modifier
)

/**
 * [v1.3.4] 歡迎導覽組件
 */
@Composable
fun WelcomeTutorial(
    viewModel: com.horizon.caadronesimulator.logic.DroneViewModel, 
    modifier: Modifier = Modifier, 
    onDismiss: () -> Unit
) {
    val step = viewModel.welcomeStep
    val tutorialSteps = listOf(
        TutorialStep(stringResource(R.string.tut_welcome_t1), stringResource(R.string.tut_welcome_d1), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_welcome_t2), stringResource(R.string.tut_welcome_d2), Alignment.Center, Modifier.padding(top = 100.dp)),
        TutorialStep(stringResource(R.string.tut_welcome_t3), stringResource(R.string.tut_welcome_d3), Alignment.Center, Modifier.padding(bottom = 120.dp)),
        TutorialStep(stringResource(R.string.tut_welcome_t4), stringResource(R.string.tut_welcome_d4), Alignment.Center, Modifier.padding(bottom = 100.dp)),
        TutorialStep(stringResource(R.string.tut_welcome_t5), stringResource(R.string.tut_welcome_d5), Alignment.Center, Modifier.padding(top = 130.dp)),
        TutorialStep(stringResource(R.string.tut_welcome_t6), stringResource(R.string.tut_welcome_d6), Alignment.Center)
    )
    val pulseAlpha by rememberInfiniteTransition(label = "").animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "")
    
    Box(modifier = modifier.fillMaxSize().background(Color(0x99000000)).clickable { 
        if (step < tutorialSteps.size - 1) viewModel.welcomeStep++ else {
            viewModel.welcomeStep = 0
            onDismiss()
        }
    }) {
        when(step) {
            1 -> TutorialHighlight(Alignment.TopEnd, Modifier.statusBarsPadding().displayCutoutPadding().padding(top = 16.dp, end = 16.dp), stringResource(R.string.tutorial_label_menu), pulseAlpha)
            2 -> TutorialHighlight(Alignment.BottomStart, Modifier.navigationBarsPadding().padding(bottom = 16.dp, start = 16.dp), stringResource(R.string.tutorial_label_radar), pulseAlpha)
            3 -> TutorialHighlight(Alignment.BottomCenter, Modifier.navigationBarsPadding(), stringResource(R.string.tutorial_label_data), pulseAlpha)
            4 -> TutorialHighlight(Alignment.TopCenter, Modifier.padding(top = 70.dp), stringResource(R.string.tutorial_label_arm), pulseAlpha)
        }
        val current = tutorialSteps[step]
        Surface(modifier = Modifier.align(current.alignment).padding(32.dp).then(current.modifier).widthIn(max = 350.dp), color = Color(0xFF1B2535), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, Color.Cyan)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(current.title, color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp)); Text(current.description, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tutorial_click_continue, step + 1, tutorialSteps.size), color = Color.Gray, fontSize = 11.sp)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.tutorial_skip_nav), color = Color.White.copy(0.5f), fontSize = 12.sp) }
                }
            }
        }
    }
}

/**
 * 搖桿設定頁面導覽 - 支援傳入目標按鈕座標
 */
@Composable
fun JoystickSettingsTutorial(
    onDismiss: () -> Unit, 
    targets: Map<String, Rect>, 
    viewModel: com.horizon.caadronesimulator.logic.DroneViewModel,
    modifier: Modifier = Modifier
) {
    val step = viewModel.joystickTutorialStep
    val tutorialSteps = listOf(
        TutorialStep(stringResource(R.string.tut_joy_t1), stringResource(R.string.tut_joy_d1), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_joy_t2), stringResource(R.string.tut_joy_d2), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_joy_t3), stringResource(R.string.tut_joy_d3), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_joy_t4), stringResource(R.string.tut_joy_d4), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_joy_t5), stringResource(R.string.tut_joy_d5), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_joy_t6), stringResource(R.string.tut_joy_d6), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_joy_t7), stringResource(R.string.tut_joy_d7), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_joy_t8), stringResource(R.string.tut_joy_d8), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_joy_t9), stringResource(R.string.tut_joy_d9), Alignment.Center)
    )

    val current = tutorialSteps[step]
    val pulseAlpha by rememberInfiniteTransition(label = "").animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "")
    val targetRect = targets[when(step) { 
        1 -> "input_mode"
        2 -> "scan"
        3 -> "wizard"
        4 -> "calib"
        5 -> "auto_bind"
        6 -> "invert"
        7 -> "mode"
        8 -> "rates"
        else -> null 
    }]
    
    val density = LocalDensity.current
    val screenH = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toDp() }
    val (align, pad) = if (targetRect != null) { 
        if (with(density) { targetRect.center.y.toDp() } > screenH * 0.45f) Alignment.TopCenter to Modifier.padding(top = 10.dp) 
        else Alignment.BottomCenter to Modifier.padding(bottom = 10.dp) 
    } else Alignment.Center to Modifier.padding(32.dp)

    Box(modifier = modifier.fillMaxSize().background(Color(0xBB000000)).clickable { 
        if (step < tutorialSteps.size - 1) viewModel.joystickTutorialStep++ else {
            viewModel.joystickTutorialStep = 0
            onDismiss()
        }
    }) {
        if (targetRect != null) DynamicTutorialHighlight(targetRect, current.title, pulseAlpha)
        Surface(modifier = Modifier.align(align).then(pad).widthIn(max = 400.dp), color = Color(0xFF1B2535), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, Color.Cyan.copy(0.8f))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(current.title, color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp)); Text(current.description, color = Color.White, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${step + 1}/${tutorialSteps.size}", color = Color.Gray, fontSize = 11.sp)
                    TextButton(onClick = onDismiss, modifier = Modifier.height(32.dp)) { Text(stringResource(R.string.action_skip), color = Color.White.copy(0.4f), fontSize = 12.sp) }
                }
            }
        }
    }
}

/**
 * 環境設定頁面導覽
 */
@Composable
fun ClimateSettingsTutorial(
    onDismiss: () -> Unit, 
    targets: Map<String, Rect>, 
    viewModel: com.horizon.caadronesimulator.logic.DroneViewModel,
    modifier: Modifier = Modifier
) {
    val step = viewModel.climateTutorialStep
    val tutorialSteps = listOf(
        TutorialStep(stringResource(R.string.tut_clim_t1), stringResource(R.string.tut_clim_d1), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_clim_t2), stringResource(R.string.tut_clim_d2), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_clim_t3), stringResource(R.string.tut_clim_d3), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_clim_t4), stringResource(R.string.tut_clim_d4), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_clim_t5), stringResource(R.string.tut_clim_d5), Alignment.Center),
        TutorialStep(stringResource(R.string.tut_clim_t6), stringResource(R.string.tut_clim_d6), Alignment.Center)
    )

    val current = tutorialSteps[step]
    val pulseAlpha by rememberInfiniteTransition(label = "").animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "")
    val targetRect = targets[when(step) { 1 -> "wind_level"; 2 -> "wind_dir"; 3 -> "wind_var"; 4 -> "time"; 5 -> "shadow"; else -> null }]
    
    val density = LocalDensity.current
    val screenH = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toDp() }
    val (align, pad) = if (targetRect != null) { 
        if (with(density) { targetRect.center.y.toDp() } > screenH * 0.45f) Alignment.TopCenter to Modifier.padding(top = 10.dp) 
        else Alignment.BottomCenter to Modifier.padding(bottom = 10.dp)
    } else Alignment.Center to Modifier.padding(32.dp)

    Box(modifier = modifier.fillMaxSize().background(Color(0xBB000000)).clickable { 
        if (step < tutorialSteps.size - 1) viewModel.climateTutorialStep++ else {
            viewModel.climateTutorialStep = 0
            onDismiss()
        }
    }) {
        if (targetRect != null) DynamicTutorialHighlight(targetRect, current.title, pulseAlpha)
        Surface(modifier = Modifier.align(align).then(pad).widthIn(max = 400.dp), color = Color(0xFF1B2535), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, Color.Cyan.copy(0.8f))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(current.title, color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp)); Text(current.description, color = Color.White, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${step + 1}/${tutorialSteps.size}", color = Color.Gray, fontSize = 11.sp)
                    TextButton(onClick = onDismiss, modifier = Modifier.height(32.dp)) { Text(stringResource(R.string.action_skip), color = Color.White.copy(0.4f), fontSize = 12.sp) }
                }
            }
        }
    }
}


@Composable
fun TutorialHighlight(alignment: Alignment, modifier: Modifier, label: String, pulseAlpha: Float) {
    val isBottom = alignment == Alignment.BottomStart || alignment == Alignment.BottomCenter || alignment == Alignment.BottomEnd
    val (fW, fH) = when(label) { "視覺雷達" -> 150.dp to 100.dp; "解鎖馬達" -> 105.dp to 50.dp; "飛行數據", "功能選單" -> 380.dp to 50.dp; else -> 160.dp to 60.dp }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(alignment).then(modifier).size(fW, fH).border(3.dp, Color.Cyan.copy(pulseAlpha), RoundedCornerShape(12.dp)).background(Color.Cyan.copy(0.1f)))
        Column(modifier = Modifier.align(alignment).then(modifier).offset(y = if (isBottom) -(65.dp) else (fH + 5.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isBottom) {
                Text(label, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                Icon(Icons.Default.ArrowDownward, null, tint = Color.Cyan, modifier = Modifier.size(30.dp))
            } else {
                Icon(Icons.Default.ArrowUpward, null, tint = Color.Cyan, modifier = Modifier.size(30.dp).offset(y = (-5).dp))
                Text(label, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
fun DynamicTutorialHighlight(rect: Rect?, label: String, pulseAlpha: Float) {
    if (rect == null) return
    val density = LocalDensity.current
    val screenH = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toDp() }
    with(density) {
        val l = rect.left.toDp(); val t = rect.top.toDp(); val w = rect.width.toDp(); val h = rect.height.toDp()
        val isBottom = t > (screenH * 0.5f)
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.offset(x = l, y = t).size(w, h).border(3.dp, Color.Cyan.copy(pulseAlpha), RoundedCornerShape(12.dp)).background(Color.Cyan.copy(0.1f)))
            Column(modifier = Modifier.offset(x = l + (w / 2) - 80.dp, y = if (isBottom) (t - 65.dp) else (t + h + 5.dp)).width(160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isBottom) {
                    Text(label, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp) )
                    Icon(Icons.Default.ArrowDownward, null, tint = Color.Cyan, modifier = Modifier.size(30.dp))
                } else {
                    Icon(Icons.Default.ArrowUpward, null, tint = Color.Cyan, modifier = Modifier.size(30.dp).offset(y = (-5).dp))
                    Text(label, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                }
            }
        }
    }
}
