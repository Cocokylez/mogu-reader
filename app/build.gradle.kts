import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Release signing. The keystore and its password live OUTSIDE the repository
// (see keystore.properties, which is gitignored) because Android refuses to
// install an update whose signing key differs from the installed app: losing
// this key means every user has to uninstall and reinstall. A checkout without
// the properties file still builds -- it just falls back to the debug key and
// produces an APK that cannot update an official install.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKey = keystorePropsFile.exists()

android {
    namespace = "com.tome.reader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tome.reader"
        minSdk = 26
        targetSdk = 34
        versionCode = 23
        versionName = "2.9.0"

        // App icons are vector-based adaptive icons; keep them as vectors at runtime.
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Minification is off by default so the project always runs as-is.
            // proguard-rules.pro contains the keep rules needed if you enable it.
            isMinifyEnabled = false
            // Release matters for feel, not just size: a debuggable build disables
            // Compose runtime optimisations and scrolls visibly worse than the
            // same code released.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            // Scrapers call android.util.Log; without this every live-source check
            // dies on "not mocked" instead of reporting what the site returned.
            isReturnDefaultValues = true
        }
    }

}

dependencies {
    // AndroidX core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (versions managed by the BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)


    // Backdrop blur for the floating glass overlays (nav pill, reader dock).
    implementation(libs.haze)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room (library database) — KSP annotation processor
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (settings)
    implementation(libs.androidx.datastore.preferences)

    // Background diagnostics
    implementation(libs.androidx.work.runtime.ktx)

    // Storage Access Framework helpers
    implementation(libs.androidx.documentfile)

    // Image loading
    implementation(libs.coil.compose)

    // Format parsers
    implementation(libs.junrar) // CBR / RAR
    implementation(libs.jsoup)  // HTML parsing for online manga catalogues
    implementation(libs.okhttp3) // HTTP client for scrapers

    // Protobuf (MANGA Plus API)
    implementation(libs.protobuf.javalite)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Gradle does not forward -D to test JVMs, so the live-source check would silently
// skip. Run it with:  gradlew testDebugUnitTest --tests '*LiveSourceReadTest*' -DliveSources=true
tasks.withType<Test>().configureEach {
    systemProperty("liveSources", System.getProperty("liveSources") ?: "false")
    testLogging { showStandardStreams = true }
}
