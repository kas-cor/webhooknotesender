package com.kascorp.webhooknotesender.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
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
                val result = compressVideoFile(file, quality)
                result.copy(originalSize = originalSize)
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

    private fun compressVideoFile(file: File, quality: Int): CompressResult {
        val outputFile = File(file.parentFile, "comp_${file.name}")
        try {
            transcodeVideo(file.absolutePath, outputFile.absolutePath, quality)
            val transcoded = outputFile.readBytes()
            // Gzip-compress the transcoded MP4 so the "gzip" encoding tag is accurate.
            // The server must gzip-decompress to get the h.264 MP4 data.
            val compressed = gzipCompress(transcoded)
            return CompressResult(
                data = compressed,
                encoding = "gzip",
                originalSize = file.length(),
                compressedSize = compressed.size.toLong()
            )
        } finally {
            if (outputFile.exists()) outputFile.delete()
        }
    }

    private fun transcodeVideo(inputPath: String, outputPath: String, quality: Int) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        var videoTrackIdx = -1
        var videoFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                videoTrackIdx = i
                videoFormat = fmt
                break
            }
        }
        if (videoTrackIdx == -1) {
            extractor.release()
            java.io.File(inputPath).copyTo(java.io.File(outputPath), overwrite = true)
            return
        }

        val width = videoFormat!!.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val origBitrate = if (videoFormat.containsKey(MediaFormat.KEY_BIT_RATE))
            videoFormat.getInteger(MediaFormat.KEY_BIT_RATE) else 2_000_000
        val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
        val targetBitrate = (origBitrate.toLong() * quality / 100).coerceIn(200_000L, origBitrate.toLong()).toInt()

        val encodeFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val decoder = MediaCodec.createDecoderByType(videoFormat!!.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(videoFormat, inputSurface, null, 0)
        decoder.start()

        extractor.selectTrack(videoTrackIdx)

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputIdx = decoder.dequeueInputBuffer(10000L)
                if (inputIdx >= 0) {
                    val inputBuf = decoder.getInputBuffer(inputIdx)!!
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inputIdx, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var decoderOutputIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000L)
            when {
                decoderOutputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no output yet */ }
                decoderOutputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                decoderOutputIdx >= 0 -> {
                    decoder.releaseOutputBuffer(decoderOutputIdx, false)
                }
            }

            val encoderOutputIdx = encoder.dequeueOutputBuffer(bufferInfo, 10000L)
            when {
                encoderOutputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no output yet */ }
                encoderOutputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw RuntimeException("format changed twice")
                    muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                encoderOutputIdx >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0) {
                        val outBuf = encoder.getOutputBuffer(encoderOutputIdx)!!
                        muxer.writeSampleData(0, outBuf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encoderOutputIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }
        }

        extractor.release()
        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
        inputSurface.release()
    }

    data class CompressResult(
        val data: ByteArray,
        val encoding: String?,
        val originalSize: Long = 0L,
        val compressedSize: Long = 0L
    )
}
