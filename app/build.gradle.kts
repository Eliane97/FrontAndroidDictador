// build.gradle.kts (MÓDULO :app)
// ¡Este archivo usa SINTAXIS KOTLIN DSL!

plugins {
    id("com.android.application")
    // Si NO usas Kotlin en absoluto en tu código, puedes COMENTAR o ELIMINAR esta línea:
    id("org.jetbrains.kotlin.android") // <-- Comenta si solo tienes código Java
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Define la compatibilidad de Java a la versión 17
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // En .kts, jvmTarget se asigna con "=" y requiere strings
        jvmTarget = "17"
    }

    packagingOptions {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/README")
            excludes.add("META-INF/*.RSA")
            excludes.add("META-INF/*.SF")
            excludes.add("META-INF/*.DSA")
        }
    }
}

dependencies {
    androidTestImplementation("junit:junit:4.12")
    // 1. Esto DEBE estar aquí para que Apache POI no de error en Android < 8.0
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 2. Apache POI con la exclusión que ya pusiste (Correcto)
    implementation("org.apache.poi:poi-ooxml:5.2.3") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-api")
    }

    // 3. Puente de logs (Tenías la línea repetida, con una basta)
    implementation("org.slf4j:slf4j-android:1.7.36")

    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

}