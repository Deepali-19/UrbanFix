package com.example.urban.loginSingUp

import android.content.Context
import io.appwrite.Client
import io.appwrite.ID
import io.appwrite.services.Storage
import io.appwrite.models.InputFile
import java.io.File

class AppwriteManager private constructor(context: Context) {

    private val client = Client(context)
        .setEndpoint("https://fra.cloud.appwrite.io/v1")
        .setProject("699971230022a191cce2")

    private val storage = Storage(client)

    companion object {
        @Volatile
        private var INSTANCE: AppwriteManager? = null

        fun getInstance(context: Context): AppwriteManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppwriteManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    suspend fun uploadImage(bucketId: String, file: File): io.appwrite.models.File {
        return storage.createFile(
            bucketId = bucketId,
            fileId = ID.unique(),
            file = InputFile.fromFile(file)
        )
    }
}