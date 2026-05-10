import java.util.Properties
import java.io.File
import java.io.FileInputStream
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.impl.VariantOutputImpl
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
}

fun getKeystoreProperties(): Properties? {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        return keystoreProperties;
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

val gitShortHash: String? = resolveGitShortHash()

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
        versionCode = 4
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        val keystoreProperties = getKeystoreProperties();
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
            optimization.enable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach {
            val apkName =
                "${rootProject.name}-${android.defaultConfig.versionName}-${resolveGitShortHash()}-${variant.buildType}.apk"

            (it as VariantOutputImpl).outputFileName = apkName
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.ucrop)
    implementation(libs.okhttp)
    implementation(libs.glide)
    implementation(libs.dexkit)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
