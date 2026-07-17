package com.kascorp.webhooknotesender.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.ShortcutReceiverActivity
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val SHORTCUT_PREFIX = "shortcut_"
        const val ACTION_CAPTURE_SHORTCUT = "com.kascorp.webhooknotesender.CAPTURE_SHORTCUT"
    }

    /**
     * Creates a pinned shortcut on the home screen.
     * Requires user confirmation via system dialog.
     */
    fun requestPinShortcut(profile: ProfileEntity) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return
        }

        val shortcut = createShortcutInfo(profile)
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * Updates dynamic shortcuts with all active profiles.
     * Maximum 5 dynamic shortcuts (system limit).
     */
    fun updateDynamicShortcuts(profiles: List<ProfileEntity>) {
        val shortcuts = profiles.take(5).map { profile ->
            createShortcutInfo(profile)
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /**
     * Removes shortcuts associated with a specific profile.
     */
    fun removeShortcut(profileId: Long) {
        val shortcutId = "$SHORTCUT_PREFIX$profileId"
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
    }

    /**
     * Checks if a shortcut exists for the given profile.
     */
    fun isShortcutCreated(profileId: Long): Boolean {
        val shortcutId = "$SHORTCUT_PREFIX$profileId"
        return ShortcutManagerCompat.getDynamicShortcuts(context)
            .any { it.id == shortcutId }
    }

    /**
     * Creates a ShortcutInfoCompat for the given profile.
     */
    private fun createShortcutInfo(profile: ProfileEntity): ShortcutInfoCompat {
        val intent = Intent(ACTION_CAPTURE_SHORTCUT).apply {
            putExtra("profile_id", profile.id)
            `package` = context.packageName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val iconRes = when (profile.type.lowercase()) {
            "audio" -> R.drawable.ic_mic
            "video" -> R.drawable.ic_videocam
            else -> R.drawable.ic_camera
        }

        return ShortcutInfoCompat.Builder(context, "$SHORTCUT_PREFIX${profile.id}")
            .setShortLabel(profile.name)
            .setLongLabel("${profile.name} · ${profile.type}")
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }
}
