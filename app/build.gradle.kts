plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("com.google.gms.google-services")

    id("kotlin-kapt")
}

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
    buildFeatures.viewBinding=true
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
