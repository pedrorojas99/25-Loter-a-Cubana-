plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.veinticinco.loteriacubana"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.veinticinco.loteriacubana"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"https://pnnujsvixsglydhafzih.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_QneyznGxBERNLMlJ2JHDYQ_0LQFJ8Gt\"")
    }

    flavorDimensions += "audience"
    productFlavors {
        create("usuarios") {
            dimension = "audience"
            applicationIdSuffix = ".usuarios"
            resValue("string", "app_name", "25 Lotería Cubana")
            buildConfigField("boolean", "ADMIN_APP", "false")
        }
        create("admin") {
            dimension = "audience"
            applicationIdSuffix = ".admin"
            resValue("string", "app_name", "25 Lotería Cubana Admin")
            buildConfigField("boolean", "ADMIN_APP", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
