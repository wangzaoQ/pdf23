buildscript {
    repositories {
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine/")
        }
        maven {
            url = uri("https://artifact.byteplus.com/repository/public/")
        }
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.github.megatronking.stringfog:gradle-plugin:5.2.0")
        classpath("com.github.megatronking.stringfog:xor:5.0.0")
        // google-services 通过插件别名（version catalog）管理
//        classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.2")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Kotlin 2.x + Parcelize 会引入 kotlin-parcelize-runtime；
// 某些老库仍会传递依赖 kotlin-android-extensions-runtime（已废弃），两者会产生重复类冲突。
subprojects {
    configurations.configureEach {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
}
