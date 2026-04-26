package com.horizon.caadronesimulator.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.horizon.caadronesimulator.model.ChannelMapping
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Rect

@Composable
fun JoystickMappingScreen(
    mappingLY: ChannelMapping,
    mappingLX: ChannelMapping,
    mappingRY: ChannelMapping,
    mappingRX: ChannelMapping,
    isAutoBinding: String?,
    useRawMapping: Boolean,
    halfThrottle: Boolean,
    joystickDeadzone: Float,
    activeAxis: String,
    joystickMode: Int,
    stickLX: Float, stickLY: Float,
    stickRX: Float, stickRY: Float,
    isCalibrating: Boolean,
    calibrationStep: Int,
    setupWizardStep: Int,
    wizardCountdown: Int = 0,
    isWizardWaiting: Boolean = false,
    onStartCalibration: () -> Unit,
    onNextCalibrationStep: () -> Unit,
    onFinishCalibration: () -> Unit,
    onStartWizard: () -> Unit,
    onCancelWizard: () -> Unit,
    onToggleRawMapping: (Boolean) -> Unit,
    onToggleHalfThrottle: (Boolean) -> Unit,
    onUpdateDeadzone: (Float) -> Unit,
    onStartBinding: (String) -> Unit,
    onToggleInvert: (String) -> Unit,
    onManualBind: (String, Int) -> Unit,
    onModeChange: (Int) -> Unit,
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    val configuration = LocalConfiguration.current
    val isSmallHeight = configuration.screenHeightDp < 400
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xEE111111)).clickable(enabled = false) {}) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("外接搖桿映射", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onStartWizard, 
                    modifier = Modifier.height(28.dp).onGloballyPositioned { onTargetPositioned("wizard", it.boundsInRoot()) }, 
                    shape = RoundedCornerShape(14.dp), 
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.8f)),
                    contentPadding = PaddingValues(0.dp)
                ) { 
                    Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text("引導設定 (Wizard)", fontSize = 11.sp) 
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onStartCalibration, 
                    modifier = Modifier.height(28.dp).onGloballyPositioned { onTargetPositioned("calib", it.boundsInRoot()) }, 
                    shape = RoundedCornerShape(14.dp), 
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.8f)),
                    contentPadding = PaddingValues(0.dp)
                ) { 
                    Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text("搖桿校準", fontSize = 11.sp) 
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isSmallHeight) {
                // 小裝置一頁式排版: 左右分欄
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 左側: 映射列表
                    Surface(color = Color(0x11FFFFFF), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1.2f)) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val labelLY = when(joystickMode) { 1 -> "俯仰 Pitch"; 2 -> "油門 Throttle"; 3 -> "俯仰 Pitch"; else -> "LY" }
                            val labelLX = when(joystickMode) { 3 -> "橫滾 Roll"; else -> "航向 Yaw" }
                            val labelRY = when(joystickMode) { 1 -> "油門 Throttle"; 2 -> "俯仰 Pitch"; 3 -> "油門 Throttle"; else -> "RY" }
                            val labelRX = when(joystickMode) { 3 -> "航向 Yaw"; else -> "橫滾 Roll" }
                            
                            val suffixLY = if (isSmallHeight) "" else " (左垂直)"
                            val suffixLX = if (isSmallHeight) "" else " (左水平)"
                            val suffixRY = if (isSmallHeight) "" else " (右垂直)"
                            val suffixRX = if (isSmallHeight) "" else " (右水平)"

                            CompactMappingLine("$labelLY$suffixLY", mappingLY, "ly", isAutoBinding, onStartBinding, onToggleInvert, onManualBind, isSmall = true, onTargetPositioned = onTargetPositioned)
                            HorizontalDivider(color = Color(0x0AFFFFFF))
                            CompactMappingLine("$labelLX$suffixLX", mappingLX, "lx", isAutoBinding, onStartBinding, onToggleInvert, onManualBind, isSmall = true)
                            HorizontalDivider(color = Color(0x0AFFFFFF))
                            CompactMappingLine("$labelRY$suffixRY", mappingRY, "ry", isAutoBinding, onStartBinding, onToggleInvert, onManualBind, isSmall = true)
                            HorizontalDivider(color = Color(0x0AFFFFFF))
                            CompactMappingLine("$labelRX$suffixRX", mappingRX, "rx", isAutoBinding, onStartBinding, onToggleInvert, onManualBind, isSmall = true)
                        }
                    }

                    // 右側: 設定與預覽
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 活動軸向提示 (置頂)
                        Surface(color = Color.Black, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Text(text = "活動軸向: $activeAxis", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 模式切換
                        Row(modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("mode", it.boundsInRoot()) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            IconButton(modifier = Modifier.size(24.dp), onClick = { onModeChange(if(joystickMode > 1) joystickMode - 1 else 3) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(18.dp)) }
                            Text(when(joystickMode) { 1 -> "Mode 1"; 2 -> "Mode 2"; 3 -> "Mode 3"; else -> "M$joystickMode" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            IconButton(modifier = Modifier.size(24.dp), onClick = { onModeChange(if(joystickMode < 3) joystickMode + 1 else 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(18.dp)) }
                        }

                        // 死區與 Half Throttle
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = halfThrottle, onCheckedChange = onToggleHalfThrottle, modifier = Modifier.size(24.dp), colors = CheckboxDefaults.colors(checkedColor = Color.Cyan, uncheckedColor = Color.Gray))
                                Text("Half Throttle", color = Color.Gray, fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("死區: ${String.format(java.util.Locale.US, "%.2f", joystickDeadzone)}", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                                Slider(value = joystickDeadzone, onValueChange = onUpdateDeadzone, valueRange = 0f..0.5f, modifier = Modifier.weight(1f).height(20.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan, inactiveTrackColor = Color.DarkGray))
                            }
                        }

                        // 視覺預覽 (縮小)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            MiniStickVisual(joystickMode, true, stickLX, stickLY)
                            Spacer(modifier = Modifier.width(12.dp))
                            MiniStickVisual(joystickMode, false, stickRX, stickRY)
                        }
                    }
                }
            } else {
                // 原有的直向排版
                Surface(color = Color(0x11FFFFFF), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val labelLY = when(joystickMode) { 1 -> "俯仰 Pitch"; 2 -> "油門 Throttle"; 3 -> "俯仰 Pitch"; else -> "LY" }
                        val labelLX = when(joystickMode) { 3 -> "橫滾 Roll"; else -> "航向 Yaw" }
                        val labelRY = when(joystickMode) { 1 -> "油門 Throttle"; 2 -> "俯仰 Pitch"; 3 -> "油門 Throttle"; else -> "RY" }
                        val labelRX = when(joystickMode) { 3 -> "航向 Yaw"; else -> "橫滾 Roll" }

                        CompactMappingLine("$labelLY (左垂直)", mappingLY, "ly", isAutoBinding, onStartBinding, onToggleInvert, onManualBind, onTargetPositioned = onTargetPositioned)
                        HorizontalDivider(color = Color(0x0AFFFFFF))
                        CompactMappingLine("$labelLX (左水平)", mappingLX, "lx", isAutoBinding, onStartBinding, onToggleInvert, onManualBind)
                        HorizontalDivider(color = Color(0x0AFFFFFF))
                        CompactMappingLine("$labelRY (右垂直)", mappingRY, "ry", isAutoBinding, onStartBinding, onToggleInvert, onManualBind)
                        HorizontalDivider(color = Color(0x0AFFFFFF))
                        CompactMappingLine("$labelRX (右水平)", mappingRX, "rx", isAutoBinding, onStartBinding, onToggleInvert, onManualBind)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Settings Row
                Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = halfThrottle, onCheckedChange = onToggleHalfThrottle, modifier = Modifier.size(28.dp), colors = CheckboxDefaults.colors(checkedColor = Color.Cyan, uncheckedColor = Color.Gray))
                    Text("Half Throttle", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("死區: ${String.format(java.util.Locale.US, "%.2f", joystickDeadzone)}", color = Color.Gray, fontSize = 12.sp)
                    Slider(value = joystickDeadzone, onValueChange = onUpdateDeadzone, valueRange = 0f..0.5f, modifier = Modifier.width(120.dp).height(20.dp).padding(horizontal = 8.dp), colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan, inactiveTrackColor = Color.DarkGray))
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Surface(color = Color.Black, shape = RoundedCornerShape(4.dp)) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                            Text(text = "活動軸向: $activeAxis", color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode & Sticks Visual
                Row(modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("mode", it.boundsInRoot()) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(modifier = Modifier.size(28.dp), onClick = { onModeChange(if(joystickMode > 1) joystickMode - 1 else 3) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(20.dp)) }
                    
                    val modeLabel = when(joystickMode) {
                        1 -> "Mode 1 (日本手)"
                        2 -> "Mode 2 (美國手)"
                        3 -> "Mode 3 (中國手)"
                        else -> "Mode $joystickMode"
                    }
                    Text(modeLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    
                    IconButton(modifier = Modifier.size(28.dp), onClick = { onModeChange(if(joystickMode < 3) joystickMode + 1 else 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(20.dp)) }
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    MiniStickVisual(joystickMode, true, stickLX, stickLY)
                    Spacer(modifier = Modifier.width(12.dp))
                    MiniStickVisual(joystickMode, false, stickRX, stickRY)
                }
            }
        }

        // Popups
        if (isAutoBinding != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Surface(color = Color(0xFF222222), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.Cyan)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.Cyan, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        val bindingLabel = when(isAutoBinding) { "ly" -> "左垂直 (LY)"; "lx" -> "左水平 (LX)"; "ry" -> "右垂直 (RY)"; "rx" -> "右水平 (RX)"; else -> isAutoBinding }
                        Text("正在偵測: $bindingLabel", color = Color.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("請撥動遙控器上的對應搖桿軸...", color = Color.White, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { onStartBinding("") }, modifier = Modifier.height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("取消偵測", fontSize = 13.sp) }
                    }
                }
            }
        }

        if (isCalibrating) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Surface(color = Color(0xFF222222), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF4CAF50))) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("搖桿校準 - Step $calibrationStep", color = Color(0xFF4CAF50), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(if(calibrationStep == 1) "步驟 1：請放開所有搖桿使其回正\n(系統將鎖定物理中位點)" else "步驟 2：請將左右搖桿向所有角落繞圈\n(系統將記錄最大行程)", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 新增：校準過程中的即時桿位圖像
                        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MiniStickVisual(joystickMode, true, stickLX, stickLY)
                                Text("左搖桿", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MiniStickVisual(joystickMode, false, stickRX, stickRY)
                                Text("右搖桿", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { if(calibrationStep == 1) onNextCalibrationStep() else onFinishCalibration() }, modifier = Modifier.fillMaxWidth().height(40.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text(if(calibrationStep == 1) "確認中位，下一步" else "完成校準並儲存", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        if (setupWizardStep > 0) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Surface(color = Color(0xFF1B2535), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF2196F3))) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("引導設定 Wizard", color = Color(0xFF2196F3), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val instr = if (setupWizardStep > 0) {
                            if (isWizardWaiting) {
                                "偵測成功！請將搖桿回中 (${wizardCountdown}s)..."
                            } else {
                                when(setupWizardStep) { 
                                    1 -> "請將「左搖桿」向上推到底"; 
                                    2 -> "請將「左搖桿」向右撥到底"; 
                                    3 -> "請將「右搖桿」向上推到底"; 
                                    4 -> "請將「右搖桿」向右撥到底"; 
                                    else -> "" 
                                }
                            }
                        } else ""
                        
                        Text(instr, color = if(isWizardWaiting) Color.Cyan else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 動畫引導
                        val infiniteTransition = rememberInfiniteTransition(label = "wizard")
                        val animValue by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = -1f,
                            animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                            label = "stickAnim"
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                            WizardStickVisual(isActive = setupWizardStep <= 2 && !isWizardWaiting, animValue = animValue, isVertical = setupWizardStep == 1)
                            WizardStickVisual(isActive = setupWizardStep >= 3 && !isWizardWaiting, animValue = animValue, isVertical = setupWizardStep == 3)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onCancelWizard, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.height(36.dp)) { Text("取消設定", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun WizardStickVisual(isActive: Boolean, animValue: Float, isVertical: Boolean) {
    Box(modifier = Modifier.size(70.dp).background(Color(0x33000000), CircleShape).border(1.dp, if(isActive) Color.Cyan else Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        val offsetX = if(isActive && !isVertical) (-animValue * 20).dp else 0.dp
        val offsetY = if(isActive && isVertical) (animValue * 20).dp else 0.dp
        Box(modifier = Modifier.size(30.dp).offset(x = offsetX, y = offsetY).background(brush = androidx.compose.ui.graphics.Brush.radialGradient(colors = listOf(Color.White, Color.LightGray)), shape = CircleShape).border(2.dp, if(isActive) Color.Cyan else Color.Transparent, CircleShape))
    }
}

@Composable
fun CompactMappingLine(
    label: String, 
    mapping: ChannelMapping, 
    key: String, 
    isBinding: String?, 
    onStartBinding: (String) -> Unit, 
    onToggleInvert: (String) -> Unit, 
    onManualBind: (String, Int) -> Unit,
    isSmall: Boolean = false,
    onTargetPositioned: (String, Rect) -> Unit = { _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }
    val labelWidth = if (isSmall) 80.dp else 165.dp
    val fontSize = if (isSmall) 10.5.sp else 13.sp
    
    Row(modifier = Modifier.fillMaxWidth().height(if (isSmall) 26.dp else 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label, 
            color = Color.White, 
            modifier = Modifier.width(labelWidth), 
            fontSize = fontSize, 
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Button(
            onClick = { onStartBinding(key) }, 
            colors = ButtonDefaults.buttonColors(containerColor = if(isBinding == key) Color.Cyan else Color(0x22FFFFFF)), 
            shape = RoundedCornerShape(5.dp), 
            contentPadding = PaddingValues(0.dp), 
            modifier = Modifier.height(if (isSmall) 22.dp else 26.dp).weight(1f).onGloballyPositioned { 
                if (key == "ly") {
                    onTargetPositioned("auto_bind", it.boundsInRoot()) 
                }
            }
        ) { 
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if(isBinding == key) (if(isSmall) "偵測" else "偵測中...") else (if(isSmall) "Auto" else "Auto Bind"), fontSize = 10.sp, color = if(isBinding == key) Color.Black else Color.White) 
            }
        }
        
        Spacer(modifier = Modifier.width(if (isSmall) 6.dp else 10.dp))
        
        Box(modifier = Modifier.width(if (isSmall) 65.dp else 90.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(if (isSmall) 22.dp else 26.dp).background(Color(0x33FFFFFF), RoundedCornerShape(5.dp)).clickable { expanded = true }, 
                contentAlignment = Alignment.Center
            ) { 
                Text(text = if(mapping.axis == -1) (if(isSmall) "未配對" else "未設定") else mapping.label, color = if(mapping.axis == -1) Color.Gray else Color.Cyan, fontSize = 10.sp)
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray, modifier = Modifier.size(14.dp).align(Alignment.CenterEnd).padding(end = 1.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(110.dp).heightIn(max = 200.dp), properties = PopupProperties(focusable = false)) {
                (0..28).forEach { axisId -> 
                    DropdownMenuItem(text = { Text("Axis $axisId", fontSize = 13.sp) }, onClick = { onManualBind(key, axisId); expanded = false }) 
                }
            }
        }
        
        Spacer(modifier = Modifier.width(if (isSmall) 6.dp else 10.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isSmall) Text("反向", color = Color.Gray, fontSize = 11.sp)
            Switch(checked = mapping.inverted, onCheckedChange = { onToggleInvert(key) }, modifier = Modifier.scale(if (isSmall) 0.5f else 0.6f).onGloballyPositioned { 
                if (key == "ly") onTargetPositioned("invert", it.boundsInRoot()) 
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Red, uncheckedTrackColor = Color.DarkGray))
        }
    }
}

@Composable
fun MiniStickVisual(mode: Int, isLeft: Boolean, x: Float, y: Float) {
    Box(modifier = Modifier.size(36.dp).background(Color(0x44000000), RoundedCornerShape(4.dp)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))) {
        Box(modifier = Modifier.size(8.dp).offset(x = (x * 12).dp, y = -(y * 12).dp).align(Alignment.Center).background(Color.Cyan, CircleShape))
        val label = if (isLeft) { 
            when(mode) { 1 -> "Y/P"; 3 -> "R/P"; else -> "Y/T" } 
        } else { 
            when(mode) { 1 -> "R/T"; 3 -> "Y/T"; else -> "R/P" } 
        }
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
    }
}
