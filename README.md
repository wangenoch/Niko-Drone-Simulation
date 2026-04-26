# 🚁 Niko Drone Simulator (v1.0)

[![Android Version](https://img.shields.io/badge/Android-9.0%20%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-orange.svg)](https://developer.android.com)

**Niko Drone Simulator** 是一款專為考照學員開發的專業飛行訓練系統。透過高度擬真的物理模擬與強大的硬體相容性，本專案將您的行動裝置轉化為一個不受場地、天氣限制的專業練習平台。

---

## 🎮 強大的硬體遙控器支援 (Joystick Support)

為了追求極致的操控手感，本模擬器針對市面上各類專業與消費級控制設備進行了深度優化：

### 1. DJI 全系列遙控器 (深度整合)
*   **相容型號**：DJI FPV Remote Controller 2 (灰柄)、RC-N1/N2/N3 系列及初代大黑遙控器。
*   **操作特性**：完整支援 DJI 玩家習慣的「內八字 (CSC)」解鎖邏輯，無需重新適應。

### 2. 藍牙搖桿與行動控制器 (Wireless Liberty)
*   **主流手把**：Xbox Series X/S、PS5 DualSense、PS4 DualShock 等。
*   **行動背夾**：支援 Razer Kishi, Backbone One 等直接插入式或藍牙連接控制器。

### 3. 專業航模遙控器 (EdgeTX / OpenTX)
*   **品牌相容**：RadioMaster (TX16S, Zorro, Boxer)、Jumper、TBS Tango 2、FrSky 等。
*   **高階自定義**：支援多通道映射，可自由分配搖桿、旋鈕與三段開關。
*   **帶屏遙控器**：支持最新RadioMaster AX12

---

## 🛠️ 專業映射與校準系統

我們開發了一套直覺的設定工具，確保任何硬體都能精確匹配：

*   **✨ 一鍵引導設定 (Setup Wizard)**：跟隨指示撥動桿位，系統自動判定左/右手模式 (Mode 1/2/3) 與軸向正反向。
*   **🎯 精確校準工具**：定義物理中位點與邊界，徹底解決搖桿飄移 (Drift) 或行程不足的問題。
*   **⚙️ 進階參數控制**：提供死區 (Deadzone) 調整、半油門模式以及物理特性參數切換。

---

## 🌟 核心特色

### 1. 專業級 HUD 監控面板
*   **雷達模式**：即時視覺化場地邊界與相對位置。
*   **FPV / OSD 模式**：模擬真實圖傳數據疊加（高度、速度、傾角）。
*   **姿態球模式**：高精度反饋無人機的翻滾 (Roll) 與俯仰 (Pitch) 姿態。

### 2. 進階環境模擬
*   **動態氣候系統**：可調整風力等級、隨機風向與亂流激烈度。
*   **光影調節**：支援切換不同時段，並可調整投影深淺以輔助高度判斷。
*   **物理特性切換**：可選取不同機型（如迷你機、穿越機），載入專屬的動力物理參數。

### 3. 完善的引導與安全機制
*   **互動教學**：內建歡迎導覽、搖桿映射教學與環境設定指引。
*   **碰撞偵測**：擬真的損毀判定與場地邊界警告系統。

---

## 🚀 技術架構

*   **UI 框架**：100% 使用 **Jetpack Compose** 打造流暢的響應式介面。
*   **渲染引擎**：基於 OpenGL ES 的高性能 3D 模擬渲染。
*   **架構模式**：狀態驅動 (State-driven) 設計，確保低延遲的數據同步。

## 👨‍💻 開發者資訊

*   **Developer**: Enoch Wang
*   **Release Date**: April 2026

---

**Niko Drone Simulator - 讓專業遙控器與智慧型手機，共同成就您的飛行大師之路。**

本文由AI產出
