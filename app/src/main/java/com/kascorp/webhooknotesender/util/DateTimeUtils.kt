package com.kascorp.webhooknotesender.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DateTimeUtils @Inject constructor() {

    companion object {
        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun nowUtcIso8601(): String {
            return isoFormat.format(Date())
        }

        fun formatTimestamp(timestamp: Long): String {
            return isoFormat.format(Date(timestamp))
        }

        fun formatRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60_000 -> "< 1 min"
                diff < 3_600_000 -> "${diff / 60_000} min"
                diff < 86_400_000 -> "${diff / 3_600_000} h"
                else -> "${diff / 86_400_000} d"
            }
        }
    }
}
