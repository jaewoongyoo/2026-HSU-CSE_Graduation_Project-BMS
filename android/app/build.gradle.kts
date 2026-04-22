plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) // ✅ 1번 순서: 베이스 코틀린
    alias(libs.plugins.kotlin.compose) // ✅ 2번 순서: 컴포즈 전용 코틀린
    alias(libs.plugins.ksp)// ✅ KAPT 대신 KSP 플러그인 장착
    kotlin("plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.han.battery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.han.battery"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        disable += "PackagedPrivateKey"
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Room DB
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.aws.android.sdk.iot)
    implementation(libs.aws.android.sdk.mobile.client)
    implementation(libs.gson) // 데이터 객체를 JSON 문자열로 변환할 때 편리합니다.

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation("io.ktor:ktor-client-logging:2.3.8")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.8")

    // SLF4J 구현 (로깅 경고 해결)
    implementation("org.slf4j:slf4j-android:1.7.36")

    testImplementation(libs.junit)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
