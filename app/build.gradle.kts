plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.agneswd.ronumi"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "dev.agneswd.ronumi"
        // API 28 is the first release that reports unlock events through UsageStatsManager.
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") { dimension = "distribution" }
        create("play") { dimension = "distribution" }
    }

    buildFeatures {
        compose = true
    }
    sourceSets.getByName("main").assets.srcDir(rootProject.file("licenses"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val releaseKeystore = providers.environmentVariable("STILLPOINT_KEYSTORE").orNull
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = file(releaseKeystore)
            storePassword = providers.environmentVariable("STILLPOINT_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("STILLPOINT_KEY_ALIAS").orElse("stillpoint").get()
            keyPassword = providers.environmentVariable("STILLPOINT_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    "playImplementation"("com.android.billingclient:billing:9.1.0")
    testImplementation("junit:junit:4.13.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
