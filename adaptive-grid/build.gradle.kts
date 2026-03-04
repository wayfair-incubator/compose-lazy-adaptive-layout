plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.vanniktech.maven.publish") version "0.35.0"
    id("app.cash.paparazzi") version "2.0.0-alpha02"
}

android {
    namespace = "com.lazyadaptivelayout"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    // Ensure Kotlin uses JDK 21 toolchain for compilation (Gradle can auto-provision)
    kotlin {
        jvmToolchain(21)
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Vanniktech Maven Publish Plugin Configuration
mavenPublishing {
    coordinates("com.wayfair", "lazy-adaptive-grid", "1.0.0")
 
    // Publish to Maven Central (Central Portal)
    publishToMavenCentral(automaticRelease = true, validateDeployment = false)

    // Sign all publications (required by Central)
    signAllPublications()
 
    pom {
        name.set("LazyAdaptiveLayout")
        description.set("A high-performance, customizable adaptive grid layout for Android Jetpack Compose")
        url.set("https://github.com/wayfair-incubator/compose-lazy-adaptive-layout")
        
        scm {
            connection.set("scm:git:git://github.com/wayfair-incubator/compose-lazy-adaptive-layout.git")
            developerConnection.set("scm:git:ssh://github.com/wayfair-incubator/compose-lazy-adaptive-layout.git")
            url.set("https://github.com/wayfair-incubator/compose-lazy-adaptive-layout")
        }
        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/wayfair-incubator/compose-lazy-adaptive-layout/blob/main/LICENSE"
            }
        }
        developers {
            developer {
                id = "nispatel145"
                name = "Nisarg Patel"
                email = "npatel2@wayfair.com"
            }
            developer {
                id = "agrosner"
                name = "Andrew Grosner"
                email = "agrosner@wayfair.com"
            }
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    
    // Core Compose dependencies
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.runtime.saveable)
    
    // Material3
    implementation(libs.androidx.material3)
    
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Testing
    testImplementation(libs.junit)
    // Coroutines testing for unit tests
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose UI testing on JVM (Robolectric) — no emulator required
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    // Paparazzi for JVM snapshots (no emulator required)
    testImplementation(libs.paparazzi)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
} 

