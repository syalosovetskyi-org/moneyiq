import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry)
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(FileInputStream(f))
}

android {
    namespace = "org.syalosovetskyi.onemoney"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.syalosovetskyi.onemoney"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "1.2.15"
        testInstrumentationRunner = "org.syalosovetskyi.onemoney.HiltTestRunner"
        multiDexKeepProguard = file("multidex-keep.pro")
    }

    // Release signing: reads from env vars set by GitHub Actions (SIGNING_*)
    // or from local.properties (signing.storeFile, etc.) for local builds.
    val storeFilePath: String? = System.getenv("SIGNING_STORE_FILE")
        ?: localProps.getProperty("signing.storeFile")
    val storePass: String? = System.getenv("SIGNING_STORE_PASSWORD")
        ?: localProps.getProperty("signing.storePassword")
    val keyAliasVal: String? = System.getenv("SIGNING_KEY_ALIAS")
        ?: localProps.getProperty("signing.keyAlias")
    val keyPass: String? = System.getenv("SIGNING_KEY_PASSWORD")
        ?: localProps.getProperty("signing.keyPassword")

    val hasReleaseSigning = listOf(storeFilePath, storePass, keyAliasVal, keyPass)
        .all { !it.isNullOrBlank() }
    val requestedReleaseBuild = gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("Release", ignoreCase = true)
    }

    if (requestedReleaseBuild && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is required. Set SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, " +
                "SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD, or matching signing.* values in local.properties."
        )
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile     = file(storeFilePath!!)
                storePassword = storePass
                keyAlias      = keyAliasVal
                keyPassword   = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning)
                signingConfigs.getByName("release") else null
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

    val debugMonoflowUrl   = localProps.getProperty("monoflow.url",   "")
    val debugMonoflowToken = localProps.getProperty("monoflow.token", "")
    // Приёмник падений. В исходнике DSN не живёт: он задаётся в local.properties
    // (файл не под git) или переменной окружения GLITCHTIP_DSN в CI. Пусто —
    // отправка падений просто выключена, приложение работает как обычно.
    val glitchtipDsn = (System.getenv("GLITCHTIP_DSN")
        ?: localProps.getProperty("glitchtip.dsn", "")).trim()

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            buildConfigField("String", "DEBUG_MONOFLOW_URL",   "\"$debugMonoflowUrl\"")
            buildConfigField("String", "DEBUG_MONOFLOW_TOKEN", "\"$debugMonoflowToken\"")
            buildConfigField("String", "GLITCHTIP_DSN",        "\"$glitchtipDsn\"")
        }
        release {
            buildConfigField("String", "DEBUG_MONOFLOW_URL",   "\"\"")
            buildConfigField("String", "DEBUG_MONOFLOW_TOKEN", "\"\"")
            buildConfigField("String", "GLITCHTIP_DSN",        "\"$glitchtipDsn\"")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Workaround for IncompatibleClassChangeError in NonNullableMutableLiveDataDetector
        // caused by version incompatibility between androidx.lifecycle lint and Kotlin Analysis API
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.text.google.fonts)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    kspAndroidTest(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // Charts
    implementation(libs.mpandroidchart)

    // DataStore
    implementation(libs.datastore.preferences)

    // Glance (виджеты рабочего стола)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // WorkManager (обновление виджетов)
    implementation(libs.work.runtime.ktx)

    // Biometric
    implementation(libs.biometric)

    // Sentry
    implementation(libs.sentry.android)

    // Testing — unit
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation("androidx.work:work-testing:2.10.0")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit.ktx)

    // Testing — instrumented
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
}

// Плагин Sentry на сборке НИЧЕГО НАРУЖУ НЕ ОТДАЁТ. Раньше он заливал в публичный
// sentry.io (org serg-yalosovetsky, проект one_money) mapping-файл и, при заданном
// токене, includeSourceContext — то есть ИСХОДНИКИ приложения. Приёмник падений
// переехал на свой glitchtip.ibotz.fun (ADR-075), и загрузка символов туда не
// настроена, поэтому она просто выключена: цена — обфусцированные стектрейсы
// релизной сборки. Включать обратно только на СВОЙ приёмник (serg/tasks#679).
sentry {
    includeSourceContext = false
    autoUploadProguardMapping = false
    uploadNativeSymbols = false
    telemetry = false

    // Sentry 4.14.1 ASM bytecode transform (transformDebugClassesWithAsm) drops some
    // worker classes (DriveBackupWorker/MonoFlowSyncWorker/NotificationWorker) from the
    // dex on a clean build → NoClassDefFoundError: DriveBackupEntryPoint on cold start.
    // We don't use auto perf-tracing, so disable the transform. Error reporting via the
    // Sentry SDK (SentryAndroid.init in MoneyIQApp) is unaffected.
    tracingInstrumentation {
        enabled.set(false)
    }
}

tasks.register("printVersionName") {
    doLast { println(android.defaultConfig.versionName) }
}
