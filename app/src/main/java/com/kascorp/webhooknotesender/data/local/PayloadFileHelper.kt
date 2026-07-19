package com.kascorp.webhooknotesender.data.local

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Helper for storing large JSON payloads as files instead of in the database.
 * This avoids SQLiteBlobTooBigException when Base64-encoded media data is too large.
 */
object PayloadFileHelper {

    private const val PAYLOADS_DIR = "queue_payloads"

    /**
     * Save payload JSON string to a file with an auto-generated temporary filename.
     * Use this when saving BEFORE a DB insert (avoids race condition with QueueWorker).
     * Returns the file name (not full path).
     */
    fun savePayload(context: Context, jsonPayload: String): String {
        val dir = File(context.cacheDir, PAYLOADS_DIR)
        dir.mkdirs()
        val fileName = "payload_${UUID.randomUUID()}.json"
        val file = File(dir, fileName)
        file.writeText(jsonPayload)
        return fileName
    }

    /**
     * Read payload JSON string from a file.
     */
    fun loadPayload(context: Context, fileName: String): String? {
        val file = File(File(context.cacheDir, PAYLOADS_DIR), fileName)
        return if (file.exists()) file.readText() else null
    }

    /**
     * Delete a payload file.
     */
    fun deletePayload(context: Context, fileName: String) {
        val file = File(File(context.cacheDir, PAYLOADS_DIR), fileName)
        if (file.exists()) file.delete()
    }

    /**
     * Delete files in the payloads directory whose names are not in [validFileNames].
     * Handles crashes that occur after savePayload() but before the DB insert.
     */
    fun cleanupOrphanedFiles(context: Context, validFileNames: Set<String>) {
        val dir = File(context.cacheDir, PAYLOADS_DIR)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.name !in validFileNames) {
                file.delete()
            }
        }
    }

}
