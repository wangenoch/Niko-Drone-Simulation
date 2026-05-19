package com.horizon.caadronesimulator.util
import android.content.Context
import com.horizon.caadronesimulator.model.DroneState

object LogExporter {
    fun exportDiagnosticLog(
        context: Context,
        state: DroneState,
        physicalLog: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Store 版佔位：不執行匯出
        onError("上架版不支援診斷日誌匯出")
    }
}
