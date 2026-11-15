// Pleos Connect 차량 화면 시뮬레이터 (르르링 앱과 별개의 앱)
// 영상 「We'r EV_PleosConnect_주행시나리오」를 재현: EV 주행 → 배터리 20% → 발전 모드 전환 → 회복 → EV 복귀
// 화면은 Compose 로 직접 그린다 (차량/에뮬레이터 WebView 렌더링 문제 회피)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pleosconnect"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pleosconnect"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
