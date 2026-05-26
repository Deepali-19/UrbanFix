package com.example.urban

// Small wrapper for values coming from BuildConfig.
object AppConfig {

    val mapsApiKey: String
        get() = BuildConfig.MAPS_API_KEY

    val rootApprovalEmail: String
        get() = BuildConfig.ROOT_APPROVAL_EMAIL

    val fcmServerKey: String
        get() = BuildConfig.FCM_SERVER_KEY

    val smtpEmail: String
        get() = BuildConfig.SMTP_EMAIL

    val smtpAppPassword: String
        get() = BuildConfig.SMTP_APP_PASSWORD

    val approvalActionBaseUrl: String
        get() = BuildConfig.APPROVAL_ACTION_BASE_URL

    val appwriteEndpoint: String
        get() = BuildConfig.APPWRITE_ENDPOINT

    val appwriteProjectId: String
        get() = BuildConfig.APPWRITE_PROJECT_ID

    val appwriteBucketId: String
        get() = BuildConfig.APPWRITE_BUCKET_ID

    val geminiApiKey: String
        get() = BuildConfig.GEMINI_API_KEY

    val geminiModel: String
        get() = BuildConfig.GEMINI_MODEL

    val sightengineApiUser: String
        get() = BuildConfig.SIGHTENGINE_API_USER

    val sightengineApiSecret: String
        get() = BuildConfig.SIGHTENGINE_API_SECRET
}
