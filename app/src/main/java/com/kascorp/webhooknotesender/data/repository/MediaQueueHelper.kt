package com.kascorp.webhooknotesender.data.repository

import android.content.Context
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.util.DateTimeUtils
import com.kascorp.webhooknotesender.util.ShortcutHelper
import com.kascorp.webhooknotesender.work.QueueWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared "enqueue media" pipeline used both by manual captures
 * (ProfilesViewModel) and by the folder watcher (FolderWatcherService).
 *
 * Builds the webhook JSON payload, saves it as a file FIRST (before the DB
 * insert, to avoid a race with QueueWorker), inserts a PENDING queue item,
 * triggers queue processing, increments the profile use count and refreshes
 * the app shortcuts ranking.
 */
@Singleton
class MediaQueueHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueRepository: QueueRepository,
    private val profileRepository: ProfileRepository,
    private val shortcutHelper: ShortcutHelper,
    private val json: Json
) {

    fun buildJsonPayload(profile: ProfileEntity, base64Data: String, encoding: String? = null): String {
        val messageMap = mutableMapOf(
            "name" to JsonPrimitive(profile.name),
            "prompt" to JsonPrimitive(profile.prompt),
            "datetime" to JsonPrimitive(DateTimeUtils.nowUtcIso8601()),
            "type" to JsonPrimitive(profile.type),
            "data" to JsonPrimitive(base64Data)
        )
        if (encoding != null) {
            messageMap["encoding"] = JsonPrimitive(encoding)
        }
        val payload = JsonObject(
            mapOf(
                "messages" to JsonArray(listOf(JsonObject(messageMap)))
            )
        )
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    suspend fun enqueue(
        profile: ProfileEntity,
        base64Data: String,
        encoding: String? = null,
        sourceUri: String? = null
    ): Long {
        val payload = buildJsonPayload(profile, base64Data, encoding)
        // Save payload to file FIRST (before DB insert) to avoid race condition:
        // QueueWorker could pick up the item before the payload file is saved.
        val fileName = PayloadFileHelper.savePayload(context, payload)
        val queueItem = QueueItemEntity(
            profileName = profile.name,
            url = profile.url,
            bearerToken = profile.bearerToken,
            jsonPayload = "",
            payloadFilePath = fileName,
            mediaType = profile.type,
            status = QueueStatus.PENDING.name,
            sourceUri = sourceUri
        )
        val id = queueRepository.insert(queueItem)
        // Trigger queue processing
        QueueWorker.enqueue(context)
        // Track usage for app shortcuts ranking
        profileRepository.incrementUseCount(profile.id)
        // Update app shortcuts (long-press app icon) with new rankings
        val topProfiles = profileRepository.getTopProfiles(5).first()
        shortcutHelper.updateAppShortcuts(topProfiles)
        return id
    }
}
