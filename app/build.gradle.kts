import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("com.google.gms.google-services")

    id("kotlin-kapt")
}

val secretsProperties = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) {
        secretsFile.inputStream().use(::load)
    }
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(::load)
    }
}

fun configValue(key: String, default: String = ""): String {
    return sequenceOf(
        secretsProperties.getProperty(key),
        localProperties.getProperty(key),
        System.getenv(key),
        default
    ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}

fun quoted(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

val mapsApiKey = configValue("MAPS_API_KEY")
val appwriteEndpoint = configValue("APPWRITE_ENDPOINT", "https://fra.cloud.appwrite.io/v1")
val appwriteProjectId = configValue("APPWRITE_PROJECT_ID")
val appwriteBucketId = configValue("APPWRITE_BUCKET_ID")
val geminiApiKey = configValue("GEMINI_API_KEY")
val geminiModel = configValue("GEMINI_MODEL", "gemini-2.5-flash")
val sightengineApiUser = configValue("SIGHTENGINE_API_USER")
val sightengineApiSecret = configValue("SIGHTENGINE_API_SECRET")

android {
    namespace = "com.example.urban"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.urban"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue("string", "google_maps_key", quoted(mapsApiKey))
        buildConfigField("String", "APPWRITE_ENDPOINT", quoted(appwriteEndpoint))
        buildConfigField("String", "APPWRITE_PROJECT_ID", quoted(appwriteProjectId))
        buildConfigField("String", "APPWRITE_BUCKET_ID", quoted(appwriteBucketId))
        buildConfigField("String", "GEMINI_API_KEY", quoted(geminiApiKey))
        buildConfigField("String", "GEMINI_MODEL", quoted(geminiModel))
        buildConfigField("String", "SIGHTENGINE_API_USER", quoted(sightengineApiUser))
        buildConfigField("String", "SIGHTENGINE_API_SECRET", quoted(sightengineApiSecret))
    }
    packaging {
        resources {

            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"

            pickFirsts += "META-INF/INDEX.LIST"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //    room database
    implementation("androidx.room:room-runtime:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    //    Data store
    implementation("androidx.datastore:datastore-preferences:1.1.0")
    implementation("androidx.datastore:datastore-preferences-core:1.1.0")
    implementation("androidx.datastore:datastore-core:1.1.0")

    // intuit
    implementation("com.intuit.ssp:ssp-android:1.0.5")
    implementation("com.intuit.sdp:sdp-android:1.0.5")

    // Import the Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-analytics")

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")

    // Firebase real-time database
    implementation("com.google.firebase:firebase-database-ktx:20.3.0")

    //    FCM
    implementation("com.google.firebase:firebase-messaging:23.4.1")


    // NETWORKING + GOOGLE LOGIN TOOLKIT
    //google-auth-library-oauth2-http :- Handles authentication
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")

    //google-api-client :- general client library to talk to Google APIs easily
    implementation("com.google.api-client:google-api-client:2.3.0")

    //okhttp :- HTTP networking library.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    //Appwrite
    implementation("io.appwrite:sdk-for-android:12.0.0")

    //Glid:-  Image Loader Library from url
    implementation ("com.github.bumptech.glide:glide:4.16.0")
//    kapt("com.github.bumptech.glide:compiler:4.16.0")

    //MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    //Shimmer Loading
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    //Lottie loading animation
    implementation("com.airbnb.android:lottie:6.4.1")

    //CircleImage
    implementation("de.hdodenhof:circleimageview:3.1.0")

    //Google Maps + heat-map utilities
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")




}
