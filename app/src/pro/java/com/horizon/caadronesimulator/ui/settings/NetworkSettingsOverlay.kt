package com.horizon.caadronesimulator.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.5.2] 網絡連線設定對話框 - 交互優化版
 * 優化橫向小螢幕空間利用，並實作點擊背景收起輸入法、Enter 即儲存之交互。
 */
@Composable
fun NetworkSettingsOverlay(
    host: String,
    port: Int,
    protocol: String,
    onDismiss: () -> Unit,
    onSave: (String, Int, String) -> Unit
) {
    var tempHost by remember { mutableStateOf(host) }
    var tempPort by remember { mutableStateOf(port.toString()) }
    var tempProtocol by remember { mutableStateOf(protocol) }
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .zIndex(100f)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.width(340.dp).pointerInput(Unit) { detectTapGestures { } }, // 防止點擊對話框內部也收起
            color = Color(0xFF1B2535),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.net_title), color = Color.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { 
                        keyboardController?.hide()
                        onDismiss() 
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 協定選擇 (水平整合)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.net_label_proto), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("UDP", "TCP").forEach { p ->
                            Surface(
                                modifier = Modifier.weight(1f).height(28.dp),
                                color = if (tempProtocol == p) Color.Cyan else Color(0x1AFFFFFF),
                                shape = RoundedCornerShape(6.dp),
                                onClick = { 
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    tempProtocol = p 
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(p, color = if (tempProtocol == p) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // IP 位址 (標籤與輔助鈕並排)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.net_label_ip), color = Color.Gray, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { tempHost = "127.0.0.1" }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(20.dp)) {
                        Text(stringResource(R.string.net_ip_local), fontSize = 10.sp, color = Color.Cyan)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { tempHost = "0.0.0.0" }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(20.dp)) {
                        Text(stringResource(R.string.net_ip_all), fontSize = 10.sp, color = Color.Cyan)
                    }
                }
                
                OutlinedTextField(
                    value = tempHost,
                    onValueChange = { tempHost = it },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.White.copy(0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.Cyan
                    ),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
                )

                Spacer(Modifier.height(8.dp))

                // Port (緊湊排版)
                Text(stringResource(R.string.net_label_port), color = Color.Gray, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = tempPort,
                        onValueChange = { if (it.all { c -> c.isDigit() }) tempPort = it },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Cyan,
                            unfocusedBorderColor = Color.White.copy(0.15f)
                        ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            val portInt = tempPort.toIntOrNull() ?: 14550
                            onSave(tempHost, portInt, tempProtocol)
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("14550", "5760").forEach { p ->
                            AssistChip(
                                onClick = { 
                                    tempPort = p 
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                },
                                label = { Text(p, fontSize = 9.sp) },
                                modifier = Modifier.height(24.dp),
                                colors = AssistChipDefaults.assistChipColors(labelColor = Color.Cyan.copy(alpha = 0.8f))
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 儲存按鈕
                Button(
                    onClick = { 
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        val portInt = tempPort.toIntOrNull() ?: 14550
                        onSave(tempHost, portInt, tempProtocol) 
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.action_save_apply), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
