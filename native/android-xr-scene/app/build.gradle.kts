plugins {
    id("com.android.application")
}

providers.environmentVariable("OPENSTREAM_ANDROID_XR_BUILD_DIR").orNull?.let { requestedBuildDir ->
    val localBuildDir = file(requestedBuildDir)
    require(localBuildDir.isAbsolute) { "OPENSTREAM_ANDROID_XR_BUILD_DIR must be absolute" }
    layout.buildDirectory.set(localBuildDir)
}

android {
    namespace = "com.cyberbanksy.openstream.xr"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.cyberbanksy.openstream.xr"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
    val media3Version = "1.10.1"
    val sceneCoreVersion = "1.0.0-beta01"

    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.xr.scenecore:scenecore:$sceneCoreVersion")

    testImplementation("junit:junit:4.13.2")
}
