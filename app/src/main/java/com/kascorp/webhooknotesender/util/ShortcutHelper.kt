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
     * Cleans up any existing (e.g., disabled) shortcut first to avoid crash.
     */
    fun requestPinShortcut(profile: ProfileEntity) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return
        }

        // Clean up any existing shortcut (disabled/greyed out) to avoid crash when re-creating
        val shortcutId = "$SHORTCUT_PREFIX${profile.id}"
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
        } catch (_: Exception) { }
        try {
            ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(shortcutId))
        } catch (_: Exception) { }
        // Platform API (API 30+): enable then remove disabled shortcuts.
        // Xiaomi launcher doesn't remove disabled shortcuts via compat methods,
        // so we enable first, then remove via platform API.
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.enableShortcuts(listOf(shortcutId))
                manager?.removeLongLivedShortcuts(listOf(shortcutId))
                manager?.removeDynamicShortcuts(listOf(shortcutId))
            } catch (_: Exception) { }
        }

        val shortcut = createShortcutInfo(profile)
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        // Track pinned shortcut for isShortcutCreated()
        prefs.edit().putBoolean(shortcutId, true).apply()
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
     * Tries multiple removal methods to ensure pinned shortcuts are removed
     * from the home screen across different launchers.
     */
    fun removeShortcut(profileId: Long) {
        val shortcutId = "$SHORTCUT_PREFIX$profileId"
        prefs.edit().remove(shortcutId).apply()

        // Method 1: Remove from dynamic shortcuts (all API levels)
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
        } catch (_: Exception) { }

        // Method 2: Remove long-lived/pinned via compat library
        try {
            ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(shortcutId))
        } catch (_: Exception) { }

        // Method 3: Remove long-lived/pinned via platform API (API 30+)
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.removeLongLivedShortcuts(listOf(shortcutId))
            } catch (_: Exception) { }
        }

        // Method 4: Disable as fallback — grays out the shortcut so tapping does nothing
        try {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(shortcutId),
                "Shortcut removed"
            )
        } catch (_: Exception) { }
    }

    /**
     * Checks if a shortcut exists for the given profile.
     * Uses dynamic shortcuts (reliable across all launchers) and SharedPreferences
     * (for pinned shortcuts — some launchers like Xiaomi don't report pinned
     * shortcuts correctly via the platform API).
     */
    fun isShortcutCreated(profileId: Long): Boolean {
        val shortcutId = "$SHORTCUT_PREFIX$profileId"

        // Dynamic shortcuts are reliably reported across all launchers
        val existsInDynamic = ShortcutManagerCompat.getDynamicShortcuts(context)
            .any { it.id == shortcutId }
        if (existsInDynamic) return true

        // For pinned shortcuts, trust SharedPreferences as source of truth.
        // Some launchers (Xiaomi, etc.) don't report pinned shortcuts via the
        // platform API, so we can't use pinnedShortcuts for cleanup.
        return prefs.getBoolean(shortcutId, false)
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
            .setLongLived(true)
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
