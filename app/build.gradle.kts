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
        versionCode = 100
        versionName = "1.0.0"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") { dimension = "distribution" }
        create("play") { dimension = "distribution" }
    }

    buildFeatures {
        compose = true
        resValues = true
    }
    // Lists the translated languages for the app language setting in Android 13 and later.
    // The default language comes from res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }
    sourceSets.getByName("main").assets.srcDir(rootProject.file("licenses"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val releaseKeystore = providers.environmentVariable("RONUMI_KEYSTORE").orNull
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = file(releaseKeystore)
            storePassword = providers.environmentVariable("RONUMI_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("RONUMI_KEY_ALIAS").orElse("ronumi").get()
            keyPassword = providers.environmentVariable("RONUMI_KEY_PASSWORD").get()
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

// AdMob IDs for the Play version. Debug builds always use Google's test IDs. Release builds read the real IDs from
// RONUMI_ADMOB_APP_ID, RONUMI_ADMOB_INTERSTITIAL, and RONUMI_ADMOB_REWARDED, and fall back to the test IDs.
val testAds = mapOf(
    "RONUMI_ADMOB_APP_ID" to "ca-app-pub-3940256099942544~3347511713",
    "RONUMI_ADMOB_INTERSTITIAL" to "ca-app-pub-3940256099942544/1033173712",
    "RONUMI_ADMOB_REWARDED" to "ca-app-pub-3940256099942544/5224354917",
)
androidComponents {
    onVariants(selector().withFlavor("distribution" to "play")) { variant ->
        val release = variant.buildType == "release"
        fun id(name: String): String {
            val real = providers.environmentVariable(name).orNull
            if (release && real == null) logger.warn("${variant.name}: $name is not set. The build uses a Google test ad ID.")
            return if (release && real != null) real else testAds.getValue(name)
        }
        variant.manifestPlaceholders.put("admobAppId", id("RONUMI_ADMOB_APP_ID"))
        for ((key, name) in listOf("ads_unit_interstitial" to "RONUMI_ADMOB_INTERSTITIAL", "ads_unit_rewarded" to "RONUMI_ADMOB_REWARDED")) {
            variant.resValues.put(variant.makeResValueKey("string", key), com.android.build.api.variant.ResValue(id(name), null))
        }
    }
}

dependencies {
    "playImplementation"("com.android.billingclient:billing:9.1.0")
    "playImplementation"("com.google.android.gms:play-services-ads:25.5.0")
    "playImplementation"("com.google.android.ump:user-messaging-platform:4.0.0")
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
