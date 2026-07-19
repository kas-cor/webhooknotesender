package com.kascorp.webhooknotesender.util

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Base64 as JdkBase64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Base64Encoder @Inject constructor() {

    private companion object {
        /** Buffer size for streaming reads. */
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Encodes a file to Base64 string using streaming.
     * Avoids loading the entire file into memory at once.
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
     * Encodes an input stream to Base64 string using a true streaming
     * Base64 encoder ([JdkBase64.getEncoder().wrap]). Unlike the naive
     * chunk-and-concat approach, this correctly handles 3-byte boundaries
     * across chunk boundaries, producing valid Base64 output regardless
     * of input size.
     */
    private fun encodeStream(inputStream: InputStream): String {
        ByteArrayOutputStream().use { outputStream ->
            val encoder = JdkBase64.getEncoder().wrap(outputStream)
            inputStream.copyTo(encoder, bufferSize = BUFFER_SIZE)
            encoder.close()
            return outputStream.toString("UTF-8")
        }
    }
}
