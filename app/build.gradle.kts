import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun getKeystoreProperties(): Properties? {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        return keystoreProperties
    }
    return null
}

// Resolve the current git commit short hash (7 chars).
// Returns null when:
//   * git is not on PATH
//   * the project is not a git repo (e.g. a tarball build)
//   * the HEAD has no commit yet
// The CI workflow forwards a GIT_SHORT_SHA env var so this still works in
// shallow clones where `git rev-parse` may resolve a different SHA.
fun resolveGitShortHash(): String? {
    System.getenv("GIT_SHORT_SHA")?.takeIf { it.isNotBlank() }?.let { return it.take(7) }
    return runCatching {
        val process = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && output.isNotEmpty()) output else null
    }.getOrNull()
}

// Resolved once at configuration time so every variant reuses the same value
// (avoids forking `git` per variant) and gives a stable fallback when git is
// not available.
val gitShortHash: String = resolveGitShortHash()?.takeIf { it.isNotBlank() } ?: "nogit"

android {
    namespace = "com.luoshui.paycardeditor"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.luoshui.paycardeditor"
        minSdk = 35
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // The module is an Xposed hook bundle for Mi Wallet, which never
            // runs on ChromeOS, so the single-ABI restriction is intentional.
            //noinspection ChromeOsAbiSupport
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        val keystoreProperties = getKeystoreProperties()
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Only attach the release signing config when the developer (or CI)
            // has actually provided a keystore.properties; otherwise leave the
            // build unsigned so `assembleRelease` does not fail on PR jobs.
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // AGP 9 R8: enabling `optimization` implicitly turns on minify
            // (shrink + obfuscate + optimize). Verified via `:app:minifyReleaseWithR8`
            // running on assembleRelease and apkanalyzer showing obfuscated symbols.
            optimization.enable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            merges += "META-INF/xposed/*"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val apkName =
                "${rootProject.name}-${android.defaultConfig.versionName}-$gitShortHash-${variant.buildType}.apk"
            // `outputFileName` is part of the stable `VariantOutput` API (since
            // AGP 7.0) — no need to cast to `VariantOutputImpl` from the
            // `com.android.build.api.variant.impl` internal package.
            output.outputFileName = apkName
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.ucrop)
    // Override the OkHttp 3.12.13 brought in transitively by uCrop with a
    // maintained 5.x release. R8 tree-shakes the unused parts so the on-disk
    // cost is negligible (verified: 195 bytes of okhttp3 in dex).
    implementation(libs.okhttp)
    implementation(libs.dexkit)

    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.material.kolor)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // DataStore and coroutines
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Navigation Compose and Coil
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
