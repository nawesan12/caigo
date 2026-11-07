import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Lo que no va al repo (claves, URLs propias) se lee de local.properties.
val locales = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun local(clave: String, porDefecto: String = ""): String = locales.getProperty(clave, porDefecto)
fun cadena(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "ar.com.caigo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ar.com.caigo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        buildConfigField("String", "CLERK_PUBLICABLE", cadena(local("caigo.clerk.publicable")))
        buildConfigField("String", "MAPA_ESTILO", cadena(local("caigo.mapa.estilo", "https://tiles.openfreemap.org/styles/liberty")))
        buildConfigField("String", "MAPA_ESTILO_OSCURO", cadena(local("caigo.mapa.estiloOscuro", "https://tiles.openfreemap.org/styles/dark")))
        buildConfigField("String", "SITIO", cadena(local("caigo.sitio", "https://caigo.com.ar")))
        // Firebase sin google-services.json: si faltan estos datos, el push queda apagado.
        buildConfigField("String", "FIREBASE_APP_ID", cadena(local("caigo.firebase.appId")))
        buildConfigField("String", "FIREBASE_API_KEY", cadena(local("caigo.firebase.apiKey")))
        buildConfigField("String", "FIREBASE_PROYECTO", cadena(local("caigo.firebase.proyecto")))
        buildConfigField("String", "FIREBASE_REMITENTE", cadena(local("caigo.firebase.remitente")))
    }

    // Firma de release: la llave de subida vive fuera del repo (~/Trabajo/vimo/credenciales-android).
    // Sin esos datos en local.properties, el release sale sin firmar.
    signingConfigs {
        if (local("caigo.firma.archivo").isNotEmpty()) {
            create("subida") {
                storeFile = file(local("caigo.firma.archivo"))
                storePassword = local("caigo.firma.clave")
                keyAlias = local("caigo.firma.alias", "caigo")
                keyPassword = local("caigo.firma.clave")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API", cadena(local("caigo.api.debug", "http://10.0.2.2:4300")))
        }
        release {
            buildConfigField("String", "API", cadena(local("caigo.api.release", "https://api.caigo.com.ar")))
            signingConfigs.findByName("subida")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    // APKs por arquitectura para probar en un teléfono sin bajar 110 MB (Play usa el App Bundle,
    // que ya separa solo).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Las capturas de PantallasTest (claro y oscuro) van a build/capturas: el CI las sube para revisarlas.
roborazzi {
    outputDir.set(layout.buildDirectory.dir("capturas"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.face)
    implementation(libs.mlkit.barcode)
    implementation(libs.zxing.core)

    implementation(libs.maplibre)
    implementation(libs.billing)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.browser)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
