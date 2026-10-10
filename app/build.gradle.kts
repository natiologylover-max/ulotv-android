plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val siteUrl = providers.gradleProperty("siteUrl").orElse("https://ethiohub.site.je/").get()

android {
    namespace = "com.ulotv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ulotv.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SITE_URL", "\"$siteUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
