package com.horizon.caadronesimulator.logic

import android.content.Context
import com.horizon.caadronesimulator.model.ConnectionStatus

/**
 * [v1.7.6] 合規版 (Store) 內置通訊管理器佔位符
 * 職責：提供與 Pro 版一致的介面簽署，確保 Main 代碼編譯通過。
 */
class InternalCommManager(
    context: Context,
    onStatusUpdate: (Boolean, String) -> Unit,
    onDataReceived: (Float, Float, Float, Float) -> Unit,
    onRawChannelsReceived: (List<Float>) -> Unit,
    onDiagnosticUpdate: (String, String, String, Map<String, Any>) -> Unit,
    onProtocolDetected: (String) -> Unit,
    onConnectionStatusUpdate: (ConnectionStatus) -> Unit
) {
    fun setLockedProtocol(p: String) {}
    fun toggleLogcat(e: Boolean) {}
    fun scanAndConnect() {}
    fun stopAll() {}
    fun setBaudRate(b: Int) {}
    fun setLockedPath(p: String) {}
    fun tryExportReport(a: android.app.Activity, o: (String) -> Unit) {}
    fun injectLog(msg: String) {} // [關鍵修正] 補齊被 InputCoordinator 引用的方法
    fun unregister(context: Context) {}
    fun register(context: Context) {}
    fun getFullLog(): String = ""
}
