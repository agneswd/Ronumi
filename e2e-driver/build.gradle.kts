plugins { alias(libs.plugins.android.application) }
android {
    namespace = "dev.agneswd.ronumi.e2e"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "dev.agneswd.ronumi.e2e"
        minSdk = 28
        targetSdk = 36
    }
}
