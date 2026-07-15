plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dexcontrol.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dexcontrol.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 8
        versionName = "1.4.2"
    }

    // Chave de assinatura fixa: garante que todas as versões tenham a mesma
    // assinatura, permitindo atualizar o app sem desinstalar.
    signingConfigs {
        create("dexcontrol") {
            storeFile = file("dexcontrol.p12")
            storePassword = "fa0274f0ef8186aedcd9f72071bcbf61cba6f29e636fad94"
            keyAlias = "dexcontrol"
            keyPassword = "fa0274f0ef8186aedcd9f72071bcbf61cba6f29e636fad94"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dexcontrol")
        }
        debug {
            signingConfig = signingConfigs.getByName("dexcontrol")
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Shizuku: injeção de eventos com privilégio de shell (sem root),
    // necessária para controlar a tela do DeX de verdade.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Acesso a APIs ocultas (setDisplayId, IInputManager) no Android 9+.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
