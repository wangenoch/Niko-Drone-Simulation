package com.horizon.caadronesimulator.model

/**
 * [v1.6.1] 全域應用程式配置 (憲法級預設值基準)
 * 集中管理版本號、手感、環境、視覺與系統安全等資訊，方便維護並作為「恢復原廠設定」單一事實來源。
 */
object AppConfig {
    // --- 基礎資訊 ---
    const val CURRENT_VERSION = "1.6.0"
    const val RELEASE_DATE = "2026-05"
    const val DEVELOPER = "Enoch Wang"

    const val SPECIAL_TITLE_ZH = "NikoNiko考照場地模擬器"
    const val SPECIAL_TITLE_EN = "Niko Drone Licensing Simulator"

    /** 根據當前語系獲取預設標題 (Fallback 邏輯) */
    fun getDefaultSpecialTitle(lang: String): String {
        return when (lang) {
            "zh" -> SPECIAL_TITLE_ZH
            else -> SPECIAL_TITLE_EN
        }
    }

    val SPECIAL_THANKS = listOf(
        "測試與建議：全體考照班教官與學員"
    )

    /** [v1.7.6] 攝影機模式內部 ID */
    const val CAM_MODE_STATION_TRACK = "STATION_TRACK"
    const val CAM_MODE_STATION_SMART = "STATION_SMART"
    const val CAM_MODE_STATION_FIXED = "STATION_FIXED"
    const val CAM_MODE_FOLLOW = "FOLLOW"
    const val CAM_MODE_FPV = "FPV"
    const val CAM_MODE_OBS = "OBSERVER"

    /** [v1.7.6] 風向內部 ID */
    const val WIND_DIR_NONE = "NONE"
    const val WIND_DIR_N = "N"; const val WIND_DIR_NE = "NE"; const val WIND_DIR_E = "E"; const val WIND_DIR_SE = "SE"
    const val WIND_DIR_S = "S"; const val WIND_DIR_SW = "SW"; const val WIND_DIR_W = "W"; const val WIND_DIR_NW = "NW"
    const val WIND_DIR_RANDOM = "RANDOM"

    /** [v1.7.6] 時間內部 ID */
    const val TIME_MORNING = "MORNING"
    const val TIME_NOON = "NOON"
    const val TIME_AFTERNOON = "AFTERNOON"

    /** 搖桿手感全域預設標準 */
    object JoystickDefaults {
        /** 基礎靈敏度：影響操縱桿推到底時的極限反應速度 */
        const val RATE = 1.2f
        /** 指數曲線：影響操縱桿中心區域的細膩度，數值越大中心越柔和 */
        const val EXPO = 0.4f
        /** 物理死區：過濾實體搖桿電位器在中位時的細微抖動 */
        const val DEADZONE = 0.05f
    }

    /** 環境與氣候預設標準 */
    object EnvironmentDefaults {
        /** 風力等級：0 為無風 */
        const val WIND_LEVEL = 0
        /** 風向：ID */
        const val WIND_DIRECTION = WIND_DIR_NONE
        /** 太陽渲染開關：控制光暈與耀光效果 */
        const val SUN_ENABLED = true
        /** 太陽方位：0.0~1.0 模擬從日出到日落的時間點 */
        const val SUN_POSITION = 0.5f
        /** 陰影深淺：影響場地陰影的視覺強度 */
        const val SHADOW_INTENSITY = 1f
        /** 雲層密度：影響天空覆蓋程度 */
        const val CLOUD_DENSITY = 0.5f
        /** 氣象模式：0=無雲, 1=卷雲, 2=積雲, 3=層雲 */
        const val WEATHER_MODE = 2 
        /** 雲層顯示開關 */
        const val SHOW_CLOUDS = true
        /** 遠景山脈顯示開關 */
        const val SHOW_MOUNTAINS = true
        /** 進階大氣物理：開啟後會實施高度風切與陣風模擬 */
        const val HARDCORE_PHYSICS = true
    }

    /** 視覺導航與 UI 介面預設標準 */
    object VisualDefaults {
        /** 主視角視野角度 (Field of View) */
        const val MAIN_FOV = 45f
        /** 縮放倍率 */
        const val ZOOM_FACTOR = 0.5f
        
