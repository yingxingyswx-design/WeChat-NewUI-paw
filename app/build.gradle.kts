plugins {
    id("com.android.application")
}

// Keep Gradle output out of app/, which publish.ps1 mirrors into the backup
// repo and backup.ps1 packs into the source snapshot.
layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(".gradle-build/app"))

android {
    namespace = "io.github.yingxingyswx_design.wechatglass"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.yingxingyswx_design.wechatglass"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    signingConfigs {
        // Same debug certificate the module has always been signed with, so
        // the APK can update the copy already installed on the phone.
        create("moduleDebug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("moduleDebug")
        }
    }

    buildFeatures {
        buildConfig = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

dependencies {
    compileOnly(files("libs/api-82.jar"))
    compileOnly("androidx.recyclerview:recyclerview:1.3.2")
}

val copyApk = tasks.register<Copy>("copyApk") {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("out"))
    rename { "wechat-glass-1.1.apk" }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyApk)
}
