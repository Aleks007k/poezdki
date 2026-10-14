plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

// Номер сборки GitHub растёт с каждой сборкой — поэтому каждая новая версия
// ставится поверх старой. Локально (без GitHub) — 1.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "io.github.aleks007k.poezdki"
    compileSdk = 35

    defaultConfig {
        // Идентификатор приложения. НЕ МЕНЯТЬ: иначе это будет другое приложение,
        // и поездки не перейдут.
        applicationId = "io.github.aleks007k.poezdki"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Подпись релиза.
    // Постоянный ключ лежит в signing/release.p12 и зашифрован паролем, который
    // знает только владелец (секрет GitHub SIGNING_PASSWORD). Без ключа релиз
    // подписывается отладочным ключом — такие сборки CI не выкладывает.
    val keystoreFile = rootProject.file("signing/release.p12")
    val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
    val hasRealSigning = keystoreFile.exists() && !signingPassword.isNullOrBlank()

    signingConfigs {
        if (hasRealSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingPassword
                keyAlias = "poezdki"
                keyPassword = signingPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasRealSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Снимки экранов (ScreenshotsTest) — только по запросу: ./gradlew recordPaparazziDebug -Pshots
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("shots")) exclude("**/ScreenshotsTest*")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.play.services.location)
    implementation(libs.androidx.fragment)

    testImplementation(libs.junit)
}
