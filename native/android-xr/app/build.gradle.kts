plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.orgista.openstream"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.orgista.openstream"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "WEB_XR_URL",
            "\"https://openstream.orgista.com/xr/viewer\""
        )

        // FFmpeg software decoders ship native libs per ABI; the target
        // devices are 64-bit ARM, so ship only that to keep the APK lean.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }
}

dependencies {
    // Pinned to 1.10.0 to exactly match the NextLib FFmpeg extension build
    // (its version scheme is <media3>-<nextlib>), guaranteeing decoder ABI
    // compatibility.
    val media3Version = "1.10.0"

    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // FFmpeg software decoders for Media3 (the Jellyfin/Findroid approach):
    // decode codecs the hardware can't — TrueHD, DTS, E-AC3, FLAC, etc. — on
    // device in real time. This is Infuse's model: direct play with a
    // software-decode fallback, not server-style re-encoding.
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1")

    // Pure-Java SMB2/3 client for direct NAS access; no gateway required.
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    // Async image loading for remote TMDB posters/backdrops.
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
