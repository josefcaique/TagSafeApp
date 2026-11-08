plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.digitalsix.YouSafe"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        versionCode = 32
        versionName = "11"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    flavorDimensions += listOf("app", "environment")

    productFlavors {
        // --- Dimensão de APP ---
        create("tagsafe") {
            dimension = "app"
            applicationId = "com.digitalsix.YouSafe"
            manifestPlaceholders["appLabel"] = "TagSafe"
        }

        create("tagsafedemo") {
            dimension = "app"
            applicationId = "com.digitalsix.TagsafeDemo"
            manifestPlaceholders["appLabel"] = "TagSafe Demo"
        }

        // --- Dimensão de AMBIENTE ---
        create("staging") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://dev.api.tagsafeapplication.com/\"")
            manifestPlaceholders["appLabelSuffix"] = " Staging"
        }

        create("production") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://api.tagsafeapplication.com/\"")
            manifestPlaceholders["appLabelSuffix"] = ""
        }

        create("demo") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://demo.api.tagsafeapplication.com/\"")
            manifestPlaceholders["appLabelSuffix"] = ""
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-mlkit-vision:1.4.2")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
