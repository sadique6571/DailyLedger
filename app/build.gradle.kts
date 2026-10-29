import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val googleWebClientId = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")
val firebaseConfigured = file("google-services.json").isFile
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")

// Signing credentials are injected by CI, never packaged into BuildConfig or resources.
val signingPath = providers.environmentVariable("DAILYLEDGER_SIGNING_KEYSTORE_PATH").orNull
val signingStorePassword = providers.environmentVariable("DAILYLEDGER_SIGNING_STORE_PASSWORD").orNull
val signingAlias = providers.environmentVariable("DAILYLEDGER_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("DAILYLEDGER_SIGNING_KEY_PASSWORD").orNull
val privateSigningConfigured = listOf(signingPath, signingStorePassword, signingAlias, signingKeyPassword)
    .all { !it.isNullOrBlank() }

// Debug-only work remains available without release credentials. Release builds fail closed.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }) {
        check(privateSigningConfigured && file(signingPath!!).isFile) {
            "Release signing is not configured. Supply the DAILYLEDGER_SIGNING_* environment variables."
        }
    }
}

android {
    namespace = "com.sadique.dailyledger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sadique.dailyledger"
        minSdk = 28
        targetSdk = 36
        versionCode = 51
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    signingConfigs {
        if (privateSigningConfigured) {
            create("privateRelease") {
                storeFile = file(signingPath!!)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
            // CI's emulator installs the release over debug to verify upgrade compatibility.
            getByName("debug") {
                storeFile = file(signingPath!!)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Keep the current app certificate while moving its private material out of Git.
            // A different certificate requires the documented rotation and Firebase migration.
            signingConfig = if (privateSigningConfigured) signingConfigs.getByName("privateRelease") else null
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }


    packaging {
        // Compress native libraries in the download; Android extracts only the device ABI on install.
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.fragment:fragment-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation("net.zetetic:sqlcipher-android:4.19.1@aar")
    implementation("androidx.sqlite:sqlite:2.7.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    implementation("androidx.biometric:biometric:1.1.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
