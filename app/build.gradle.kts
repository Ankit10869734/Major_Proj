import com.android.build.api.variant.VariantOutput
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.moodmix.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.moodmix.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "v1.0 Release"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keyStoreFilePath = keystoreProperties.getProperty("KEYSTORE_FILE")
            val storePass = keystoreProperties.getProperty("KEYSTORE_PASSWORD")
            val alias = keystoreProperties.getProperty("KEY_ALIAS")
            val keyPass = keystoreProperties.getProperty("KEY_PASSWORD")

            val fileExists = keyStoreFilePath != null && rootProject.file(keyStoreFilePath).exists()
            val credentialsValid = !storePass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()

            if (fileExists && credentialsValid) {
                storeFile = rootProject.file(keyStoreFilePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            } else {
                val missingDetails = mutableListOf<String>()
                if (!keystorePropertiesFile.exists()) {
                    missingDetails.add("keystore.properties file not found in root project")
                } else {
                    if (!fileExists) missingDetails.add("Keystore file '$keyStoreFilePath' not found")
                    if (storePass.isNullOrBlank()) missingDetails.add("KEYSTORE_PASSWORD is empty")
                    if (alias.isNullOrBlank()) missingDetails.add("KEY_ALIAS is empty")
                    if (keyPass.isNullOrBlank()) missingDetails.add("KEY_PASSWORD is empty")
                }
                val errorMessage = "CRITICAL ERROR: Release build signing configuration is incomplete (${missingDetails.joinToString("; ")}). " +
                        "Please create 'keystore.properties' in the root project directory with KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD properties."

                if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
                    throw InvalidUserDataException(errorMessage)
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            ndk {
                abiFilters.clear()
                abiFilters.add("arm64-v8a")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        if (variant.buildType == "release") {
            variant.outputs.forEach { output ->
                if (output is VariantOutput) {
                    output.outputFileName.set("MoodMix-v1.0-release.apk")
                }
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette.ktx)
    implementation("androidx.core:core-splashscreen:1.2.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
