package com.kascorp.webhooknotesender.util

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Base64Encoder @Inject constructor() {

    private companion object {
        private const val CHUNK_SIZE = 8192 // 8KB chunks
    }

    /**
     * Encodes a file to Base64 string using chunked reading.
     * Uses streaming to avoid loading entire file into memory.
     */
    fun encodeFile(file: File): String {
        return FileInputStream(file).use { inputStream ->
            encodeStream(inputStream)
        }
    }

    /**
     * Encodes a byte array to Base64 string.
     */
    fun encode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /**
     * Encodes an input stream to Base64 string using chunked reading.
     */
    private fun encodeStream(inputStream: InputStream): String {
        val buffer = ByteArray(CHUNK_SIZE)
        val result = StringBuilder()
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val chunk = if (bytesRead < CHUNK_SIZE) {
                buffer.copyOf(bytesRead)
            } else {
                buffer.copyOf()
            }
            result.append(Base64.encodeToString(chunk, Base64.NO_WRAP))
        }

        return result.toString()
    }
}
