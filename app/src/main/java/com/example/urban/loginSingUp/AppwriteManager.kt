package com.example.urban.loginSingUp

import android.content.Context
import com.example.urban.BuildConfig
import io.appwrite.Client
import io.appwrite.ID
import io.appwrite.models.InputFile
import io.appwrite.services.Storage
import java.io.File

class AppwriteManager private constructor(context: Context) {

    private val client = Client(context)
        .setEndpoint(BuildConfig.APPWRITE_ENDPOINT)
        .setProject(BuildConfig.APPWRITE_PROJECT_ID)

    private val storage = Storage(client)

    companion object {
        @Volatile
        private var INSTANCE: AppwriteManager? = null

        // Shared Appwrite instance.
        fun getInstance(context: Context): AppwriteManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppwriteManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        // Builds a file view URL.
        fun buildFileViewUrl(fileId: String, bucketId: String): String {
            return "${BuildConfig.APPWRITE_ENDPOINT}/storage/buckets/$bucketId/files/$fileId/view?project=${BuildConfig.APPWRITE_PROJECT_ID}"
        }
    }

    // Uploads an image to Appwrite.
    suspend fun uploadImage(bucketId: String, file: File): io.appwrite.models.File {
        return storage.createFile(
            bucketId = bucketId,
            fileId = ID.unique(),
            file = InputFile.fromFile(file)
        )
    }
}
