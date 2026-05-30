import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.epassport.app"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.epassport.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // local.properties の AI_OCR_API_KEY を BuildConfig に注入
        // 存在しない場合は空文字列でビルド（実行時にエラーになるため注意）
        val localProps = rootProject.file("local.properties").let { file ->
            if (file.exists()) Properties().apply { load(file.inputStream()) } else Properties()
        }
        val aiOcrApiKey = localProps.getProperty("AI_OCR_API_KEY", "")
        val aiOcrModel = localProps.getProperty("AI_OCR_MODEL", "gemini-1.5-flash")
        buildConfigField("String", "AI_OCR_API_KEY", "\"$aiOcrApiKey\"")
        buildConfigField("String", "AI_OCR_MODEL", "\"$aiOcrModel\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":sdk-nfc"))
    implementation(project(":sdk-ocr"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
