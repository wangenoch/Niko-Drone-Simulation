package com.horizon.caadronesimulator.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusLine(label: String, value: String, color: Color) {
    Row { 
        Text(label, color = Color.White, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(value, color = color, fontSize = 13.sp) 
    }
}

@Composable
fun ControlBtn(icon: Any, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, 
        modifier = Modifier
            .size(45.dp)
            .background(Color(0xAA333333), RoundedCornerShape(8.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) { 
        when (icon) {
            is String -> Text(icon, fontSize = 20.sp)
            is ImageVector -> Icon(icon, contentDescription = null, tint = Color.White)
        }
    }
}
