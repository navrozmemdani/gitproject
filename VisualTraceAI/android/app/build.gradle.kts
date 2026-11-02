import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// google-services.json is environment specific and never committed. Without it the app
// runs in guest mode and account features report that sign-in is not configured.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun config(name: String, default: String = ""): String =
    (System.getenv(name) ?: localProps.getProperty(name) ?: project.findProperty(name) as String? ?: default)

fun String.quoted() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Release signing: values come from keystore.properties (git-ignored) or CI environment
// variables. Private keys are never stored in the repository.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signing(name: String): String? = System.getenv(name) ?: keystoreProps.getProperty(name)

android {
    namespace = "com.visualtrace.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.visualtrace.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = config("VT_VERSION_CODE", "1").toInt()
        versionName = "1.0.0"
        testInstrumentationRunner = "com.visualtrace.ai.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "CLIENT_API_KEY", config("VT_CLIENT_API_KEY").quoted())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", config("VT_GOOGLE_WEB_CLIENT_ID").quoted())
        buildConfigField("String", "SUPPORT_EMAIL", config("VT_SUPPORT_EMAIL", "support@visualtrace.ai").quoted())
    }

    signingConfigs {
        create("release") {
            val storePath = signing("VT_KEYSTORE_FILE")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = signing("VT_KEYSTORE_PASSWORD")
                keyAlias = signing("VT_KEY_ALIAS")
                keyPassword = signing("VT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "API_BASE_URL", config("VT_API_BASE_URL_DEBUG", "http://10.0.2.2:8000/").quoted())
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", config("VT_API_BASE_URL", "https://api.visualtrace.ai/").quoted())
            val release = signingConfigs.getByName("release")
            if (release.storeFile != null) signingConfig = release
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
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    lint {
        disable += setOf("MissingTranslation")
        abortOnError = true
        checkReleaseBuilds = true
        textReport = true
        textOutput = file("stdout")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":features"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
