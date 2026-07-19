package com.kascorp.webhooknotesender.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.graphics.drawable.VectorDrawable
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
     * Uses removeLongLivedShortcuts() on API 30+ to also remove pinned shortcuts
     * from the home screen. Falls back to removeDynamicShortcuts() on older API.
     */
    fun removeShortcut(profileId: Long) {
        val shortcutId = "$SHORTCUT_PREFIX$profileId"
        prefs.edit().remove(shortcutId).apply()
        if (Build.VERSION.SDK_INT >= 30) {
            val manager = context.getSystemService(ShortcutManager::class.java)
            manager?.removeLongLivedShortcuts(listOf(shortcutId))
        } else {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
        }
    }

    /**
     * Checks if a shortcut exists for the given profile.
     * Checks pinned shortcuts (API 33+), dynamic shortcuts, and SharedPreferences.
     * If the shortcut was removed manually from the home screen, cleans up prefs.
     */
    fun isShortcutCreated(profileId: Long): Boolean {
        val shortcutId = "$SHORTCUT_PREFIX$profileId"
        val prefsHas = prefs.getBoolean(shortcutId, false)
        if (!prefsHas) return false

        val existsInDynamic = ShortcutManagerCompat.getDynamicShortcuts(context)
            .any { it.id == shortcutId }

        // Check pinned shortcuts via platform API (Android 13+)
        val existsInPinned = if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(ShortcutManager::class.java)
            manager?.pinnedShortcuts?.any { it.id == shortcutId } ?: false
        } else {
            false
        }

        val exists = existsInDynamic || existsInPinned
        if (!exists) {
            // User removed the shortcut manually — clean up stale prefs entry
            prefs.edit().remove(shortcutId).apply()
        }
        return exists
    }

    /**
     * Creates a ShortcutInfoCompat for the given profile.
     * Generates a bitmap icon with a colored circle background
     * and white icon for reliable rendering across launchers.
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

        val bgColor = when (profile.type.lowercase()) {
            "audio" -> 0xFF7C4DFF.toInt()
            "video" -> 0xFF00C853.toInt()
            else -> 0xFF448AFF.toInt()
        }

        val icon = createShortcutIcon(iconRes, bgColor)

        return ShortcutInfoCompat.Builder(context, "$SHORTCUT_PREFIX${profile.id}")
            .setShortLabel(profile.name)
            .setLongLabel(profile.name)
            .setIcon(icon)
            .setIntent(intent)
            .build()
    }

    /**
     * Generates a bitmap with a colored circle background and the vector icon in white.
     */
    private fun createShortcutIcon(iconRes: Int, bgColor: Int): IconCompat {
        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            .coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw colored circle background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            isDither = true
        }
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Draw white icon on top
        val drawable = context.getDrawable(iconRes)
        if (drawable is VectorDrawable) {
            val iconSize = (size * 0.56f).toInt()
            val iconOffset = (size - iconSize) / 2
            drawable.setBounds(iconOffset, iconOffset, iconOffset + iconSize, iconOffset + iconSize)
            drawable.draw(canvas)
        }

        return IconCompat.createWithBitmap(bitmap)
    }
}
