package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties

/**
 * [v1.3.5] 全通訊協議管理中樞
 * 實作通用協議鎖定、自動偵測與專屬硬體標籤顯示。
 */
@Composable
fun ProtocolManagementWidget(
    detectedProtocol: String,
    lockedProtocol: String,
    onUpdateLockedProtocol: (String) -> Unit
) {
    val isAX12Active = detectedProtocol == "AX12(UMBUS)" || lockedProtocol == "AX12(UMBUS)"
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "協議管理: ",
                color = Color.Gray,
                fontSize = 9.sp,
                modifier = Modifier.width(60.dp)
            )
            
            var protocolExpanded by remember { mutableStateOf(false) }
            Box {
                Surface(
                    modifier = Modifier.clickable { protocolExpanded = true },
                    color = if (lockedProtocol.isEmpty()) Color.Gray.copy(alpha = 0.2f) else Color.Cyan.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (lockedProtocol.isEmpty()) "自動偵測" else "鎖定: $lockedProtocol",
                            color = if (lockedProtocol.isEmpty()) Color.White else Color.Cyan,
                            fontSize = 8.sp
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = protocolExpanded,
                    onDismissRequest = { protocolExpanded = false },
                    modifier = Modifier.width(140.dp),
                    properties = PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(
                        text = { Text("自動偵測 (推薦)", fontSize = 11.sp) },
                        onClick = { onUpdateLockedProtocol(""); protocolExpanded = false }
                    )
                    HorizontalDivider(color = Color(0x11FFFFFF))
                    listOf("UART", "Serial USB", "AX12(UMBUS)", "CRSF", "S.Bus", "MAVLink v2").forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p, fontSize = 11.sp) },
                            onClick = { onUpdateLockedProtocol(p); protocolExpanded = false }
                        )
                    }
                }
            }
            
            if (isAX12Active) {
                Spacer(modifier = Modifier.width(8.dp))
                Badge(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                    contentColor = Color.Green
                ) {
                    Text("UMBUS 高速模式", fontSize = 7.sp, modifier = Modifier.padding(2.dp))
                }
            }
        }
    }
}
