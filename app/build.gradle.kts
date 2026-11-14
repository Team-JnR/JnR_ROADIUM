plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jnr.roadium"
    // 주행 규제(CarUxRestrictions) 조회용 AAOS Car API
    useLibrary("android.car")
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jnr.roadium"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-mvp"
    }

    buildTypes {
        release {
            // R8 최적화 시 Pleos SDK(난독화된 AAR)와 kotlin 클래스 간 IllegalAccessError가 발생해
            // MVP에서는 코드 최적화를 끈다.
            optimization {
                enable = false
            }
            // 시연용: 디버그 키로 서명해 바로 설치 가능한 release APK를 만든다.
            // 정식 배포 시에는 별도 업로드 키로 교체할 것.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.navihelper)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
