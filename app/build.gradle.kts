plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lquiroz.flab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lquiroz.flab"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    /**
     * Release signing, supplied by the environment rather than committed.
     *
     * A stable signing key is what makes installing through a session-based installer — an app
     * store, or Obtainium pointed at this repo's releases — possible at all. Installing that way
     * exempts F/LAB from Android's Restricted Settings block, which is otherwise the thing standing
     * between a sideloaded build and its accessibility service.
     *
     * The key itself never enters the repository: `.gitignore` already excludes `*.jks` and
     * `*.keystore`, and CI materialises it from a secret. See README for the four values.
     */
    val keystorePath: String? = System.getenv("FLAB_KEYSTORE_PATH")
    val keystorePassword: String? = System.getenv("FLAB_KEYSTORE_PASSWORD")
    val keyAlias: String? = System.getenv("FLAB_KEY_ALIAS")
    val keyPassword: String? = System.getenv("FLAB_KEY_PASSWORD")
    val hasReleaseSigning = !keystorePath.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() &&
        !keyPassword.isNullOrBlank() &&
        file(keystorePath).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Falls back to the debug config when no release key is configured, so a release build
            // is always installable. The fallback is not a substitute: the debug key differs per
            // machine, so an update signed with a different one forces an uninstall. CI warns when
            // it takes this path.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Lint suggests merging mipmap-anydpi-v26 into mipmap-anydpi because minSdk is already 26.
        // Taking that advice makes AAPT fail to resolve the launcher icons at all, so the advice is
        // wrong for this project and the folder stays where the toolchain expects it.
        disable += "ObsoleteSdkInt"
        warningsAsErrors = false
        abortOnError = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.window)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
