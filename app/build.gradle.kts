// Imported rather than fully qualified: inside `android { }` the name `java` resolves to
// Gradle's java extension, so `java.util.Properties` doesn't compile there.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.smsmirror"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smsmirror"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing credentials, kept out of the repo. Written by ./dev.sh keystore
    // release; absent on a fresh clone, in which case assembleRelease still runs and
    // produces an unsigned APK rather than failing.
    val releaseKeystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }

    signingConfigs {
        getByName("debug") {
            // A project-local debug key, shared by the Docker build and Android Studio.
            // Without this the container generates a throwaway key on every run (its
            // ~/.android dies with the --rm container), so each APK is signed
            // differently and `adb install -r` fails with INSTALL_FAILED_UPDATE_
            // INCOMPATIBLE. Created by ./dev.sh setup; falls back to AGP's default if
            // absent, so a fresh clone still builds.
            val local = rootProject.file(".keystore/debug.keystore")
            if (local.exists()) {
                storeFile = local
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseKeystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreProps.getProperty("storeFile"))
                storePassword = releaseKeystoreProps.getProperty("storePassword")
                keyAlias = releaseKeystoreProps.getProperty("keyAlias")
                keyPassword = releaseKeystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Sideloaded personal build; keep it unobfuscated so stack traces stay readable.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystoreProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // BuildConfig.VERSION_NAME is shown at the bottom of Setup, so a bug report can
        // name the build it came from.
        buildConfig = true
    }

    packaging {
        resources {
            // JavaMail ships duplicate service descriptors across its jars.
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
            )
            pickFirsts += setOf(
                "META-INF/mailcap",
                "META-INF/mailcap.default",
                "META-INF/mimetypes.default",
                "META-INF/javamail.default.address.map",
                "META-INF/javamail.default.providers",
                "META-INF/javamail.charset.map",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)

    implementation(libs.javamail.android.mail)
    implementation(libs.javamail.android.activation)

    testImplementation(libs.junit)
}
