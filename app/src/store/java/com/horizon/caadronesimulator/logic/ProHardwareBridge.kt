package com.horizon.caadronesimulator.logic

import androidx.activity.ComponentActivity
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore

/**
 * [v1.7.6] 合規版 (Store) 硬體橋接器占位符
 * 職責：提供與 Pro 版一致的介面，但不包含任何敏感的內置串口或 Pro 特權代碼。
 */
object ProHardwareBridge {
    // [v1.7.6] 在 Store 版中，我們實例化一個 Dummy 管理器，確保 MainActivity 引用不為空
    private var _internalCommManager: InternalCommManager? = null
    val internalCommManager: InternalCommManager get() = _internalCommManager!!

    fun initialize(
        activity: ComponentActivity,
        droneState: DroneState,
        stickInputState: StickInputState,
        configStore: ConfigurationStore
    ) {
        // 初始化 Dummy 管理器，所有回調設為空操作
        _internalCommManager = InternalCommManager(
            activity, 
            { _, _ -> }, { _, _, _, _ -> }, { _ -> }, { _, _, _, _ -> }, { _ -> }, { _ -> }
        )
    }

    fun onResume() { /* No-op */ }
    fun onStop() { _internalCommManager?.stopAll() }
    fun onDestroy(activity: ComponentActivity) {
        _internalCommManager?.unregister(activity)
        _internalCommManager = null
    }
}
