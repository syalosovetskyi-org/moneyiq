import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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

    // Приёмник падений. В исходнике DSN не живёт: он задаётся в local.properties
    // (файл не под git) или переменной окружения GLITCHTIP_DSN в CI. Пусто —
    // отправка падений просто выключена, приложение работает как обычно.
    val glitchtipDsn = (System.getenv("GLITCHTIP_DSN")
        ?: localProps.getProperty("glitchtip.dsn", "")).trim()

    flavorDimensions += "distribution"
    productFlavors {
        create("personal") {
            dimension = "distribution"
            buildConfigField("boolean", "MONOFLOW_ENABLED", "true")
            val pins = (System.getenv("ALISAPHONE_CERT_SHA256") ?: localProps.getProperty("alisaphone.certSha256", "")).trim()
            buildConfigField("String", "ALISAPHONE_CERT_SHA256", "\"${pins.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            buildConfigField("String", "DEBUG_MONOFLOW_URL", "\"\"")
            buildConfigField("String", "DEBUG_MONOFLOW_TOKEN", "\"\"")
            buildConfigField("String", "GLITCHTIP_DSN", "\"${glitchtipDsn.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        create("play") {
            dimension = "distribution"
            applicationId = "ua.syalosovetskyi.zerno"
            buildConfigField("boolean", "MONOFLOW_ENABLED", "false")
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
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
    "personalImplementation"(libs.sentry.android)
    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.crashlytics)

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

// Personal builds deliberately have no Firebase configuration.
tasks.configureEach {
    if ((name.startsWith("processPersonal") && name.endsWith("GoogleServices")) ||
        (name.contains("Personal") && name.contains("Crashlytics"))) {
        enabled = false
    }
}

tasks.register("printVersionName") {
    doLast { println(android.defaultConfig.versionName) }
}

// Flavor-owned fields: Play has no MonoFlow credentials; release never embeds local debug tokens.
androidComponents {
    onVariants(selector().withFlavor("distribution" to "personal").withBuildType("debug")) { variant ->
        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\""
        variant.buildConfigFields.put("DEBUG_MONOFLOW_URL", com.android.build.api.variant.BuildConfigField(
            "String", quoted(localProps.getProperty("monoflow.url", "")), "Personal debug URL"))
        variant.buildConfigFields.put("DEBUG_MONOFLOW_TOKEN", com.android.build.api.variant.BuildConfigField(
            "String", quoted(localProps.getProperty("monoflow.token", "")), "Personal debug token"))
    }
}
