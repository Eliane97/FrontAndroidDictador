// settings.gradle.kts
// ¡Este archivo es CRÍTICO! Debe ser EXACTAMENTE como se muestra aquí.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Esta línea le dice a Gradle que los repositorios SÓLO deben estar aquí.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()        // Repositorio de Google
        mavenCentral()  // Repositorio Central de Maven
        // ¡¡ESTA LÍNEA ES FUNDAMENTAL PARA JITPACK.IO Y PDFBox-Android!!
        maven { url = uri("https://jitpack.io") } // <-- ¡DEBE SER EXACTAMENTE ASÍ!
    }
}

// Las siguientes líneas son el nombre de tu proyecto y sus módulos. No los cambies.
rootProject.name = "MyApplication"
include(":app")

