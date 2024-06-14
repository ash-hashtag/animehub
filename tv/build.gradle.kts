plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.rashstudios.animehub"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rashstudios.animehub"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }

    }


    signingConfigs {
       create("release"){
           storeFile =  file(findProperty("RELEASE_STORE_FILE") as String)
           storePassword =  findProperty("RELEASE_STORE_PASSWORD") as String
           keyAlias = findProperty("RELEASE_KEY_ALIAS") as String
           keyPassword = findProperty("RELEASE_KEY_PASSWORD") as String

           // Optional, specify signing versions used
           // v1SigningEnabled true
           // v2SigningEnabled true
       }
    }    

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.media3.ui.leanback)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.hls)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("com.github.kittinunf.fuel:fuel:3.0.0-alpha1")

    val nav_version = "2.7.7"

    implementation("androidx.navigation:navigation-compose:$nav_version")

    val leanback_version = "1.2.0-alpha04"

    implementation("androidx.leanback:leanback:$leanback_version")

    // leanback-preference is an add-on that provides a settings UI for TV apps.
    implementation("androidx.leanback:leanback-preference:$leanback_version")

    // leanback-paging is an add-on that simplifies adding paging support to a RecyclerView Adapter.
    implementation("androidx.leanback:leanback-paging:1.1.0-alpha11")

    // leanback-tab is an add-on that provides customized TabLayout to be used as the top navigation bar.
    implementation("androidx.leanback:leanback-tab:1.1.0-beta01")


    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    implementation("io.coil-kt:coil-compose:2.6.0")


    implementation("com.fleeksoft.ksoup:ksoup:0.1.2")
}
