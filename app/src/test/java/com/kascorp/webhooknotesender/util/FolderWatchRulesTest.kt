package com.kascorp.webhooknotesender.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderWatchRulesTest {

    @Test
    fun `image profile accepts image extensions`() {
        listOf("photo.jpg", "pic.jpeg", "screen.png", "art.webp", "anim.gif", "raw.bmp", "img.HEIC", "scan.avif", "tiff.tif", "UPPER.JPG")
            .forEach { name ->
                assertTrue("Expected '$name' to be watched for image", FolderWatchRules.isWatchedFile(name, "image"))
            }
    }

    @Test
    fun `audio profile accepts audio extensions`() {
        listOf("recording.aac", "song.mp3", "voice.m4a", "note.wav", "clip.ogg", "talk.flac", "file.AAC", "UPPER.MP3")
            .forEach { name ->
                assertTrue("Expected '$name' to be watched for audio", FolderWatchRules.isWatchedFile(name, "audio"))
            }
    }

    @Test
    fun `video profile accepts video extensions`() {
        listOf("movie.mp4", "clip.mkv", "video.webm", "cam.mov", "rec.3gp", "cap.m4v", "shot.MP4")
            .forEach { name ->
                assertTrue("Expected '$name' to be watched for video", FolderWatchRules.isWatchedFile(name, "video"))
            }
    }

    @Test
    fun `profile rejects extensions of other types`() {
        assertFalse(FolderWatchRules.isWatchedFile("movie.mp4", "audio"))
        assertFalse(FolderWatchRules.isWatchedFile("song.mp3", "video"))
        assertFalse(FolderWatchRules.isWatchedFile("photo.jpg", "audio"))
        assertFalse(FolderWatchRules.isWatchedFile("photo.jpg", "video"))
        assertFalse(FolderWatchRules.isWatchedFile("movie.mp4", "image"))
        assertFalse(FolderWatchRules.isWatchedFile("song.mp3", "image"))
    }

    @Test
    fun `partial downloads temp files and other types are ignored`() {
        listOf("movie.mp4.part", "song.mp3.crdownload", "clip.mp4.tmp", "video.download", "rec.temp", "audio~", ".hidden.mp3", ".hidden.jpg", "notes.txt", "photo.jpg.part")
            .forEach { name ->
                assertFalse("Expected '$name' NOT to be watched for image", FolderWatchRules.isWatchedFile(name, "image"))
                assertFalse("Expected '$name' NOT to be watched for audio", FolderWatchRules.isWatchedFile(name, "audio"))
                assertFalse("Expected '$name' NOT to be watched for video", FolderWatchRules.isWatchedFile(name, "video"))
            }
    }

    @Test
    fun `files without extension are ignored`() {
        assertFalse(FolderWatchRules.isWatchedFile("noext", "image"))
        assertFalse(FolderWatchRules.isWatchedFile("noext", "audio"))
        assertFalse(FolderWatchRules.isWatchedFile("noext", "video"))
    }

    @Test
    fun `sightingsNeeded is 2 with valid lastModified and 3 without`() {
        assertEquals(2, FolderWatchRules.sightingsNeeded(1_700_000_000_000L))
        assertEquals(2, FolderWatchRules.sightingsNeeded(123L))
        assertEquals(3, FolderWatchRules.sightingsNeeded(0L))
        assertEquals(3, FolderWatchRules.sightingsNeeded(-1L))
    }
}
