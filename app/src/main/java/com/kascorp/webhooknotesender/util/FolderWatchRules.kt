package com.kascorp.webhooknotesender.util

/**
 * Pure rules used by [com.kascorp.webhooknotesender.work.FolderWatcherService]
 * to decide which files in a watched folder are candidates for queueing.
 *
 * Kept free of Android dependencies so it can be unit tested.
 */
object FolderWatchRules {

    /** File extensions accepted for image profiles. */
    val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif",
        "tiff", "tif", "ico", "svg", "dng", "jfif"
    )

    /** File extensions accepted for audio profiles. */
    val AUDIO_EXTENSIONS = setOf(
        "aac", "mp3", "m4a", "wav", "ogg", "opus", "amr", "flac", "aiff", "aif", "m4b"
    )

    /** File extensions accepted for video profiles. */
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v", "ts", "mts", "flv", "wmv", "mpg", "mpeg"
    )

    /** Suffixes of in-progress/partial downloads that must never be queued. */
    private val IGNORED_SUFFIXES = listOf(
        ".part", ".crdownload", ".tmp", ".temp", ".download", ".partial", ".opdownload", ".ytdl", "~"
    )

    /**
     * Returns true if [fileName] should be queued for a profile of the given
     * [mediaType] ("image", "audio" or "video").
     */
    fun isWatchedFile(fileName: String, mediaType: String): Boolean {
        val name = fileName.lowercase()
        if (name.startsWith(".")) return false
        if (IGNORED_SUFFIXES.any { name.endsWith(it) }) return false
        val ext = name.substringAfterLast('.', "")
        if (ext.isEmpty() || ext == name) return false
        return when (mediaType.lowercase()) {
            "image" -> ext in IMAGE_EXTENSIONS
            "audio" -> ext in AUDIO_EXTENSIONS
            "video" -> ext in VIDEO_EXTENSIONS
            else -> false
        }
    }

    /**
     * Number of consecutive polls during which a file must keep an identical
     * size + last-modified snapshot before it is considered fully written.
     *
     * Providers that don't report last-modified (0 or negative) get an extra
     * sighting to reduce the chance of queueing a file that is still growing.
     */
    fun sightingsNeeded(lastModified: Long): Int = if (lastModified <= 0) 3 else 2
}
