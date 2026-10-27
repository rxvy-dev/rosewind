plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.wayglass.app"
    compileSdk = 34
    defaultConfig { applicationId = "dev.wayglass.app"; minSdk = 26; targetSdk = 34; versionCode = 3; versionName = "1.2" }
    signingConfigs { getByName("debug") { storeFile = file("debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.material:material:1.12.0")
}
