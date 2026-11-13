import com.github.megatronking.stringfog.plugin.StringFogExtension
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.androidx.baselineprofile)
    id("stringfog")
}

android {
    namespace = "com.pdf.editor"
    compileSdk = 36

    defaultConfig {
        val appId = "com.test.pdf"
        val appVersionName = "1.0"
        applicationId = appId
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        val buildTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MMdd_HHmm_ss"))
        project.base.archivesName.set("AI PDF Editor_${buildTime}-v${appVersionName}-${appId}")
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("libs")
        }
    }

    flavorDimensions += "versionCode"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
baselineProfile {
    mergeIntoMain = true
}
configure<StringFogExtension> {
    implementation = "com.github.megatronking.stringfog.xor.StringFogImpl"
    packageName = "com.pdf.editor"
    // StringFog 5.2.0 ASM is incompatible with Kotlin 2.3 incremental debug classes.
    // Keep release string encryption, but skip instrumentation for local debug builds.
    enable = gradle.startParameter.taskNames.none { it.contains("debug", ignoreCase = true) }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.profileinstaller)
//    debugImplementation(libs.leakcanary.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.megatronking.stringfog:xor:5.0.0")

    implementation("com.github.getActivity:DeviceCompat:2.6")
    implementation("com.github.getActivity:XXPermissions:28.0")
    implementation("io.github.scwang90:refresh-layout-kernel:2.1.0")
    implementation("io.github.scwang90:refresh-header-material:2.1.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // PDF14-MIGRATION: 与源项目保持同版的原生 PDF 渲染依赖。
    implementation("io.legere:pdfiumandroid:2.0.1")
    implementation("com.blankj:utilcodex:1.31.1") {
        exclude(group = "androidx.appcompat")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
    implementation("com.tencent:mmkv:1.3.14")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.2"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    implementation("com.google.firebase:firebase-messaging-directboot:25.0.1")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("com.facebook.android:facebook-android-sdk:18.2.3")
    implementation("com.appsflyer:af-android-sdk:6.18.1")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")

    //ad
    implementation("com.google.android.gms:play-services-ads:24.8.0")
    implementation("com.google.android.gms:play-services-time:16.0.1")
    implementation("com.google.ads.mediation:pangle:7.8.0.7.0")
    implementation("com.google.ads.mediation:applovin:13.5.1.0")
    implementation("com.google.ads.mediation:facebook:6.21.0.0")
    implementation("com.google.ads.mediation:mintegral:17.0.31.0")
    implementation("com.google.ads.mediation:vungle:7.6.1.0")
    implementation("com.unity3d.ads:unity-ads:4.16.2")
    implementation("com.google.ads.mediation:unity:4.16.4.0")
    implementation("com.google.ads.mediation:ironsource:9.2.0.0")
    implementation("com.unity3d.ads-mediation:adquality-sdk") {
        version {
            strictly("9.1.1")
        }
    }


    // Restore these when the local modules exist in this workspace again.
    baselineProfile(project(":baselineprofile"))
    // implementation(project(":refresh"))
    // implementation(project(":adapter"))
    // implementation(project(":pop"))
    // implementation(project(":magicindicator"))
    // implementation(project(":android-pro-viewer"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("com.airbnb.android:lottie:6.7.1")
    implementation("com.github.hackware1993:MagicIndicator:1.7.0")
    implementation("io.github.cymchad:BaseRecyclerViewAdapterHelper4:4.3.4")
    implementation("io.github.razerdp:BasePopup:3.2.1")
// for androidx
}
