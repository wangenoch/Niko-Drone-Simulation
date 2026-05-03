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
        TutorialStep("歡迎使用 Niko Drone Simulator", "這是一個專業的飛行訓練系統，旨在幫助您熟悉無人機考照流程與物理特性。", Alignment.Center),
        TutorialStep("功能工具列面板", "點擊右上角按鈕開啟工具列。包含：\n• 數據開關：顯示/隱藏底部資訊\n• 音效切換：開啟/靜音馬達與環境音\n• 虛擬搖桿：切換螢幕觸控按鍵\n• 視角模式：切換追蹤/跟隨/FPV 視角\n• 整合設定：進行搖桿映射與機型選擇\n• 重置飛行：立即回到起飛位置", Alignment.Center, Modifier.padding(top = 100.dp)),
        TutorialStep("視覺化雷達 HUD", "左下角顯示無人機在場地中的相對位置與航向。紅色區域為出界範圍，請保持在限制內飛行。", Alignment.Center, Modifier.padding(bottom = 120.dp)),
        TutorialStep("飛行狀態監控", "底部欄位提供高度、速度與距離資訊。注意高度限值為 30m，超過將會收到警示訊息。", Alignment.Center, Modifier.padding(bottom = 100.dp)),
        TutorialStep("馬達解鎖與啟動", "著地時點擊置頂的「起槳」按鈕或使用實體搖桿「內八 CSC」指令來解鎖馬達開始飛行。", Alignment.Center, Modifier.padding(top = 130.dp)),
        TutorialStep("準備就緒！", "現在請嘗試解鎖馬達並進行第一次起飛練習。祝您練習順利！", Alignment.Center)
    )
    val pulseAlpha by rememberInfiniteTransition(label = "").animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "")
    
    Box(modifier = modifier.fillMaxSize().background(Color(0x99000000)).clickable { 
        if (step < tutorialSteps.size - 1) viewModel.welcomeStep++ else {
            viewModel.welcomeStep = 0
            onDismiss()
        }
    }) {
        when(step) {
            1 -> TutorialHighlight(Alignment.TopEnd, Modifier.statusBarsPadding().displayCutoutPadding().padding(top = 16.dp, end = 16.dp), "功能選單", pulseAlpha)
            2 -> TutorialHighlight(Alignment.BottomStart, Modifier.navigationBarsPadding().padding(bottom = 16.dp, start = 16.dp), "視覺雷達", pulseAlpha)
            3 -> TutorialHighlight(Alignment.BottomCenter, Modifier.navigationBarsPadding(), "飛行數據", pulseAlpha)
            4 -> TutorialHighlight(Alignment.TopCenter, Modifier.padding(top = 70.dp), "解鎖馬達", pulseAlpha)
        }
        val current = tutorialSteps[step]
        Surface(modifier = Modifier.align(current.alignment).padding(32.dp).then(current.modifier).widthIn(max = 350.dp), color = Color(0xFF1B2535), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, Color.Cyan)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(current.title, color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp)); Text(current.description, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("點擊畫面繼續 (${step + 1}/${tutorialSteps.size})", color = Color.Gray, fontSize = 11.sp)
                    TextButton(onClick = onDismiss) { Text("跳過導覽", color = Color.White.copy(0.5f), fontSize = 12.sp) }
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
        TutorialStep("搖桿設定導覽", "在此頁面您可以完整設定外接遙控器，確保飛行操控精確無誤。", Alignment.Center),
        TutorialStep("輸入模式選擇", "您可以切換「外接手把」或「內置系統」。專業一體化遙控器請選擇內置模式。", Alignment.Center),
        TutorialStep("硬體掃描與連線", "點擊此按鈕啟動硬體偵測。連線成功後，RX 數值會開始跳動。", Alignment.Center),
        TutorialStep("一鍵引導設定", "最推薦新手的設定方式！點擊此處由系統引導您依序撥動搖桿完成對應。", Alignment.Center),
        TutorialStep("搖桿校準工具", "若搖桿無法回正或行程不足，請使用校準工具定義物理中位點與邊界。", Alignment.Center),
        TutorialStep("單軸自動綁定", "點擊功能旁的「Auto」並撥動搖桿，可快速手動對應指定通道。", Alignment.Center),
        TutorialStep("反向開關", "若發現動作方向相反（如推桿變下降），切換此開關即可修正。", Alignment.Center),
        TutorialStep("操控模式切換", "點擊箭頭可切換 Mode 1 (日本手) 或 Mode 2 (美國手) 等操作模式。", Alignment.Center),
        TutorialStep("靈敏度與曲線", "調整 Rate (靈敏度) 與 Expo (曲線) 來優化操控手感，亦可進入進階設定微調單軸。", Alignment.Center)
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
                    TextButton(onClick = onDismiss, modifier = Modifier.height(32.dp)) { Text("跳過", color = Color.White.copy(0.4f), fontSize = 12.sp) }
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
        TutorialStep("環境與氣候導覽", "在此頁面您可以設定模擬環境的各項參數，挑戰不同難度的飛行任務。", Alignment.Center),
        TutorialStep("風力等級選擇", "設定當前的平均風速。等級越高無人機偏移量越大，需更精確地修正。", Alignment.Center),
        TutorialStep("風向控制系統", "選擇風的來源方向。選擇「隨機」將挑戰無規律的變向強風。", Alignment.Center),
        TutorialStep("激烈度與亂流", "調整風速的起伏程度。高激烈度會產生瞬間強陣風模擬惡劣氣候環境。", Alignment.Center),
        TutorialStep("時間與光影", "切換不同時段的環境光。注意早晨與下午的陰影拉長方向會有所不同。", Alignment.Center),
        TutorialStep("陰影深淺調節", "調整無人機在地面上的投影濃度，幫助您在不同地形中更好地判斷高度與位置。", Alignment.Center)
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
                    TextButton(onClick = onDismiss, modifier = Modifier.height(32.dp)) { Text("跳過", color = Color.White.copy(0.4f), fontSize = 12.sp) }
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
