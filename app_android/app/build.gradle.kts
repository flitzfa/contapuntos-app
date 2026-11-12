import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.contapuntos.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.contapuntos.app"
        minSdk = 28          // Android 9 o más nuevo
        targetSdk = 36
        versionCode = 5
        versionName = "0.5"
        // celulares reales; deja el .apk más liviano. Para el emulador de la PC: gradlew assembleDebug -Pabis=x86_64
        ndk { abiFilters += (findProperty("abis") as String?)?.split(",") ?: listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            // saca el código que no se usa: el .apk pasa de 66 MB a menos de 30
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "reglas.pro")
            // shortcut: firmada con la clave de prueba de esta PC; para Google Play hace falta una clave propia
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // las librerías del modelo van comprimidas adentro del .apk (pesa menos para mandarlo)
    packaging { jniLibs.useLegacyPackaging = true }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // el modelo se copia de ../modelos al armar la app (ver copiarModelo)
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("modelo"))
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

val copiarModelo by tasks.registering(Copy::class) {
    val onnx = rootDir.resolve("../modelos/detector_puntos.onnx")
    doFirst { check(onnx.exists()) { "Falta $onnx: corré python 5_crear_apk.py desde la carpeta del proyecto" } }
    from(onnx)
    into(layout.buildDirectory.dir("modelo"))
}
tasks.named("preBuild") { dependsOn(copiarModelo) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // cámara en vivo
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
}
