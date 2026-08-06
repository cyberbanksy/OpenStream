plugins {
    id("com.android.application")
}

val requestedQuestBuildDir = providers.environmentVariable("OPENSTREAM_QUEST_BUILD_DIR").orNull

requestedQuestBuildDir?.let { requestedBuildDir ->
    val localBuildDir = file(requestedBuildDir)
    require(localBuildDir.isAbsolute) { "OPENSTREAM_QUEST_BUILD_DIR must be absolute" }
    layout.buildDirectory.set(localBuildDir)
}

android {
    namespace = "com.cyberbanksy.openstream.quest"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.cyberbanksy.openstream.quest"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared", "-DBUILD_TESTING=OFF")
                targets += "openstream_quest"
            }
        }
    }

    buildFeatures {
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
            requestedQuestBuildDir?.let { buildStagingDirectory(file("$it-cxx")) }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.10.0")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("org.khronos.openxr:openxr_loader_for_android:1.1.62")
}
