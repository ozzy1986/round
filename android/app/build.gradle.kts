plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile")
}

val sentryDsn = (project.findProperty("SENTRY_DSN") as String?)?.trim().orEmpty()
val sentryEnabled = sentryDsn.isNotEmpty()

android {
    namespace = "com.raund.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.raund.app"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("VERSION_NAME") as String?) ?: "1.0"
        buildConfigField("String", "API_BASE_URL", "\"https://round.ozzy1986.com/\"")
        buildConfigField("String", "SENTRY_DSN", "\"\"")
        buildConfigField("boolean", "SENTRY_ENABLED", "false")
    }

    signingConfigs {
        getByName("debug") {
            // default debug keystore
        }
        // For production: create a keystore and set RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD (e.g. in local.properties or CI secrets).
        create("release") {
            val storeFile = project.findProperty("RELEASE_STORE_FILE") as String?
            val storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
            val keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
            val keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
            if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    val allowDebugSignedRelease =
        (project.findProperty("ALLOW_DEBUG_SIGNED_RELEASE") as String?)
            ?.toBooleanStrictOrNull() == true
    val productionReleaseBuildRequested = gradle.startParameter.taskNames.any {
        val taskName = it.lowercase()
        taskName.contains("release") && !taskName.contains("localrelease")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release").let { releaseSigning ->
                if (releaseSigning.storeFile?.exists() == true) releaseSigning
                else if (allowDebugSignedRelease) {
                    logger.warn(
                        "WARN: Release keystore not set — using debug keystore because " +
                            "ALLOW_DEBUG_SIGNED_RELEASE=true. Do not use this build for production."
                    )
                    signingConfigs.getByName("debug")
                } else if (productionReleaseBuildRequested) {
                    throw org.gradle.api.GradleException(
                        "Release keystore not set. Configure RELEASE_STORE_FILE, " +
                            "RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD, " +
                            "or pass -PALLOW_DEBUG_SIGNED_RELEASE=true for non-production builds."
                    )
                } else {
                    signingConfigs.getByName("debug")
                }
            }
            buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
            buildConfigField("boolean", "SENTRY_ENABLED", sentryEnabled.toString())
        }

        create("localRelease") {
            initWith(getByName("release"))
            matchingFallbacks.add("release")
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "SENTRY_DSN", "\"\"")
            buildConfigField("boolean", "SENTRY_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    baselineProfile(project(":baselineprofile"))
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("androidx.media:media:1.7.0")
    if (sentryEnabled) {
        implementation("io.sentry:sentry-android:8.34.0")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
