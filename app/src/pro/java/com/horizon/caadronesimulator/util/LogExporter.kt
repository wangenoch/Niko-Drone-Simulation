package com.horizon.caadronesimulator.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.horizon.caadronesimulator.model.DroneState
import java.io.File
import java.io.FileOutputStream

import com.horizon.caadronesimulator.R

/**
 * [v1.5.2] 專業日誌匯出工具 (Log Exporter)
 * 職責：處理診斷數據的格式化、MediaStore 寫入與舊版本相容性儲存。
 */
object LogExporter {

    fun exportDiagnosticLog(
        context: Context,
        state: DroneState,
        physicalLog: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val combinedLog = buildString {
                append("=== NIKO DRONE SIMULATOR DIAGNOSTIC REPORT ===\n")
                append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("Profile: ${state.hardwareProfile?.id ?: "Generic"}\n\n")
                append("=== SYSTEM LOG (LOGCAT) ===\n")
                append(state.logcatContent.takeLast(5000))
                append("\n\n=== PHYSICAL STATE SNAPSHOTS ===\n")
                append(physicalLog)
            }
            
            val fileName = "Niko_FullDiag_${System.currentTimeMillis()}.txt"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(combinedLog.toByteArray())
                    }
                    onSuccess(context.getString(R.string.log_export_success))
                } ?: onError(context.getString(R.string.log_export_create_fail))
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { it.write(combinedLog.toByteArray()) }
                onSuccess(context.getString(R.string.log_export_success))
            }
        } catch (e: Exception) {
            // [v1.6.3] 備援機制：若公共目錄權限受阻，嘗試寫入 App 私有外部空間
            try {
                val privateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "Niko_RescueLog_${System.currentTimeMillis()}.txt"
                val file = File(privateDir, fileName)
                FileOutputStream(file).use { it.write(buildString { 
                    append("=== RESCUE LOG (Permission Fallback) ===\n")
                    append("Original Error: ${e.message}\n\n")
                    append(state.logcatContent.takeLast(10000))
                }.toByteArray()) }
                onSuccess(context.getString(R.string.log_export_fallback, file.name))
            } catch (e2: Exception) {
                onError(context.getString(R.string.log_export_fail, e.message ?: "Unknown"))
            }
        }
    }
}
