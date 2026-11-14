import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Optional build-time service config read from the gitignored local.properties.
// A non-blank value is compiled into BuildConfig and takes precedence over the
// in-app setting; leave it unset to keep configuring inside the app.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProperty(key: String): String = localProperties.getProperty(key).orEmpty().trim()

/**
 * Wrap the value in `new String("...")` so the generated BuildConfig field is
 * NOT a compile-time constant. Kotlin/Java inline `static final String`
 * constants into referencing code at compile time, and incremental builds do
 * not recompile unchanged Kotlin when only the constant changes — which made
 * local.properties edits appear to have no effect until a clean build. A
 * non-constant field is read via getstatic at runtime, so every rebuild picks
 * up the current local.properties value without needing a clean.
 */
fun String.toBuildConfigStringLiteral(): String =
    "new String(\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\")"

android {
    namespace = "com.avengeeker.floatingtype"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.avengeeker.floatingtype"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Preconfigured via local.properties (see README "本地构建配置");
        // empty string = not configured, fall back to in-app settings.
        buildConfigField("String", "LOCAL_ASR_BASE_URL", localProperty("asr.baseUrl").toBuildConfigStringLiteral())
        buildConfigField("String", "LOCAL_ASR_API_KEY", localProperty("asr.apiKey").toBuildConfigStringLiteral())
        buildConfigField("String", "LOCAL_ASR_MODEL", localProperty("asr.model").toBuildConfigStringLiteral())
        buildConfigField("String", "LOCAL_POLISH_BASE_URL", localProperty("polish.baseUrl").toBuildConfigStringLiteral())
        buildConfigField("String", "LOCAL_POLISH_API_KEY", localProperty("polish.apiKey").toBuildConfigStringLiteral())
        buildConfigField("String", "LOCAL_POLISH_MODEL", localProperty("polish.model").toBuildConfigStringLiteral())

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
