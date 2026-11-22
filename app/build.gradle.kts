plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.spectro.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spectro.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 35
        versionName = "3.2.8-beta18"

        // Project URL and sb_publishable key are public client configuration.
        // RLS protects the data; properties/environment can override these
        // defaults whenever the publishable key is rotated.
        val supabaseUrl = providers.gradleProperty("SPECTRO_SUPABASE_URL")
            .orElse(providers.environmentVariable("SPECTRO_SUPABASE_URL"))
            .getOrElse("https://fbfxtyowcxgagseqdlyz.supabase.co")
        val supabaseAnonKey = providers.gradleProperty("SPECTRO_SUPABASE_ANON_KEY")
            .orElse(providers.environmentVariable("SPECTRO_SUPABASE_ANON_KEY"))
            .getOrElse("sb_publishable_7cUKVShHm2xZg96-2pvnOw_79AunZnU")
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseAnonKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }


    signingConfigs {
        create("spectroRelease") {
            val keyPath = System.getenv("SPECTRO_KEYSTORE_PATH")
            val keyPassword = System.getenv("SPECTRO_KEY_PASSWORD")
            if (!keyPath.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                storeFile = file(keyPath)
                storePassword = keyPassword
                keyAlias = "spectro"
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("spectroRelease")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
