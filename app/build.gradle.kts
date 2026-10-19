plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.star_link"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.star_link"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions.add("storeType")
    productFlavors {
        create("cafe") {
            dimension = "storeType"
            applicationIdSuffix = ".cafe"
            versionNameSuffix = "-cafe"
            resValue("string", "app_name", "스타링크 카페")
        }
        create("pub") {
            dimension = "storeType"
            applicationIdSuffix = ".pub"
            versionNameSuffix = "-pub"
            resValue("string", "app_name", "스타링크 주점")
        }
        create("restaurant") {
            dimension = "storeType"
            applicationIdSuffix = ".restaurant"
            versionNameSuffix = "-restaurant"
            resValue("string", "app_name", "스타링크 식당")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // 이미지 로딩용 필수 라이브러리
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room DB
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // 손님 앱은 오직 전송(Client) 역할만 수행합니다.
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-websockets:2.3.12")
    
    // JSON 데이터 처리를 위한 직렬화 라이브러리 추가
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
