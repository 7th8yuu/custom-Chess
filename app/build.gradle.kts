plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Web client ID cho Google Sign-In: ưu tiên biến môi trường (GitHub Secret), sau đó gradle.properties.
val googleWebClientId: String =
    System.getenv("GOOGLE_WEB_CLIENT_ID")?.trim()?.takeIf { it.isNotEmpty() }
        ?: (project.findProperty("GOOGLE_WEB_CLIENT_ID") as String?)?.trim()
        ?: ""

android {
    namespace = "com.customchess"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.customchess.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    // Keystore cố định nằm trong repo => SHA-1 không đổi giữa các lần build trên GitHub Actions,
    // nên Google Sign-In (OAuth client kiểu Android) luôn khớp.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/chessmate-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // Ký bằng khóa debug để APK release cài được ngay. Thay bằng khóa riêng khi phát hành thật.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // HTTP (API của AI, Lichess)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Tab trình duyệt ngay trong ứng dụng cho đăng nhập Lichess (OAuth)
    implementation("androidx.browser:browser:1.8.0")

    // Đăng nhập Google: Credential Manager (khi có Web client ID) hoặc Google Sign-In cổ điển (chỉ cần Android OAuth client)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    // org.json thật cho unit test (bản trong android.jar chỉ là khung rỗng)
    testImplementation("org.json:json:20240303")
}