        /** 初始攝影機模式 */
        const val CAMERA_MODE = CAM_MODE_STATION_TRACK
        /** 智慧縮放模式 ID */
        const val CAMERA_MODE_SMART_ID = CAM_MODE_STATION_SMART

        // --- 站位視角 (固定) 專屬預設值 ---
        /** 觀察員站位高度 (公尺) */
        const val OBSERVER_HEIGHT = 1.6f
        /** 站位視角固定預設仰角 (度) */
        const val OBSERVER_TILT = 32.0f
        /** 固定模式初始縮放倍率 */
        const val ZOOM_FACTOR_FIXED = 0.5f

        // --- 站位視角 (追蹤) 專屬預設值 ---
        /** 追蹤模式初始高度 (公尺) */
        const val OBSERVER_HEIGHT_TRACKING = 6.0f
        /** 追蹤模式初始仰角 (度) */
        const val OBSERVER_TILT_TRACKING = 0.0f
        /** 追蹤模式初始縮放倍率 */
        const val ZOOM_FACTOR_TRACKING = 1.5f

        /** 是否顯示頂部特別標題 */
        const val SHOW_SPECIAL_TITLE = true
        /** 是否顯示側邊觸控操作拉桿 (輔助操作用) */
        const val SHOW_SIDE_SLIDERS = true
        /** 是否顯示高度與速度側邊標尺 */
        const val SHOW_SIDE_RULERS = true
        /** 側邊拉桿位置是否左右交換 (適合特殊操作習慣) */
        const val REVERSE_SLIDERS = true
        /** AR 輔助投影：在地面顯示飛機垂直投影點 (H 點) */
        const val SHOW_GROUND_ANCHOR = true
        /** 子畫面自動位移：當飛機飛近邊緣時，PiP 視窗會自動避讓 */
        const val AUTO_PIP_RELOCATE = true
        /** 是否啟用遠距離自動放大助手 */
        const val ENABLE_ZOOM_ASSISTANT = true
        /** 是否顯示飛行軌跡 */
        const val SHOW_FLIGHT_PATH = false
        /** 多功能儀表板 (MFD) 模式：0=標準, 1=OSD, 2=姿態球 */
        const val HUD_MODE = 0
        /** 雷達縮放模式：0=固定, 1=自動, 2=最大化 */
        const val RADAR_ZOOM_MODE = 0
    }

    /** 系統與安全預設標準 */
    object SystemDefaults {
        /** 飛行圍欄：限高 30m 與場地邊界碰撞判定 */
        const val USE_FLIGHT_LIMIT = true
        /** 專業考核降落標準：落地垂直速度過快即判定損毀 */
        const val USE_STRICT_LANDING = true
        /** 自動隱藏系統狀態欄 (沉浸模式) */
        const val HIDE_STATUS_BAR = true
        /** 進入設定選單時是否暫停物理模擬 */
        const val PAUSE_IN_SETTINGS = true
        /** 啟動時是否自動掃描並連接 USB Serial */
        const val AUTO_CONNECT_ENABLED = false
        /** 是否套用各機型真實的物理參數 (質量、慣性等) */
        const val APPLY_PHYSICAL_SPECS = true
        /** 啟動時油門安全鎖狀態 */
        const val IS_THROTTLE_HOLD_ACTIVE = true
        /** 是否靜音 */
        const val IS_MUTED = true
        /** 是否顯示即時光影效果 */
        const val SHOW_SHADOW = true
        /** 是否顯示場地中的實體障礙物 (供進階練習) */
        const val SHOW_OBSTACLES = false

        // --- 專家模式保護區 ---
        /** 專家模式管理密碼 (解鎖需前往一般設定，點擊 Developer 文字 7 次) */
        const val ADMIN_PASSWORD = "12345678"
        /** 是否開啟專家模式快速鎖定 (隱藏敏感設定) */
        const val IS_EXPERT_MODE_LOCKED = false

        /** 是否啟用定點計時評測器 */
        const val IS_SPOT_TIMER_ENABLED = false
        /** 定點停留合格要求的秒數 */
        const val SPOT_TIMER_SECONDS = 5.0f
    }
}
