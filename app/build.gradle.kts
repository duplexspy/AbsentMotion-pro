import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

android {
    namespace = "app.absentmotion"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.absentmotion"
        minSdk = 33
        targetSdk = 36
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("boolean", "COMPATIBLE", "false")
    }

    val signingFile = rootProject.file("signing.properties")
    if (signingFile.isFile) {
        val keys = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            storeFile = rootProject.file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            vcsInfo.include = false
            isMinifyEnabled = true
            proguardFiles(file("proguard-rules.pro"))
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        baseline = file("lint-baseline.xml")
        checkReleaseBuilds = false
    }

    androidResources {
        noCompress += listOf("bin", "param")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("pro") {
            dimension = "distribution"
            applicationId = "app.absentmotion.pro"
            minSdk = 28
            buildConfigField("boolean", "COMPATIBLE", "true")
        }
    }

    sourceSets {
        named("test") {
            kotlin.directories += "src/test/kotlin"
            java.directories += "src/test/kotlin"
        }
        named("pro") {
            kotlin.directories += "src/foss/kotlin"
            assets.directories += "src/foss/assets"
            manifest.srcFile("src/compat/AndroidManifest.xml")
            res.directories += "src/compat/res"
        }
        named("testPro") {
            kotlin.directories += "src/testFoss/kotlin"
            java.directories += "src/testFoss/kotlin"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (!name.contains("Test")) compilerOptions.allWarningsAsErrors.set(true)
}

tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}

tasks.register<Copy>("copyProDebugApk") {
    from(layout.buildDirectory.dir("outputs/apk/pro/debug"))
    into(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    rename { "app-debug.apk" }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyProDebugApk")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.json:json:20260814")
}
