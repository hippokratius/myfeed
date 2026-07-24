pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Nextcloud-SingleSignOn wird nur über JitPack veröffentlicht.
        maven("https://jitpack.io")
    }
}

rootProject.name = "myfeed"

include(":core")

// Das :app-Modul benötigt das Android SDK. In Umgebungen ohne SDK (z. B. reine
// JVM-CI oder Sandboxen ohne Zugriff auf dl.google.com) wird nur :core konfiguriert,
// damit die JVM-Tests trotzdem laufen können.
val localProperties = file("local.properties")
val hasSdkDir = localProperties.exists() &&
    localProperties.readLines().any { it.trim().startsWith("sdk.dir") }
val hasAndroidSdk = hasSdkDir ||
    !System.getenv("ANDROID_HOME").isNullOrBlank() ||
    !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()

if (hasAndroidSdk) {
    include(":app")
} else {
    logger.warn("Kein Android SDK gefunden – nur :core wird konfiguriert (ANDROID_HOME setzen, um :app zu bauen).")
}
