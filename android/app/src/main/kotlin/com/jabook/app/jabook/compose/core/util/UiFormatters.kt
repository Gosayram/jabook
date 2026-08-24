// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.core.util

import android.content.res.Resources
import com.jabook.app.jabook.R
import java.util.Locale

/**
 * Centralized formatters for UI display values.
 *
 * Use these throughout the app to ensure consistent formatting of
 * duration, speed, size, and other numeric values.
 */
public object UiFormatters {
    private val STRIP_NUMERIC_PREFIX_REGEX = Regex("^\\d+[.)\\s]+")

    public fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    public fun formatDurationCompact(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${totalSeconds}s"
        }
    }

    public fun formatTimeRemaining(ms: Long): String {
        if (ms <= 0) return ""
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "-$hours:${String.format(Locale.getDefault(), "%02d", minutes)}"
            else -> "-${String.format(Locale.getDefault(), "%d:%02d", minutes, totalSeconds % 60)}"
        }
    }

    public fun formatSpeed(speed: Float): String {
        val rounded = (Math.round(speed * 20.0) / 20.0).toFloat()
        return if (rounded == rounded.toInt().toFloat()) {
            "${rounded.toInt()}x"
        } else {
            String.format(Locale.getDefault(), "%.1fx", rounded)
        }
    }

    public fun formatSpeedDisplay(speed: Float): String = String.format(Locale.getDefault(), "%.2fx", speed)

    public fun formatFileSize(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        val kb = safeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
            else -> "$safeBytes B"
        }
    }

    /** Localized variant for user-visible size display. */
    public fun formatFileSize(
        bytes: Long,
        resources: Resources,
    ): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        val kb = safeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> resources.getString(R.string.size_gb, gb)
            mb >= 1.0 -> resources.getString(R.string.size_mb, mb)
            kb >= 1.0 -> resources.getString(R.string.size_kb, kb)
            else -> resources.getString(R.string.size_bytes, safeBytes)
        }
    }

    public fun formatSpeedBytes(bytesPerSecond: Long): String = formatFileSize(bytesPerSecond) + "/s"

    public fun formatMegaBytes(mb: Float): String {
        if (mb >= 1024) {
            return String.format(Locale.getDefault(), "%.1f GB", mb / 1024)
        }
        return String.format(Locale.getDefault(), "%.0f MB", mb)
    }

    public fun formatPercent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"

    public fun formatChapterNumber(
        index: Int,
        total: Int,
    ): String = "${index + 1} / $total"

    /**
     * Strips leading numeric prefixes like "1.", "01.", "12) " from strings.
     * Used to avoid duplication when chapter display number is prepended.
     */
    public fun stripLeadingNumericPrefix(title: String): String = title.replace(STRIP_NUMERIC_PREFIX_REGEX, "").trim()
}
