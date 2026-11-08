import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.oriftts.astur"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.oriftts.astur"
        // 降到 Android 8.0，覆盖更多旧款手机与安卓手表
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.5"

        // 覆盖主流手机/Wear OS 手表(64位)与老式安卓手表(32位 v7a)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    val signingPropertiesFile = rootProject.file("keystore.properties")
    val releaseSigning = if (signingPropertiesFile.isFile) {
        val signingProperties = Properties().apply {
            signingPropertiesFile.inputStream().use { load(it) }
        }
        signingConfigs.create("release") {
            storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            // AGP 9.4：同时启用 R8 混淆、代码优化及资源压缩。
            optimization {
                enable = true
            }
            signingConfig = releaseSigning
        }
    }
    // 同时产出通用 APK（单包内置全部 ABI），方便分发安装
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.foundation.base)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
