# RadioMaster AX12 - UMBUS 協議開發規範 (正式版)

## 0. 規範來源
- **研究專案**: [rmeadomavic/ax12-research](https://github.com/rmeadomavic/ax12-research)[cite: 1, 2, 3]
- **核心協議**: UMBUS (RadioMaster Proprietary Internal Bus)[cite: 1]

## 1. 物理層配置
- **設備路徑**: `/dev/ttyS0`[cite: 1, 2]
- **通訊參數**: 921,600 baud, 8N1, 無流控 (No flow control)[cite: 1, 2]
- **系統權限**: 需具備獨佔訪問權限 (Exclusive Access)[cite: 1, 2]

## 2. 幀格式 (Frame Format)
UMBUS 每一幀的結構如下：
`[Sync: 0xA6] [Type/Len] [Header: 2 bytes] [Payload: Variable] [Checksum: 1 byte]`[cite: 1]
- **Sync**: 固定為 `0xA6`[cite: 1, 3]
- **Type/Len**: 第二個位元組通常代表幀類型，同時也代表總長度[cite: 1, 3]

## 3. 關鍵數據幀定義 (重點)
Agent 修改程式時，應優先處理以下數據類型：

### CHANNEL_DATA (Type: 0x57)
- **總長度**: 87 bytes[cite: 1, 3]
- **發送頻率**: 25 Hz (每 40ms 一次)[cite: 1]
- **Gimbal 搖桿映射 (Byte Offset 6-13)**:
    - 數據格式: **s16le** (Signed 16-bit, Little Endian)[cite: 1, 3]
    - 數值範圍: 約 `-500` 到 `+500`[cite: 1, 2]
    - 映射表:
        - **G0 (Offset 6-7)**: Yaw / Rudder (Left X)[cite: 1, 2]
        - **G1 (Offset 8-9)**: Pitch / Elevator (Right Y)[cite: 2]
        - **G2 (Offset 10-11)**: Throttle (Left Y)[cite: 2]
        - **G3 (Offset 12-13)**: Roll / Aileron (Right X)[cite: 2]

### HEARTBEAT (Type: 0x08)
- **總長度**: 7 bytes (MCU 發送)[cite: 1, 3]
- **處理方式**: 僅用於連線狀態監控，禁止將其內容映射至搖桿輸入[cite: 3]

## 4. 校驗算法 (CRC-8/MAXIM)
- **演算法**: CRC-8/MAXIM (Dallas 1-Wire)[cite: 1, 3]
- **多項式 (Poly)**: `0x31` (Normal) / `0x8C` (Reflected)[cite: 1, 3]
- **計算範圍**: `data[1]` 到 `data[n-2]` (排除 Sync byte 與 Checksum 本身)[cite: 1, 3]
- **初始化值 (Init)**:
    - 0x57 (Channel Data): `0x00`[cite: 1, 3]
    - 0x10 (Extended Telemetry): `0x7F`[cite: 1, 3]
    - 0x15 (ELRS Telemetry): `0x32`[cite: 1, 3]

## 5. 修改禁令
- **禁止偵測其他協議**: 除非明確指令，否則禁止 Agent 在程式中嘗試偵測 MAVLink, CRSF 或 S.Bus 等非 UMBUS 協議。
- **固定偏移量**: 嚴禁修改上述 Gimbal 的 Byte 偏移量 (6, 8, 10, 12)。
- **校驗強制性**: 在解析任何數據前，必須先執行 CRC 校驗。校驗失敗的封包必須立即丟棄，不得送入物理引擎。