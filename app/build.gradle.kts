import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun setting(name: String): String = (findProperty("cerimoniarios.$name") as String?)?.trim().orEmpty()

fun quoted(value: String) = "\"${value.replace("\"", "\\\"")}\""

// Google's public test IDs: debug builds never request real ads.
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.cerimoniarios.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cerimoniarios.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "APP_URL", quoted(setting("appUrl")))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(setting("googleWebClientId")))
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", quoted(testBannerId))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = setting("admobAppId").ifEmpty { testAdMobAppId }
            buildConfigField("String", "ADMOB_BANNER_ID", quoted(setting("admobBannerId")))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val checkReleaseSettings by tasks.registering {
    doLast {
        val missing = listOf("appUrl", "googleWebClientId", "admobAppId", "admobBannerId").filter { setting(it).isEmpty() }
        check(missing.isEmpty()) {
            "Fill in gradle.properties before a release build: " + missing.joinToString { "cerimoniarios.$it" }
        }
        check(!keystoreProperties.isEmpty) { "Create keystore.properties (see README.md) before a release build." }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSettings) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.billing)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
}
