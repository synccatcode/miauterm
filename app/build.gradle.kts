plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.cortex.terminal"
    compileSdk = 35

    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.miauterm.terminal"
        minSdk = 24

        targetSdk = 28
        versionCode = 30005
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    androidResources {

        noCompress += listOf("zst")
    }

    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
    val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
    val resolvedCustomKeystore = if (!releaseStoreFile.isNullOrEmpty()) {
        val candidate = file(releaseStoreFile)
        if (candidate.exists()) candidate else rootProject.file(releaseStoreFile)
    } else {
        null
    }

    val (activeKeystore, activeStorePass, activeAlias, activeKeyPass) = when {
        resolvedCustomKeystore != null &&
            resolvedCustomKeystore.exists() &&
            !releaseStorePassword.isNullOrEmpty() &&
            !releaseKeyAlias.isNullOrEmpty() &&
            !releaseKeyPassword.isNullOrEmpty() -> {
            listOf(resolvedCustomKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        }
        else -> {
            listOf(null, null, null, null)
        }
    }
    val hasReleaseSigning = activeKeystore != null

    val isReleaseTaskRequested = gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("Release", ignoreCase = true) &&
            (taskName.contains("assemble", ignoreCase = true) ||
                taskName.contains("bundle", ignoreCase = true) ||
                taskName.contains("package", ignoreCase = true))
    }
    if (isReleaseTaskRequested && !hasReleaseSigning) {
        throw GradleException(
            "Release build requires the release signing environment variables (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD)."
        )
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning && activeKeystore != null) {
                storeType = "PKCS12"
                storeFile = activeKeystore as java.io.File
                storePassword = activeStorePass as String
                keyAlias = activeAlias as String
                keyPassword = activeKeyPass as String
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true

            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"

                it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        disable.addAll(listOf("ExpiredTargetSdkVersion"))
    }
}

dependencies {
    implementation(project(":core:terminal-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
