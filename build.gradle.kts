// build.gradle.kts (NIVEL DE PROYECTO)
// ¡Asegúrate de que NO tenga el bloque 'allprojects { repositories { ... } }' !

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.1") // <-- VERIFICA/AJUSTA VERSIÓN
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0") // <-- VERIFICA/AJUSTA VERSIÓN
    }
}

// ¡¡¡ASEGÚRATE DE QUE NO HAYA UN BLOQUE 'allprojects { repositories { ... } }' AQUÍ!!!
// Si lo ves, bórralo.

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}