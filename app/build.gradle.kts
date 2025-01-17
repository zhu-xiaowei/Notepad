import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.squareup.sqldelight")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}

android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    namespace = "com.farmerbb.notepad"

    defaultConfig {
        applicationId = "com.farmerbb.notepad"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        versionCode = libs.versions.notepad.versionCode.get().toInt()
        versionName = libs.versions.notepad.versionName.get()

        resourceConfigurations.addAll(
            listOf(
                "en", "fr", "ko", "nl", "pl", "zh-rCN", "it", "de", "ru", "cs",
                "pt-rBR", "no", "zh-rTW", "ar", "tr", "el", "bn", "sw", "es", "ja"
            )
        )

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("long", "TIMESTAMP", "${System.currentTimeMillis()}L")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    signingConfigs {
        create("release") {
            if (System.getenv("KSTOREFILE") != null) {
                storeFile = File(System.getenv("KSTOREFILE"))
            }

            storePassword = System.getenv("KSTOREPWD")
            keyAlias = System.getenv("KEYALIAS")
            keyPassword = System.getenv("KEYPWD")
        }
    }

    buildTypes {
        debug {
//            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "@string/app_name_debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles.add(getDefaultProguardFile("proguard-android.txt"))
            proguardFiles.add(File("proguard-rules.pro"))
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appName"] = "@string/app_name"

            applicationVariants.all {
                outputs.map { it as BaseVariantOutputImpl }
                    .forEach { output ->
                        output.outputFileName =
                            "${project.parent?.name}-${defaultConfig.versionName}.apk"
                    }
            }
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.richtext)

    implementation(libs.composePreferences)
    implementation(libs.fsaf)
    implementation(libs.linkifyText)
    implementation(libs.okio)
    implementation(libs.sqldelight)
    implementation(libs.systemuicontroller)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.10"))
    debugImplementation(libs.compose.ui.tooling)
//    implementation("software.aws.solution:clickstream:0.4.1")
}
