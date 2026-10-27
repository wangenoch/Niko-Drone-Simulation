# Niko Drone Simulator (iOS / iPadOS & iPhone Duo Foldable Edition)

本專案為 **Niko Drone Simulator v1.8.01** 的 iOS / iPadOS 原生版本（採用 Swift + SwiftUI + SceneKit/Metal + AVAudioEngine + GameController 框架），與 `android/` 版本共享 100% 相同的物理引擎基準 (`AI_CONTEXT_SPECS.md` / `CONTROL_SYSTEM_SPEC.md`)。

---

## 1. 硬體支援與 USB Type-C 專屬設計
* **最低系統需求**：iOS 17.0+（鎖定配備 **USB Type-C** 之 iPhone 15 / 16 / 17 系列、iPhone Duo 折疊機型及 USB-C iPad）。
* **免 MFi 即插即用 USB-C 遙控器**：透過 [`TypeCHidControllerManager.swift`](./NikoDroneSimulator/Logic/TypeCHidControllerManager.swift) 對接 Apple `GameController` 框架 (`GCExtendedGamepad`)，支援透過 Type-C 線直連主流無人機遙控器（RadioMaster / EdgeTX / OpenTX 等 USB HID 模式）。
* **120Hz ProMotion 支援**：已於 [`Info.plist`](./NikoDroneSimulator/Info.plist) 開啟 `CADisableMinimumFrameDurationOnPhone`，並在 [`SimulationSceneView.swift`](./NikoDroneSimulator/Render/SimulationSceneView.swift) 啟用 120 FPS 連續渲染。

---

## 2. iPhone Duo 折疊螢幕自適應四形態 (Foldable Adaptive Postures)
透過 [`MainAppScreen.swift`](./NikoDroneSimulator/UI/MainAppScreen.swift) 與 [`DroneState.swift`](./NikoDroneSimulator/Model/DroneState.swift) 的 `FoldablePostureMode`，支援自動感應與手動一鍵切換：
1. **`AUTO` / `STANDARD`（標準橫屏全螢幕）**：1:1 還原 Android 版戰術 HUD、左上雷達 (`150x100`)、FPV 子母畫面 (`150x100`)、遠距離 Zoom Assistant PiP 與雙虛擬搖桿。
2. **`DUO_DECK`（iPhone Duo 上下雙屏・戰術地面控制站）**：
   * **上螢幕**：100% 無手指遮擋之純淨 3D 飛行視場與浮動定點計時標籤。
   * **轉軸安全帶 (Hinge Seam Bar)**：提供視角與折疊形態快速切換。
   * **下螢幕**：化身專屬 **GCS 戰術操控台**，配置加大尺寸雙搖桿、三聯式 MFD（雷達 + 姿態球 + FPV 實時畫面）與實體風格戰術按鍵。
3. **`DUO_BOOK`（iPhone Duo 左右書本展開模式）**：左屏顯示全景 3D 考照場地，右屏作為獨立戰術航電與操控艙。
4. **`DUO_TENT`（iPhone Duo 90°~135° 半折立架模式）**：專為桌面立放並連接 **USB-C 實體遙控器** 設計；上螢幕面向飛手顯示 3D 視場，平放於桌面的下螢幕顯示 **USB-C HID 四通道即時行程監視器 (T/Y/P/R)**、姿態儀與大型桌面戰術巨集按鍵。

---

## 3. 開啟與編譯方式
1. 使用 Xcode 開啟 [`ios/NikoDroneSimulator.xcodeproj`](./NikoDroneSimulator.xcodeproj)。
2. 選擇目標裝置（任一 USB-C iPhone / iPad 或 Simulator），按下 `Cmd + R` 即可編譯執行。
