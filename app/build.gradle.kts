plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hooplog.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hooplog.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.2.6"
    }

    signingConfigs {
        create("hooplog") {
            storeFile = file("keystore/hooplog-upload.jks")
            storePassword = "hooplogpass"
            keyAlias = "hooplog"
            keyPassword = "hooplogpass"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("hooplog")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hooplog")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")
}
