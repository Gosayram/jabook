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

package com.jabook.app.jabook.compose.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Centralized DateTime formatting utility.
 * Uses device timezone with UTC fallback.
 * Formats conform to GOST 7.64-90 standard.
 */
object DateTimeFormatter {
    /**
     * GOST 7.64-90 format: DD.MM.YYYY HH:MM
     */
    private const val FORMAT_GOST = "dd.MM.yyyy HH:mm"

    /**
     * GOST 7.64-90 format with seconds: DD.MM.YYYY HH:MM:SS
     */
    private const val FORMAT_GOST_WITH_SECONDS = "dd.MM.yyyy HH:mm:ss"

    /**
     * ISO 8601 format for backup files (sortable): yyyy-MM-dd'T'HH:mm:ss'Z'
     */
    private const val FORMAT_ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    /**
     * Filename-safe format: yyyyMMdd_HHmmss
     */
    private const val FORMAT_FILENAME = "yyyyMMdd_HHmmss"

    /**
     * Get device timezone, fallback to UTC if unavailable.
     */
    private fun getDeviceTimeZone(): TimeZone =
        try {
            TimeZone.getDefault()
        } catch (e: Exception) {
            TimeZone.getTimeZone("UTC")
        }

    /**
     * Format timestamp to GOST 7.64-90 format (DD.MM.YYYY HH:MM).
     * Uses device timezone.
     *
     * @param millis Unix timestamp in milliseconds
     * @return Formatted date string
     */
    fun formatGOST(millis: Long): String {
        val sdf = SimpleDateFormat(FORMAT_GOST, Locale.getDefault())
        sdf.timeZone = getDeviceTimeZone()
        return sdf.format(Date(millis))
    }

    /**
     * Format timestamp to GOST 7.64-90 format with seconds (DD.MM.YYYY HH:MM:SS).
     * Uses device timezone.
     *
     * @param millis Unix timestamp in milliseconds
     * @return Formatted date string with seconds
     */
    fun formatGOSTWithSeconds(millis: Long): String {
        val sdf = SimpleDateFormat(FORMAT_GOST_WITH_SECONDS, Locale.getDefault())
        sdf.timeZone = getDeviceTimeZone()
        return sdf.format(Date(millis))
    }

    /**
     * Format current time to GOST format (DD.MM.YYYY HH:MM).
     * Uses device timezone.
     *
     * @return Current time formatted
     */
    fun formatCurrentGOST(): String = formatGOST(System.currentTimeMillis())

    /**
     * Format current time to GOST format with seconds (DD.MM.YYYY HH:MM:SS).
     * Uses device timezone.
     *
     * @return Current time formatted with seconds
     */
    fun formatCurrentGOSTWithSeconds(): String = formatGOSTWithSeconds(System.currentTimeMillis())

    /**
     * Format timestamp to ISO 8601 format for backup files.
     * Always uses UTC for consistency across devices.
     *
     * @param millis Unix timestamp in milliseconds
     * @return ISO 8601 formatted string in UTC
     */
    fun formatISO8601(millis: Long): String {
        val sdf = SimpleDateFormat(FORMAT_ISO_8601, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    /**
     * Format current time to ISO 8601 format.
     * Always uses UTC for consistency.
     *
     * @return Current time in ISO 8601 format (UTC)
     */
    fun formatCurrentISO8601(): String = formatISO8601(System.currentTimeMillis())

    /**
     * Format timestamp for filename (yyyyMMdd_HHmmss).
     * Uses device timezone.
     *
     * @param millis Unix timestamp in milliseconds
     * @return Filename-safe date string
     */
    fun formatForFilename(millis: Long): String {
        val sdf = SimpleDateFormat(FORMAT_FILENAME, Locale.US)
        sdf.timeZone = getDeviceTimeZone()
        return sdf.format(Date(millis))
    }

    /**
     * Format current time for filename.
     * Uses device timezone.
     *
     * @return Filename-safe current timestamp
     */
    fun formatCurrentForFilename(): String = formatForFilename(System.currentTimeMillis())

    /**
     * Get current timezone ID.
     *
     * @return Timezone ID (e.g., "Asia/Tashkent", "UTC")
     */
    fun getCurrentTimeZoneId(): String = getDeviceTimeZone().id

    /**
     * Get current timezone offset in minutes.
     *
     * @return Offset in minutes from UTC
     */
    fun getCurrentTimeZoneOffset(): Int {
        val tz = getDeviceTimeZone()
        return tz.getOffset(System.currentTimeMillis()) / (1000 * 60)
    }

    /**
     * Parse ISO 8601 string to timestamp in milliseconds.
     * Handles standard ISO format with 'T' separator and 'Z' timezone.
     *
     * @param isoString ISO 8601 formatted string
     * @return Unix timestamp in milliseconds, or 0 if parsing fails
     */
    fun parseISO8601ToMillis(isoString: String): Long =
        try {
            val sdf = SimpleDateFormat(FORMAT_ISO_8601, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
}
