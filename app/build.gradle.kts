plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.cove.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.cove.companion"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${providers.gradleProperty("supabaseUrl").getOrElse("")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${providers.gradleProperty("supabaseAnonKey").getOrElse("")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("googleWebClientId").getOrElse("")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)

    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.glance.appwidget)
    implementation(libs.datastore)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    implementation(libs.mlkit.genai.prompt)
    implementation(libs.mlkit.genai.speech)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content)
    implementation(libs.ktor.serialization)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.coroutines.test)
}
