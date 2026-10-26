# 多國語言化 (Localization) 補完計畫

根據您的查驗，目前「系統與安全」設定中部分選項（如「起動動力增益」）仍顯示中文，未隨語系切換。本計畫旨在搜尋並修復全專案中遺漏的硬編碼 (Hardcoded) 字串，確保所有 UI 元素皆能正確支援多國語言。

## 查驗發現之遺漏字串

1.  **起動動力增益**：包含標籤與詳細描述。
2.  **自動偵測內置硬體**：目前在 Kotlin 代碼中手動判斷語系。
3.  **地面效應與非中心點受力**：進階物理選項。
4.  **FPV 機身結構顯示**：視覺設定中的選項。
5.  **特別感謝名單**：部分內容硬編碼為中文。

## 使用者評論要求

> [!IMPORTANT]
> **確保專業術語翻譯精確**
> 在英文語系下，確保無人機專業術語（如 Ground Effect, Startup Boost）使用標準稱呼。

## 提出變更

### 1. 資源檔案更新

#### [MODIFY] [strings.xml](file:///Users/enoch/AndroidStudioProjects/NikoDroneSimulator/app/src/main/res/values/strings.xml) & [values-en/strings.xml](file:///Users/enoch/AndroidStudioProjects/NikoDroneSimulator/app/src/main/res/values-en/strings.xml)
*   新增下列資源 ID：
    *   `settings_startup_boost_label`
    *   `settings_startup_boost_desc`
    *   `settings_ground_effect_label`
    *   `settings_ground_effect_desc`
    *   `settings_non_center_force_label`
    *   `settings_non_center_force_desc`
    *   `settings_show_fpv_body_label`
    *   `settings_auto_detect_hw_label`
    *   `notice_special_thanks_content`

### 2. UI 代碼重構

#### [MODIFY] [UnifiedSettingsScreen.kt](file:///Users/enoch/AndroidStudioProjects/NikoDroneSimulator/app/src/main/java/com/horizon/nikonikodronesimulator/ui/settings/UnifiedSettingsScreen.kt)
*   將硬編碼的中文標籤與描述替換為 `stringResource(R.string...)`。

#### [MODIFY] [VisualNavigationScreen.kt](file:///Users/enoch/AndroidStudioProjects/NikoDroneSimulator/app/src/main/java/com/horizon/nikonikodronesimulator/ui/settings/VisualNavigationScreen.kt)
*   將 FPV 機身顯示選項替換為多國語言資源。

#### [MODIFY] [AppConfig.kt](file:///Users/enoch/AndroidStudioProjects/NikoDroneSimulator/app/src/main/java/com/horizon/nikonikodronesimulator/model/AppConfig.kt)
*   移除在 Kotlin 中處理語系的邏輯，改由 UI 直接讀取資源。

## 驗證計畫

### 自動化測試
*   執行 `./gradlew assembleDebug` 確保資源引用正確。

### 手動驗證
*   進入設定頁面，切換語言至 **English**，檢查截圖中提到的區塊是否已變更。
*   檢查更新公告中的「特別感謝」是否也已在地化。
