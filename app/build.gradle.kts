import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "de.hippokratius.myfeed"
    compileSdk = 35

    defaultConfig {
        // Bleibt trotz Umbenennung in "MyFeed" bei der alten ID: Eine geänderte
        // applicationId wäre für Android eine neue App – Updates über bestehende
        // Installationen würden abgelehnt, Feeds und Einstellungen gingen verloren.
        applicationId = "de.hippokratius.kvaesitsorss"
        minSdk = 26
        targetSdk = 35
        // Die CI setzt Version automatisch per -PversionCode/-PversionName
        // (CalVer, siehe .github/workflows/release.yml); die Defaults gelten
        // nur für lokale Builds.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"
    }

    // Fester Debug-Keystore, damit CI-Builds über Installationen hinweg
    // dieselbe Signatur tragen (sonst lehnt Android Updates ab).
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // Gleicher fester Keystore wie debug: Die Release-APK aus der CI
            // muss über bestehende (debug-signierte) Installationen updatebar
            // sein – ein anderer Key würde von Android abgelehnt.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Von der Nextcloud-SSO-Bibliothek verlangt (java.time & Co. auf minSdk 26).
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Nextcloud-Backend: Konto + Netzwerk über die Files-App (SSO), JSON in :core.
    implementation(libs.nextcloud.sso)
    implementation(libs.kotlinx.serialization.json)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
