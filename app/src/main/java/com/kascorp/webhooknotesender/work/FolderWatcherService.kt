package com.kascorp.webhooknotesender.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kascorp.webhooknotesender.MainActivity
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.repository.MediaQueueHelper
import com.kascorp.webhooknotesender.data.repository.ProfileRepository
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import com.kascorp.webhooknotesender.util.Base64Encoder
import com.kascorp.webhooknotesender.util.FolderWatchRules
import com.kascorp.webhooknotesender.util.MediaCompressor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that watches user-selected folders (via SAF tree URIs)
 * for profiles of type image/audio/video. When a new file matching the
 * profile's media type appears and is fully written, it is compressed,
 * Base64-encoded, added to the send queue and then deleted from the folder.
 *
 * Uses `specialUse` foreground service type so it can keep running in the
 * background without the Android 15 time limits of `dataSync`.
 */
@AndroidEntryPoint
class FolderWatcherService : Service() {

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var mediaQueueHelper: MediaQueueHelper

    @Inject
    lateinit var queueRepository: QueueRepository

    @Inject
    lateinit var base64Encoder: Base64Encoder

    @Inject
    lateinit var mediaCompressor: MediaCompressor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    /** Per-profile polling state, kept alive across polls. */
    private val folderStates = mutableMapOf<Long, FolderState>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (watchJob?.isActive != true) {
            watchJob = serviceScope.launch { watchLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun watchLoop() {
        while (coroutineContext.isActive) {
            val profiles = profileRepository.getWatchedProfiles().first()
            val queuedSourceUris = queueRepository.getItemsWithSourceUri()
                .mapNotNull { it.sourceUri }
                .toSet()
            if (profiles.isEmpty()) {
                // Nothing to watch anymore — shut down until next start() call
                stopSelf()
                return
            }
            // Drop state for profiles whose watch folder was removed
            folderStates.keys.retainAll(profiles.map { it.id })

            for (profile in profiles) {
                try {
                    pollFolder(profile, queuedSourceUris)
                } catch (e: Exception) {
                    Log.w(TAG, "Poll failed for profile '${profile.name}'", e)
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun pollFolder(profile: ProfileEntity, queuedSourceUris: Set<String>) {
        val watchUri = profile.watchUri ?: return
        val children = SafFolder.listFiles(contentResolver, Uri.parse(watchUri))
            ?: run {
                // Folder inaccessible (e.g. tree grant revoked) — skip this profile
                Log.w(TAG, "Cannot access watch folder of profile '${profile.name}'")
                return
            }
        // An empty folder is handled naturally below: vanished files are pruned
        // from state, and queued files already deleted are dropped.
        val state = folderStates.getOrPut(profile.id) { FolderState() }
        val now = System.currentTimeMillis()

        // Files are deleted by QueueWorker only after a successful webhook response.

        // 2) Detect new / fully-written files
        for ((name, doc) in children) {
            if (name in state.queued || doc.uri.toString() in queuedSourceUris) continue
            if (!FolderWatchRules.isWatchedFile(name, profile.type)) continue

            val retryAt = state.failed[name]
            if (retryAt != null && retryAt > now) continue

            val snapshot = FileSnapshot(doc.size, doc.lastModified)
            val prev = state.known[name]
            when {
                prev == null -> {
                    state.known[name] = snapshot
                    state.sightings[name] = 1
                }
                prev == snapshot -> {
                    val sightings = (state.sightings[name] ?: 1) + 1
                    if (sightings >= FolderWatchRules.sightingsNeeded(snapshot.lastModified)) {
                        // File is stable — process it
                        state.known.remove(name)
                        state.sightings.remove(name)
                        if (processFile(profile, name, doc)) {
                            state.queued.add(name)
                        } else {
                            state.failed[name] = now + RETRY_DELAY_MS
                        }
                    } else {
                        state.sightings[name] = sightings
                    }
                }
                else -> {
                    // Still being written — update snapshot
                    state.known[name] = snapshot
                    state.sightings[name] = 1
                }
            }
        }

        // 3) Drop transient state for files that vanished. Queued files are
        // intentionally retained until the queue worker deletes the source.
        val current = children.keys
        state.known.keys.retainAll(current)
        state.failed.keys.retainAll(current)
        state.sightings.keys.retainAll(current)
    }

    /**
     * Reads the file, compresses + Base64-encodes it and enqueues it.
     * Returns true if the file was enqueued successfully.
     */
    private suspend fun processFile(profile: ProfileEntity, name: String, doc: SafFolder.Doc): Boolean {
        return try {
            val bytes = SafFolder.read(contentResolver, doc.uri)
            val base64: String
            val encoding: String?
            if (profile.compressEnabled) {
                val result = mediaCompressor.compress(bytes, profile.type, profile.compressionQuality)
                base64 = base64Encoder.encode(result.data)
                encoding = result.encoding
            } else {
                base64 = base64Encoder.encode(bytes)
                encoding = null
            }
            mediaQueueHelper.enqueue(profile, base64, encoding, doc.uri.toString())
            Log.i(TAG, "Queued watched file '$name' (profile '${profile.name}')")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process watched file '$name'", e)
            false
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.folder_watcher_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.folder_watcher_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.folder_watcher_notification_title))
            .setContentText(getString(R.string.folder_watcher_notification_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "FolderWatcher"
        private const val CHANNEL_ID = "folder_watcher"
        private const val NOTIFICATION_ID = 2001

        /** How often each watched folder is polled for new files. */
        private const val POLL_INTERVAL_MS = 3_000L

        /** Cooldown before retrying a file that failed to process (e.g. locked). */
        private const val RETRY_DELAY_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, FolderWatcherService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

/** Snapshot of a file's size + last-modified, used for stability detection. */
data class FileSnapshot(val size: Long, val lastModified: Long)

/** Per-folder polling state. */
class FolderState {
    val known = mutableMapOf<String, FileSnapshot>()
    val sightings = mutableMapOf<String, Int>()
    val queued = mutableSetOf<String>()
    val failed = mutableMapOf<String, Long>()
}

/**
 * Minimal SAF (Storage Access Framework) helpers built on DocumentsContract —
 * no extra dependencies. Reads, lists and deletes files under a persisted
 * tree URI granted via ACTION_OPEN_DOCUMENT_TREE.
 */
object SafFolder {

    data class Doc(val uri: Uri, val size: Long, val lastModified: Long)

    /** Lists child files of a tree URI as [name] -> [Doc]. Returns null on error. */
    fun listFiles(contentResolver: ContentResolver, treeUri: Uri): Map<String, Doc>? {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val result = LinkedHashMap<String, Doc>()
            contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeIndex)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val docId = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    val size = if (cursor.isNull(sizeIndex)) -1L else cursor.getLong(sizeIndex)
                    val modified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex)
                    result[name] = Doc(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        size = size,
                        lastModified = modified
                    )
                }
            }
            result
        } catch (e: Exception) {
            Log.w("SafFolder", "listFiles failed for $treeUri", e)
            null
        }
    }

    /** Reads the full contents of a document. */
    fun read(contentResolver: ContentResolver, uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    }

    /** Deletes a document. Returns true on success. */
    fun delete(contentResolver: ContentResolver, uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (e: Exception) {
            Log.w("SafFolder", "delete failed for $uri", e)
            false
        }
    }
}
