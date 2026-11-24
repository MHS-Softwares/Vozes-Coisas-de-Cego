plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mhs.vozescoisasdecego"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mhs.vozescoisasdecego"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += "arm64-v8a" }
    }

    // Mesmo certificado de depuracao que o Zap Falado usa: da para instalar por cima nas proximas versoes.
    signingConfigs {
        create("mhs") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mhs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // As bibliotecas nativas precisam existir como arquivos na pasta do app (o motor abre por caminho).
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
    // O modelo e a voz sao copiados do APK para a pasta do app; nao comprimir acelera a copia.
    androidResources {
        noCompress += listOf("gguf", "safetensors", "json")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
