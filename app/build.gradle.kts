plugins {
    alias(libs.plugins.android.application)

    alias(libs.plugins.google.services)


}

android {
    namespace = "com.example.amazonxcodebenders"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.amazonxcodebenders"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.biometric)
    implementation(libs.lottie)
    implementation(libs.androidx.cardview)
    implementation (platform(libs.firebase.bom))
    implementation (libs.firebase.analytics)
    implementation (libs.firebase.auth)
    implementation (libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation ("com.google.android.material:material:1.12.0")
    implementation(libs.okhttp)
    implementation(libs.mpandroidchart)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)


}