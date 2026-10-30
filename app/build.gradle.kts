import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ravi.omnichat"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.ravi.omnichat"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.9"
    }

    // Release signing. Credentials come only from a properties file that is never committed:
    //   - the path in env OMNICHAT_KEYSTORE_PROPERTIES (CI secret / local), or
    //   - keystore.properties in the project root (git-ignored).
    // Keys: storeFile, storePassword, keyAlias, keyPassword. With neither present, release builds FAIL
    // (they never fall back to the debug key). Debug builds need nothing.
    val ksFile = (System.getenv("OMNICHAT_KEYSTORE_PROPERTIES")?.let { File(it) } ?: rootProject.file("keystore.properties")).takeIf { it.exists() }
    val ks = Properties().apply { ksFile?.inputStream()?.use { load(it) } }
    signingConfigs {
        create("release") {
            if (ksFile != null) {
                storeFile = file(ks.getProperty("storeFile"))
                storePassword = ks.getProperty("storePassword")
                keyAlias = ks.getProperty("keyAlias")
                keyPassword = ks.getProperty("keyPassword")
            }
        }
    }

    // The default ("standard") flavour ships with cleartext off (see res/xml/network_security_config.xml).
    // The "lan" flavour exists only for developing against a LAN Ollama/LM Studio over HTTP:
    // it swaps in network_security_config_lan.xml, and its release variant is disabled below.
    flavorDimensions += "net"
    productFlavors {
        create("standard") { dimension = "net" }
        create("lan") { dimension = "net" }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (ksFile != null) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.withType<Test>().configureEach { maxHeapSize = "2g" } // 1g left the Robolectric launch test GC-thrashing into OutOfMemoryError (28 min crawl, CI run 18)

// ---- Release signing guard: fail instead of producing a debug-signed or unsigned "release".
val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails if the release signing key is missing or is an Android debug certificate."
    doLast {
        val path = System.getenv("OMNICHAT_KEYSTORE_PROPERTIES")?.let { File(it) } ?: rootProject.file("keystore.properties")
        if (!path.exists()) throw GradleException(
            "Release signing is not configured. Set OMNICHAT_KEYSTORE_PROPERTIES to a keystore.properties file " +
                "(storeFile, storePassword, keyAlias, keyPassword). Release builds never use the debug key."
        )
        val p = Properties().apply { path.inputStream().use { load(it) } }
        val store = File(p.getProperty("storeFile") ?: throw GradleException("storeFile missing in $path"))
        if (!store.exists()) throw GradleException("Keystore file not found: $store")
        val alias = p.getProperty("keyAlias") ?: throw GradleException("keyAlias missing")
        if (alias.equals("androiddebugkey", true) || store.name.equals("debug.keystore", true))
            throw GradleException("Refusing to sign a release with the Android debug key")
        val pass = (p.getProperty("storePassword") ?: "").toCharArray()
        val keyStore = listOf("PKCS12", "JKS").firstNotNullOfOrNull { type ->
            runCatching { KeyStore.getInstance(type).apply { store.inputStream().use { load(it, pass) } } }.getOrNull()
        } ?: throw GradleException("Could not open the release keystore (wrong password or format)")
        val cert = keyStore.getCertificate(alias) as? X509Certificate
            ?: throw GradleException("Alias '$alias' not found in keystore")
        if (cert.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true))
            throw GradleException("Refusing to sign a release with an Android Debug certificate")
    }
}
tasks.matching { it.name == "packageRelease" || it.name == "assembleRelease" || it.name == "bundleRelease" }
    .configureEach { dependsOn(verifyReleaseSigning) }

// The lan flavour is debug-only: never produce a releasable lan build.
androidComponents {
    beforeVariants(selector().withBuildType("release").withFlavor("net" to "lan")) { variant ->
        variant.enabled = false
    }
}
