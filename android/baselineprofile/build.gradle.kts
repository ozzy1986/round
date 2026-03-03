plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.raund.app.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    targetProjectPath = ":app"
}

dependencies {
    implementation("androidx.test:core-ktx:1.6.0")
    implementation("androidx.test:runner:1.6.0")
    implementation("androidx.test:rules:1.6.0")
    implementation("androidx.test.ext:junit-ktx:1.2.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}
