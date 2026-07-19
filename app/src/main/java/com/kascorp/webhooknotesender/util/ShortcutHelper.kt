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
        private const val PREFS_NAME = "shortcut_prefs"
        const val ACTION_CAPTURE_SHORTCUT = "com.kascorp.webhooknotesender.CAPTURE_SHORTCUT"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        // Track pinned shortcut for isShortcutCreated()
        prefs.edit().putBoolean("$SHORTCUT_PREFIX${profile.id}", true).apply()
    }

    /**
     * Creates a dynamic shortcut for a specific profile.
     */
    fun createDynamicShortcut(profile: ProfileEntity): Boolean {
        if (isShortcutCreated(profile.id)) return false
        val shortcut = createShortcutInfo(profile)
        val result = ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
        prefs.edit().putBoolean("$SHORTCUT_PREFIX${profile.id}", true).apply()
        return result
    }

    /**
     * Removes shortcuts associated with a specific profile.
     */
    fun removeShortcut(profileId: Long) {
        val shortcutId = "$SHORTCUT_PREFIX$profileId"
        prefs.edit().remove(shortcutId).apply()
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
    }

    /**
     * Checks if a shortcut exists for the given profile.
     * Checks both pinned (via SharedPreferences) and dynamic shortcuts.
     */
    fun isShortcutCreated(profileId: Long): Boolean {
        val shortcutId = "$SHORTCUT_PREFIX$profileId"
        return prefs.getBoolean(shortcutId, false) ||
            ShortcutManagerCompat.getDynamicShortcuts(context)
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
            .setLongLabel(profile.name)
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }
}
