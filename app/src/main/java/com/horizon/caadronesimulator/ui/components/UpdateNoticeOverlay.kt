package com.horizon.caadronesimulator.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

import com.horizon.caadronesimulator.model.AppConfig

@Composable
fun UpdateNoticeOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(enabled = false) {}
            .zIndex(2000f),
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberScrollState()
        
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f),
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 內容區域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "🚀 v${AppConfig.CURRENT_VERSION} 功能升級報告",
                        color = Color.Cyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    UpdateSection("1. 遙控器支援與通訊技術優化", listOf(
                        "專業一體化遙控器深度集成：正式支援 RadioMaster AX12 UMBUS 協議，具備自動偵測機制，不再需要額外橋接 USB 線。",
                        "通訊診斷面板：新增即時查看 USB 串口路徑、波特率 (Baud Rate) 與 PPS (每秒封包數)。",
                        "協議自動識別：支援 SBUS, IBUS, UMBUS 等協議。",
                        "除錯工具：新增 Buffer 緩存監測與原始 HEX 十六進位數據監控。",
                        "診斷日誌導出：新增匯出診斷日誌至手機下載資料夾功能。"
                    ))

                    UpdateSection("2. 專業飛行儀表與導航系統", listOf(
                        "專業航空姿態儀：新增紅色倒三角基準指標 (Lubber Line) 與方位字母標籤。",
                        "數位導航顯示：新增 0°~359° 數位航向標籤，人工地平儀區域放大 20%。",
                        "飛行軌跡優化：軌跡收納至雷達視圖，支援隨重置/撞機自動清空。"
                    ))

                    UpdateSection("3. 設定自動化與持久化", listOf(
                        "雙模式獨立配置：自動分開儲存「內置 UMBUS」與「外接 HID」的參數，切換模式自動恢復。",
                        "Auto Bind 增強：修復單通道自動偵測失效問題，支援偵測中點擊取消。",
                        "視野倍率調整：優化縮放範圍為 1.0X ~ 3.0X，更符合實際飛行透視。"
                    ))

                    UpdateSection("4. 使用者引導架構", listOf(
                        "頂層圖層架構：教學導覽層級調整至最高層，解決導覽被遮擋問題。",
                        "快速導覽按鈕：在系統設定新增「操作導覽」快捷鈕，隨時重新啟動新手教學。"
                    ))

                    UpdateSection("5. 互動式雙側相機控制系統", listOf(
                        "多態滑桿功能：FPV 模式顯示「相機仰角」，站位模式顯示「視線高度」。",
                        "自定義位置對調：可於一般設定對調高度與仰角拉桿的左右位置。",
                        "視覺優化：增加對稱側邊距，有效避免誤觸系統手勢。"
                    ))

                    UpdateSection("6. 智慧動態變焦輔助系統", listOf(
                        "自動化聯動邏輯：水平距離超過 15 公尺自動開啟，偵測搖桿操作自動收合選單並彈回視窗。",
                        "動態變焦望遠鏡：視窗倍率隨距離動態演算，使遠方無人機始終維持在面前 2 公尺處大小。",
                        "高精細觀察：視窗畫面與操作鏡頭同步，提供精確姿態反饋並具備圓角裁切設計。"
                    ))

                    UpdateSection("7. JoyFlight T4 視覺識別強化", listOf(
                        "燈號識別升級：優化馬達與機身 LED 亮度與飽和度，提供清晰「前綠後紅」姿態指引。",
                        "專業天線構造：於機身尾部新增精細雙向天線組件，提升真實度並利於判斷機尾位置。"
                    ))

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "⚠️ 已知問題",
                        color = Color(0xFFFF9800),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "• UMBUS 偵測限制：使用 UMBUS 協議時，需先開啟原廠 APP (RadiomasterAX) 後再關閉，否則系統可能無法正確偵測。\n" +
                               "• 訊號穩定性：UMBUS 協議有機率發生訊號不穩定。若問題持續，建議改回外接方式。",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "🎁 特別感謝 (Special Thanks)",
                        color = Color(0xFFE1BEE7),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    AppConfig.SPECIAL_THANKS.forEach { credit ->
                        Text(
                            text = credit,
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }

                // 底部滾動提示漸層
                val isAtBottom by remember { derivedStateOf { scrollState.value >= scrollState.maxValue } }
                if (!isAtBottom) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF1A1A1A))
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("向下捲動查看更多", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 右上角關閉按鈕
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "關閉",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateSection(title: String, items: List<String>) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        items.forEach { item ->
            Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                Text("• ", color = Color.Cyan, fontSize = 13.sp)
                Text(
                    text = item,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
