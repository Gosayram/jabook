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

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField

/**
 * Centralized DateTime formatting utility.
 * Uses java.time (thread-safe, immutable) with device timezone.
 * Formats conform to GOST 7.64-90 standard.
 */
public object DateTimeFormatter {
    private val deviceZone: ZoneId =
        try {
            ZoneId.systemDefault()
        } catch (e: Exception) {
            ZoneId.of("UTC")
        }

    private val gostFormatter: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .appendPattern("dd.MM.yyyy HH:mm")
            .toFormatter()

    private val gostWithSecondsFormatter: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .appendPattern("dd.MM.yyyy HH:mm:ss")
            .toFormatter()

    private val iso8601Formatter: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 4, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral('Z')
            .toFormatter()
            .withZone(ZoneId.of("UTC"))

    private val filenameFormatter: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .appendPattern("yyyyMMdd_HHmmss")
            .toFormatter()

    /**
     * Format timestamp to GOST 7.64-90 format (DD.MM.YYYY HH:MM).
     * Uses device timezone.
     */
    public fun formatGOST(millis: Long): String {
        val instant = Instant.ofEpochMilli(millis)
        return gostFormatter.withZone(deviceZone).format(instant)
    }

    /**
     * Format timestamp to GOST 7.64-90 format with seconds (DD.MM.YYYY HH:MM:SS).
     * Uses device timezone.
     */
    public fun formatGOSTWithSeconds(millis: Long): String {
        val instant = Instant.ofEpochMilli(millis)
        return gostWithSecondsFormatter.withZone(deviceZone).format(instant)
    }

    public fun formatCurrentGOST(): String = formatGOST(System.currentTimeMillis())

    public fun formatCurrentGOSTWithSeconds(): String = formatGOSTWithSeconds(System.currentTimeMillis())

    /**
     * Format timestamp to ISO 8601 format for backup files.
     * Always uses UTC for consistency across devices.
     */
    public fun formatISO8601(millis: Long): String {
        val instant = Instant.ofEpochMilli(millis)
        return iso8601Formatter.format(instant)
    }

    public fun formatCurrentISO8601(): String = formatISO8601(System.currentTimeMillis())

    /**
     * Format timestamp for filename (yyyyMMdd_HHmmss).
     * Uses device timezone.
     */
    public fun formatForFilename(millis: Long): String {
        val instant = Instant.ofEpochMilli(millis)
        return filenameFormatter.withZone(deviceZone).format(instant)
    }

    public fun formatCurrentForFilename(): String = formatForFilename(System.currentTimeMillis())

    public fun getCurrentTimeZoneId(): String = deviceZone.id

    public fun getCurrentTimeZoneOffset(): Int {
        val offset = deviceZone.getRules().getOffset(Instant.now())
        return offset.totalSeconds / 60
    }

    /**
     * Parse ISO 8601 string to timestamp in milliseconds.
     * Handles standard ISO format with 'T' separator and 'Z' timezone,
     * including fractional seconds (e.g. [Instant.now] output).
     */
    public fun parseISO8601ToMillis(isoString: String): Long =
        try {
            Instant.from(iso8601Formatter.parse(isoString)).toEpochMilli()
        } catch (e: Exception) {
            try {
                Instant.parse(isoString).toEpochMilli()
            } catch (e: Exception) {
                0L
            }
        }
}
