package com.kascorp.webhooknotesender.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCompressor @Inject constructor() {

    fun compress(data: ByteArray, mediaType: String, quality: Int): CompressResult {
        val originalSize = data.size.toLong()
        return when (mediaType.lowercase()) {
            "image" -> {
                val compressed = compressImageBytes(data, quality)
                CompressResult(compressed, "jpeg", originalSize, compressed.size.toLong())
            }
            else -> {
                val compressed = gzipCompress(data)
                CompressResult(compressed, "gzip", originalSize, compressed.size.toLong())
            }
        }
    }

    fun compressFile(file: File, mediaType: String, quality: Int): CompressResult {
        val originalSize = file.length()
        return when (mediaType.lowercase()) {
            "image" -> {
                val compressed = compressImageFile(file, quality)
                CompressResult(compressed, "jpeg", originalSize, compressed.size.toLong())
            }
            "video" -> {
                // Raw gzip compression only (same as in-app path).
                // Video transcoding via MediaCodec was removed because the
                // decoder/encoder/surface pipeline had multiple bugs causing
                // hangs. Gzip of camera-encoded video still reduces size.
                val compressed = gzipCompress(file.readBytes())
                CompressResult(compressed, "gzip", originalSize, compressed.size.toLong())
            }
            "audio" -> {
                val compressed = gzipCompress(file.readBytes())
                CompressResult(compressed, "gzip", originalSize, compressed.size.toLong())
            }
            else -> {
                val compressed = gzipCompress(file.readBytes())
                CompressResult(compressed, "gzip", originalSize, compressed.size.toLong())
            }
        }
    }

    fun compressImageBytes(data: ByteArray, quality: Int): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    fun compressImageFile(file: File, quality: Int): ByteArray {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    fun gzipCompress(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { it.write(data) }
        return outputStream.toByteArray()
    }

    fun gzipDecompress(data: ByteArray): ByteArray {
        return java.util.zip.GZIPInputStream(data.inputStream()).use { it.readBytes() }
    }

    data class CompressResult(
        val data: ByteArray,
        val encoding: String?,
        val originalSize: Long = 0L,
        val compressedSize: Long = 0L
    )
}
