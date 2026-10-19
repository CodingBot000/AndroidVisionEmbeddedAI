plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "dev.robotvision"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.robotvision"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    androidResources { noCompress += listOf("tflite", "onnx", "ort") }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        jniLibs.pickFirsts += "**/libc++_shared.so"
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.activity.compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(libs.androidx.lifecycle.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.navigation.compose)
    implementation(libs.androidx.hilt.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.camera.camera2)
    implementation(libs.androidx.camera.camera.lifecycle)
    implementation(libs.androidx.camera.camera.view)
    implementation(libs.com.google.ai.edge.litert.litert)
    implementation(libs.com.google.mediapipe.tasks.vision)
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation(libs.androidx.room.room.runtime)
    implementation(libs.androidx.room.room.ktx)
    ksp(libs.androidx.room.room.compiler)
    implementation(libs.androidx.datastore.datastore.preferences)
    testImplementation(libs.junit.junit)
    testImplementation(libs.io.mockk.mockk)
    testImplementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)
    testImplementation(libs.app.cash.turbine.turbine)
    testImplementation(libs.org.robolectric.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
