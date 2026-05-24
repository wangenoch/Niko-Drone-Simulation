package com.horizon.caadronesimulator.ui.interaction

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.horizon.caadronesimulator.model.DroneState

/**
 * [v1.7.9] 觸控縮放交互層 (TouchZoomLayer)
 * 職責：處理兩指撥動手勢（Pinch-to-zoom），調節攝像機倍率。
 * 優點：獨立組件設計，與 HUD 邏輯隔離，具備自動干預避讓機制。
 */
@Composable
fun TouchZoomLayer(
    state: DroneState,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    // 僅當 zoom 比例發生變化時處理（過濾純平移手勢）
                    if (zoom != 1f) {
                        onUpdateState {
                            // 1. 計算新倍率：在 0.5x 到 5.0x 之間，保持專業視角穩定
                            val newZoom = (zoomFactor * zoom).coerceIn(0.5f, 5.0f)
                            
                            // 2. 更新狀態
                            zoomFactor = newZoom
                            
                            // 3. 觸發手動干預機制：更新時間戳以暫停 CameraDirector 的自動智慧縮放
                            lastManualTouchTime = System.currentTimeMillis()
                        }
                    }
                }
            }
    )
}
