# [v1.7.7] 工業級 R8 混淆與安全保護規則 (Niko Drone Simulator)

# --- 1. 基本安全保護 ---
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# 隱藏所有原始碼檔名與行號，防止反編譯時洩漏邏輯結構
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- 2. 核心算法保護 (物理引擎與數據模型) ---
# 這些類別會被混淆成亂碼 (a, b, c...)，保護商業機密
-keep class com.horizon.nikonikodronesimulator.logic.PhysicsEngine { *; } # 若有反射調用才需 keep，否則建議混淆
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# --- 3. 必須保留的進入點 (Keep Points) ---
-keep class com.horizon.nikonikodronesimulator.MainActivity { *; }

# --- 4. 資源與 Compose 相容性 ---
# 確保 Compose 的狀態管理與重組邏輯不被損壞
-keepclassmembers class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class **.R$* {
    public static <fields>;
}

# --- 5. USB Serial 庫保護 ---
# 第三方庫通常需要保留特定的 native 方法或序列化介面
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# --- 6. OpenGLES 渲染保護 ---
# 確保渲染回調方法不被混淆導致 3D 畫面黑屏
-keepclassmembers class * extends android.opengl.GLSurfaceView$Renderer {
    public void onSurfaceCreated(...);
    public void onSurfaceChanged(...);
    public void onDrawFrame(...);
}
