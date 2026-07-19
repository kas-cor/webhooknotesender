package com.kascorp.webhooknotesender.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class MediaCompressorTest {

    private val compressor = MediaCompressor()

    /**
     * Create a test bitmap (400x400) with color gradient so JPEG
     * compression has clear quality-dependent size differences.
     */
    private fun createTestBitmapBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        for (x in 0 until 400) {
            for (y in 0 until 400) {
                bitmap.setPixel(x, y, (0xFF shl 24) or (x shl 16) or (y shl 8) or (x xor y))
            }
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) // lossless PNG source
        bitmap.recycle()
        return stream.toByteArray()
    }

    // ===================== Image compression at various quality levels =====================

    @Test
    fun `compressImageBytes at quality 1 produces valid JPEG`() {
        val data = createTestBitmapBytes()
        val compressed = compressor.compressImageBytes(data, 1)
        assert(compressed.size < data.size) { "Compressed size (${compressed.size}) should be < original (${data.size})" }
        assert(compressed.isNotEmpty()) { "Compressed JPEG should not be empty" }
        assert(compressed[0] == 0xFF.toByte() && compressed[1] == 0xD8.toByte()) { "Should start with JPEG magic bytes FF D8" }
    }

    @Test
    fun `compressImageBytes at quality 25 produces valid JPEG`() {
        val data = createTestBitmapBytes()
        val compressed = compressor.compressImageBytes(data, 25)
        assert(compressed.size < data.size) { "Compressed size (${compressed.size}) should be < original (${data.size})" }
        assert(compressed.isNotEmpty()) { "Compressed JPEG should not be empty" }
    }

    @Test
    fun `compressImageBytes at quality 50 produces valid JPEG`() {
        val data = createTestBitmapBytes()
        val compressed = compressor.compressImageBytes(data, 50)
        assert(compressed.size < data.size) { "Compressed size (${compressed.size}) should be < original (${data.size})" }
        assert(compressed.isNotEmpty()) { "Compressed JPEG should not be empty" }
    }

    @Test
    fun `compressImageBytes at quality 75 produces valid JPEG`() {
        val data = createTestBitmapBytes()
        val compressed = compressor.compressImageBytes(data, 75)
        assert(compressed.size < data.size) { "Compressed size (${compressed.size}) should be < original (${data.size})" }
        assert(compressed.isNotEmpty()) { "Compressed JPEG should not be empty" }
    }

    @Test
    fun `compressImageBytes at quality 100 produces valid JPEG`() {
        val data = createTestBitmapBytes()
        val compressed = compressor.compressImageBytes(data, 100)
        assert(compressed.size < data.size) { "Compressed size (${compressed.size}) should be < original (${data.size})" }
        assert(compressed.isNotEmpty()) { "Compressed JPEG should not be empty" }
    }

    @Test
    fun `higher quality produces larger or equal compressed image`() {
        val data = createTestBitmapBytes()
        val sizes = listOf(1, 25, 50, 75, 100).map { quality ->
            compressor.compressImageBytes(data, quality).size
        }
        // Each subsequent quality level should produce >= size of the previous
        for (i in 1 until sizes.size) {
            assert(sizes[i] >= sizes[i - 1]) {
                "Quality level $i (size=${sizes[i]}) should be >= previous (size=${sizes[i - 1]})"
            }
        }
        // Quality 100 should be noticeably larger than quality 1
        val ratio = sizes.last().toDouble() / sizes.first().toDouble()
        assert(ratio >= 1.2) {
            "Quality 100 (${sizes.last()}) should be >= 1.2× quality 1 (${sizes.first()}), but ratio is $ratio"
        }
    }

    @Test
    fun `compressed JPEG can be decoded back to bitmap`() {
        val data = createTestBitmapBytes()
        val compressed = compressor.compressImageBytes(data, 50)
        val decoded = BitmapFactory.decodeByteArray(compressed, 0, compressed.size)
        assert(decoded.width > 0) { "Decoded bitmap should have positive width" }
        assert(decoded.height > 0) { "Decoded bitmap should have positive height" }
        assert(decoded.width == 400) { "Decoded width should be 400, got ${decoded.width}" }
        assert(decoded.height == 400) { "Decoded height should be 400, got ${decoded.height}" }
        decoded.recycle()
    }

    // ===================== compress() public API =====================

    @Test
    fun `compress for image returns result with jpeg encoding`() {
        val data = createTestBitmapBytes()
        val result = compressor.compress(data, "image", 50)
        assert(result.data.size < data.size) { "Compressed data should be smaller than original" }
        assert(result.compressedSize < result.originalSize) {
            "Compressed size (${result.compressedSize}) should be < original (${result.originalSize})"
        }
        assert(result.encoding == "jpeg") { "encoding should be 'jpeg' for image, got '${result.encoding}'" }
    }

    @Test
    fun `compress for audio returns gzip encoding`() {
        val data = "Test audio data with repeating patterns for compression. ".repeat(200).toByteArray()
        val result = compressor.compress(data, "audio", 50)
        assert(result.data.size < data.size) { "Compressed data should be smaller than original" }
        assert(result.encoding == "gzip") { "encoding should be 'gzip' for audio, got '${result.encoding}'" }
    }

    @Test
    fun `compress for video returns gzip encoding`() {
        val data = "Test video data placeholder. ".repeat(200).toByteArray()
        val result = compressor.compress(data, "video", 50)
        assert(result.data.size < data.size) { "Compressed data should be smaller than original" }
        assert(result.encoding == "gzip") { "encoding should be 'gzip' for video, got '${result.encoding}'" }
    }

    // ===================== gzip roundtrip =====================

    @Test
    fun `gzip roundtrip preserves original data`() {
        val original = "Hello compress-and-decompress world! ".repeat(100).toByteArray()
        val compressed = compressor.gzipCompress(original)
        val decompressed = compressor.gzipDecompress(compressed)
        assert(original.contentEquals(decompressed)) { "Decompressed data should match original" }
        assert(compressed.size < original.size) { "Compressed size should be smaller" }
    }

    @Test
    fun `gzip roundtrip on binary data`() {
        val original = ByteArray(2000) { (it % 251).toByte() }
        val compressed = compressor.gzipCompress(original)
        val decompressed = compressor.gzipDecompress(compressed)
        assert(original.contentEquals(decompressed)) { "Decompressed binary data should match original" }
        assert(compressed.size < original.size) { "Binary data should compress to smaller size" }
    }

    // ===================== Quality extremes =====================

    @Test
    fun `compress with quality 1 is smaller than quality 100`() {
        val data = createTestBitmapBytes()
        val result1 = compressor.compress(data, "image", 1)
        val result100 = compressor.compress(data, "image", 100)
        assert(result1.compressedSize < result100.compressedSize) {
            "Quality 1 (${result1.compressedSize}) should be smaller than quality 100 (${result100.compressedSize})"
        }
    }

    @Test
    fun `compress returns originalSize equal to input data size`() {
        val data = createTestBitmapBytes()
        val result = compressor.compress(data, "image", 50)
        assert(result.originalSize == data.size.toLong()) {
            "originalSize (${result.originalSize}) should match input size (${data.size})"
        }
    }
}
