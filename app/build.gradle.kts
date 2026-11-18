import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val yandexGeocoderApiKey = (
    localProperties.getProperty("YANDEX_GEOCODER_API_KEY")
        ?: providers.gradleProperty("YANDEX_GEOCODER_API_KEY").orNull
        ?: providers.environmentVariable("YANDEX_GEOCODER_API_KEY").orNull
        ?: ""
    ).trim()

fun obfuscateSecret(value: String, mask: Int): String {
    if (value.isEmpty()) return ""
    val bytes = value.toByteArray(Charsets.UTF_8)
    val masked = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor mask).toByte() }
    return Base64.getEncoder().encodeToString(masked)
}

val yandexGeocoderKeyMask = 0x5B
val yandexGeocoderKeyEnc = obfuscateSecret(yandexGeocoderApiKey, yandexGeocoderKeyMask)

android {
    namespace = "ru.delivery.photo"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.delivery.photo"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"

        // Ключ не хранится в исходниках открытым текстом: берётся из local.properties/env
        // и вшивается в приложение в обфусцированном виде (см. ReverseGeocoder).
        buildConfigField("String", "YANDEX_GEOCODER_KEY_ENC", "\"$yandexGeocoderKeyEnc\"")
        buildConfigField("int", "YANDEX_GEOCODER_KEY_MASK", "$yandexGeocoderKeyMask")
    }

    /**
     * Два варианта сборки по архитектуре процессора.
     *
     * Телефоны — ARM, эмулятор — всегда x86_64. Разные варианты нужны ещё и
     * потому, что Gradle очищает каталог выхода при пересборке: держать
     * оба APK в одной папке нельзя, они затрут друг друга.
     */
    flavorDimensions += "abi"
    productFlavors {
        create("arm") {
            dimension = "abi"
            // Только реальные архитектуры устройств. x86 и x86_64 добавили бы
            // ~34 МБ мёртвого веса, а на телефонах не используются.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        create("x86") {
            dimension = "abi"
            // Эмулятор. Отдельный applicationId — чтобы вариант для эмулятора
            // можно было поставить рядом с обычным, не вытесняя его.
            applicationIdSuffix = ".x86"
            ndk { abiFilters += listOf("x86_64") }
        }
    }

    // Ресурсы приложения — русский и английский.
    androidResources {
        localeFilters += listOf("ru", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Отладочная подпись, чтобы APK можно было ставить на устройства без MDM.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

/**
 * Тесты запускаются в отдельной JVM, которой нужно читать классы из каталога
 * сборки. Путь проекта содержит кириллицу, и при кодировке по умолчанию
 * (cp1251) тестовая JVM не находит скомпилированный класс.
 */
tasks.withType<Test>().configureEach {
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-Duser.language=en",
        "-Duser.country=US",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
