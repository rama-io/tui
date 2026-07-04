import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

val currentYear = LocalDate.now().year

android {
    namespace = "com.rama.tui"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rama.tui"
        minSdk = 21
        targetSdk = 36
        versionCode = 7
        versionName = "$currentYear.$versionCode"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "tui_${versionName}.apk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/*.version"
            excludes += "META-INF/com/android/build/gradle/app-metadata.properties"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation(project(":bohio"))
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}