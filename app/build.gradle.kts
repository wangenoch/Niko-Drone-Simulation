plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.horizon.caadronesimulator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.horizon.caadronesimulator"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.2.86"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // [v1.3.9] README ➔ Kotlin 代碼生成轉譯層
    // 讀取根目錄 README.md 並將升級報告區塊轉譯為 Kotlin 常數，消除檔案冗餘
    val generateReleaseContent by tasks.registering {
        val readmeFile = rootProject.file("README.md")
        val outputFile = file("src/main/java/com/horizon/caadronesimulator/generated/ReleaseContent.kt")
        
        inputs.file(readmeFile)
        outputs.file(outputFile)
        
        doLast {
            val content = readmeFile.readText()
            // 轉義反斜槓與美金符號，避免 Kotlin String Template 衝突
            val escapedContent = content.replace("\\", "\\\\").replace("$", "\\$")
            
            outputFile.parentFile.mkdirs()
            outputFile.writeText("""
                package com.horizon.caadronesimulator.generated

                /**
                 * 由 Gradle 任務 syncReadme 自動生成，請勿手動修改。
                 * 來源: 專案根目錄 README.md
                 */
                object ReleaseContent {
                    const val RAW_MARKDOWN = ""${'"'}$escapedContent""${'"'}
                }
            """.trimIndent())
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        dependsOn(generateReleaseContent)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // USB Serial Support (for SIYI MK15 / CP2102)
    implementation(libs.usb.serial)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
