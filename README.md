# 🚁 Niko Drone Simulator (v1.8.01)

[![Android Version](https://img.shields.io/badge/Android-9.0%2B%20(API%2036%20Ready)-brightgreen.svg)](https://developer.android.com)
[![iOS Version](https://img.shields.io/badge/iOS%20%2F%20iPadOS-17.0%2B%20(USB--C)-000000.svg)](https://developer.apple.com/ios/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Swift](https://img.shields.io/badge/Swift-5.9%2B%20%7C%20Metal-F05138.svg)](https://developer.apple.com/swift/)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20SwiftUI-orange.svg)](https://developer.android.com)
[![Foldable Ready](https://img.shields.io/badge/Foldable-iPhone%20Duo%20Adaptive-purple.svg)](https://github.com/wangenoch/Niko-Drone-Simulation)

**Niko Drone Simulator** 是一款專為考照學員開發的跨平台專業無人機飛行訓練系統。透過高度擬真的 60FPS 物理模擬與強大的硬體搖桿相容性，本專案將您的 **Android 裝置** 與 **USB Type-C iPhone / iPad（含 iPhone Duo 摺疊雙螢幕）** 轉化為不受場地、天氣限制的專業術科練習平台。

---

## 🚀 v1.8.01 重大里程碑更新報告

從 v1.7.0 演進至 v1.8.01，本模擬器完成了**雙平台原生架構升級（Android + iOS）**、**逃逸衝量物理校準**、**放風箏牽引教學模式（Tethered Mentor）**，以及針對 **Android 16 (API 36)** 與 **iPhone Duo 摺疊雙螢幕** 的全面適配。以下為本次里程碑更新摘要：

### 1. 雙平台原生架構與 iOS / iPhone Duo 支援 🍎
* **iOS / iPadOS 原生版本登場 (USB Type-C 專屬)**：採用 100% 原生 **Swift + SwiftUI + Metal 60FPS 渲染引擎** 與 `AVAudioEngine` 即時四軸混音合成器，完整移植 1:1 物理參數與考場模型。
* **iPhone Duo 摺疊三態自適應佈局 (Foldable Posture Engine)**：支援 **標準單螢幕 (Compact)**、**全展開大螢幕 HUD (Expanded Unfolded)**，以及專為外接實體遙控器設計的 **90° 帳篷折疊分屏模式 (Tabletop Split Deck：上螢幕 100% 無遮擋 3D 視野 + 下螢幕獨立戰術儀表與實體搖桿配置艙)**。
* **跨平台 Monorepo 結構重組**：專案正式劃分為 `android/`、`ios/` 與 `docs/` 三大模組，共享一致的物理常數、音效合成曲線與考照判定標準。

### 2. 系統相容性與效能強化 (Android 16 API 36 Ready) ⚡
* **Android 16 (API 36) 大螢幕與無邊框相容**：全面升級 `enableEdgeToEdge()`，移除舊版方向與尺寸鎖定限制，完美通過 Google Play 最新大螢幕與摺疊機規範。
* **R8 Full Mode 深度極致優化**：啟用 R8 完整壓縮與代碼混淆規則，提升執行期 60Hz 物理積分穩定度並大幅縮減安裝包體積。
* **硬體相容性保障**：實裝遊戲模式宣告（`android:isGame="true"`），消除部分機型因系統節能服務（GOS）造成的動力積分延遲。

### 3. 物理引擎：逃逸衝量與決定論判定 ⚖️
* **逃逸衝量物理校準 (Escape Impulse, v1.7.35+)**：精確重構地面接觸與脫離瞬間的法向衝量與摩擦阻尼，徹底消除微幅彈跳與貼地滑行異常。
* **路跡判官系統 (Trajectory Judge)**：採用「向量路徑穿透判定」搭配 `entrySpeed` 進入速度快照，損毀判定不再受靈敏度 (Rate) 或瞬間油門指令干擾。
* **損毀狀態自旋鎖 (Impact Latch)**：實裝損毀狀態不可逆機制，根除多執行緒競態條件（Race Condition）造成的砸地後隨機復原問題。

### 4. 創新教學系統：放風箏牽引模式 (Tethered Mentor, v1.7.25+) 🪁
* **3D 動態牽引繩模擬**：內建虛擬教官牽引繩系統，提供可調式限制半徑與彈性回復力，協助初學者建立空間感與定點懸停肌肉記憶。
* **多機型物理特性切換**：完整內建 **Niko Pro (標準考照機)**、**T25 (進階練習機)**、**T40 (重型植保機)** 與 **FPV 穿越機** 專屬動力曲線與質量慣性。

### 5. 控制系統與在地化體驗升級 🎮
* **全系統座標歸一化與無視 Rate 行程判定**：實體遙控器與虛擬觸控底層完全對位；即使在低 Rate 設定下，T40 重型機型打桿到底仍可 100% 精準觸發「內八解鎖 (CSC)」與「落地上鎖」。
* **中英雙語在地化補全**：完善考照規範提示、損毀診斷標籤（`撞擊障礙物`、`撞擊角錐`、`飛機翻覆`、`已飛離空域`）與垂直軸手感設定。

### 🎁 特別感謝
* **測試與建議**：飛能科技、飛恩模型 全體考照班教官與學員。

---

## 🎮 強大的硬體遙控器支援 (Joystick Support)

為了追求極致的操控手感，本模擬器在 Android (USB OTG / Bluetooth) 與 iOS Type-C (GameController HID / USB-C) 針對市面上各類專業與消費級控制設備進行了深度優化：

### 1. DJI 全系列遙控器 (深度整合)
* **相容型號**：DJI FPV Remote Controller 2/3 (灰柄)、RC-N1/N2/N3 系列及初代大黑遙控器。
* **操作特性**：完整支援 DJI 玩家習慣的「內八字 (CSC)」解鎖邏輯與回中油門特性，無需重新適應。

### 2. 專業航模遙控器 (EdgeTX / OpenTX USB HID)
* **品牌相容**：RadioMaster (TX16S, Zorro, Boxer, Pocket)、Jumper、TBS Tango 2、FrSky 等。
* **高階自定義**：透過 USB Type-C 直連手機或平板，支援多通道軸向映射、正反向切換与三段開關配置。
* **帶屏遙控器**：支援最新 RadioMaster AX12 (含內置 UMBUS 通訊)。

### 3. 藍牙搖桿與 Type-C 行動背夾控制器
* **主流手把**：Xbox Series X/S、PS5 DualSense、PS4 DualShock 等。
* **行動背夾**：支援 Razer Kishi Ultra/V2、Backbone One (USB-C) 等直插式控制器。

---

## 🛠️ 專業映射與校準系統

我們開發了一套直覺的設定工具，確保任何硬體都能精確匹配：

* **✨ 一鍵引導設定 (Setup Wizard)**：跟隨指示撥動桿位，系統自動判定日本手 / 美國手 / 中國手 (Mode 1/2/3) 與軸向正反向。
* **🎯 精確校準工具**：定義物理中位點與邊界，徹底解決搖桿飄移 (Drift) 或行程不足的問題。
* **⚙️ 進階參數控制**：提供死區 (Deadzone) 調整、靈敏度 (Rate / Expo)、半油門模式以及機型物理參數切換。

---

## 🌟 核心特色

### 1. 專業級 HUD 監控面板與考場視角
* **雷達與俯視輔助視窗 (PIP)**：即時視覺化考場邊界、H 點起降區與相對位置。
* **FPV / OSD 模式**：模擬真實圖傳數據疊加（高度、速度、傾角、風速、電池與飛行計時）。
* **多視角與動態縮放切換**：支援飛手視角 (Pilot Ground View)、跟隨視角 (Chase Cam)、FPV 視角與一鍵變焦切換（1.0x / 1.3x / 1.5x）。

### 2. 進階環境與考照課目模擬
* **標準術科考場**：內建矩形航線（定高平飛）、水平八字圓周、定點起降與盲飛訓練模式。
* **動態氣候系統**：可調整蒲福風級、隨機風向、陣風與亂流強度，以及地面磁場干擾模擬。
* **光影調節**：支援切換不同時段（晨昏、正午、夜間照明），並可調整地面投影深淺以輔助高度判斷。

---

## 📂 雙平台專案架構 (Monorepo Structure)

```text
NikoDroneSimulator/
├── android/                 # Android 原生專案 (Kotlin + Jetpack Compose + OpenGL ES 2.0)
│   └── app/src/main/java/com/horizon/nikonikodronesimulator/
│       ├── model/           # 核心物理參數、全域狀態機與設定持久化
│       ├── drones/          # 各機型 (Niko Pro, T25, T40, FPV) 物理與 3D 幾何定義
│       ├── logic/           # 60Hz 物理積分、碰撞判定與搖桿控制器仲裁
│       ├── render/          # OpenGL ES 3D 渲染引擎與考場環境繪製
│       ├── audio/           # 四軸獨立轉速混音與環境音效引擎
│       └── ui/              # Jetpack Compose 響應式 HUD 與設定介面
├── ios/                     # iOS / iPadOS 原生專案 (Swift 5.9 + SwiftUI + Metal)
│   ├── NikoDroneSimulator.xcodeproj
│   └── NikoDroneSimulator/
│       ├── Model/           # 與 Android 1:1 對齊之物理常數、狀態與翻譯系統
│       ├── Drones/          # 四大機型動力配置與 3D 網格參數
│       ├── Logic/           # 60Hz 決定論物理引擎、GameController USB-C 搖桿與 iPhone Duo 摺疊偵測
│       ├── Render/          # Metal 60FPS 3D 渲染管線與雙視角考場鏡頭
│       ├── Audio/           # AVAudioEngine 即時合成四軸馬達聲學引擎
│       └── UI/              # SwiftUI HUD、設定面板與 iPhone Duo 摺疊三態自適應佈局
└── docs/                    # 雙平台技術規格書與 AI 上下文防護規範 (AI_CONTEXT_SPECS.md)
```

---

## 📱 系統配置要求

### Android 版本
* **最低版本**：Android 9.0 (Pie / API 28)，完整適配至 **Android 16 (API 36)**。
* **硬體建議**：4GB RAM 或以上；支援 USB OTG（用於外接遙控器）。
* **圖形支援**：OpenGL ES 2.0+。

### iOS / iPadOS 版本 (USB Type-C 專屬)
* **最低版本**：iOS 17.0 / iPadOS 17.0 或以上。
* **支援裝置**：配備 **USB Type-C** 連接埠之機型（iPhone 15 / 16 / 17 全系列、**iPhone Duo 摺疊機**、USB-C 款 iPad Pro / Air / mini）。
* **圖形支援**：Apple Metal GPU 硬體加速。

---

## ⚖️ 開源授權 (License)
本專案採用 GNU General Public License v3.0 (GPL-3.0) 授權。
* **開放透明**：本專案所有原始碼均對公眾開放。
* **傳承限制 (Copyleft)**：如果您修改了本專案的原始碼並進行分發，您必須同樣以 GPL-3.0 協議開放您的原始碼。這確保了本專案的改進將始終回饋給開源社群。
* **自由使用**：您可以自由地複製、修改並分發本軟體，但必須保留原作者（Enoch Wang）的版權聲明。
* **無擔保聲明**：本專案僅供教學與練習參考，開發者對使用本軟體造成的任何損害（包含但不限於實機飛行損失）不負任何賠償責任。
完整的授權條款請參閱專案根目錄下的 LICENSE 檔案。

## 👨‍💻 開發者資訊

* **Developer**: Enoch Wang
* **Version**: v1.8.01
* **Platforms**: Android (API 28–36) & iOS / iPadOS 17.0+ (USB Type-C & iPhone Duo)
* **Release Date**: October 2026

---

**Niko Drone Simulator - 讓專業遙控器與智慧型手機，共同成就您的飛行大師之路。**

---
☕ **支持 Niko Niko 模擬器長期維護**
這是一個由個人開發者維護的專案。如果您覺得 Niko 的模擬練習對您的術科考試有幫助，歡迎贊助一杯咖啡，協助我們支付軟體開發、硬體測試機維護（如 USB 模組與電池更換）的費用。
[支持咖啡贊助基金](https://www.paypal.com/ncp/payment/C6TVZPUDPV82C)
