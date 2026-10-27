import Foundation

/// [v1.8.01] 全系統物理常數定義 (DATUM: H-Pad)
/// 基準點 (0,0,0) 為 H 坪起飛點中心。
enum Constants {
    // 物理角錐位置 (相對於 H 點) [X, Z]
    static let CONE_POSITIONS: [[Float]] = [
        [-6.0, 12.0],  // P1: 左前
        [-12.0, 6.0],  // P2: 左中
        [-6.0, 0.0],   // P3: 左後 (與 H 點平齊)
        [0.0, 6.0],    // P4: 中心 (原座標系原點)
        [6.0, 12.0],   // P5: 右前
        [12.0, 6.0],   // P6: 右中
        [6.0, 0.0]     // P7: 右後 (與 H 點平齊)
    ]

    static let FIELD_WIDTH_HALF: Float = AppConfig.SystemDefaults.FIELD_WIDTH_HALF
    static let FIELD_Z_FRONT: Float = AppConfig.SystemDefaults.FIELD_Z_FRONT
    static let FIELD_Z_BACK: Float = AppConfig.SystemDefaults.FIELD_Z_BACK
    static let WARNING_BUFFER: Float = AppConfig.SystemDefaults.WARNING_BUFFER

    // 實體障礙物定義 [X, Z, 高度, 類型ID, 碰撞半徑]
    static let OBSTACLES: [[Float]] = [
        [-32.0, 16.0, 13.0, 0.0, 2.5],
        [-28.0, 31.0, 8.0, 2.0, 1.8],
        [32.0, 11.0, 10.0, 1.0, 2.0],
        [28.0, 1.0, 7.0, 3.0, 1.5],
        [35.0, 26.0, 11.0, 2.0, 1.8]
    ]
}
