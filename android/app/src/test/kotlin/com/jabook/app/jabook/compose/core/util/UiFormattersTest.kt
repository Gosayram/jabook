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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class UiFormattersTest {
    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun formatDuration_zero() {
        assertEquals("0:00", UiFormatters.formatDuration(0L))
    }

    @Test
    fun formatDuration_secondsOnly() {
        assertEquals("0:45", UiFormatters.formatDuration(45_000L))
    }

    @Test
    fun formatDuration_minutesAndSeconds() {
        assertEquals("5:30", UiFormatters.formatDuration(330_000L))
    }

    @Test
    fun formatDuration_hoursMinutesSeconds() {
        assertEquals("1:23:45", UiFormatters.formatDuration(5_025_000L))
    }

    @Test
    fun formatDuration_negative_clampsToZero() {
        // Duration.toComponents on a clamped-to-zero value must not emit negative parts
        assertEquals("0:00", UiFormatters.formatDuration(-5_000L))
    }

    @Test
    fun formatDurationCompact_seconds() {
        assertEquals("30s", UiFormatters.formatDurationCompact(30_000L))
    }

    @Test
    fun formatDurationCompact_minutes() {
        assertEquals("5m", UiFormatters.formatDurationCompact(300_000L))
    }

    @Test
    fun formatDurationCompact_hours() {
        assertEquals("1h 30m", UiFormatters.formatDurationCompact(5_400_000L))
    }

    @Test
    fun formatDurationCompact_zero() {
        assertEquals("0s", UiFormatters.formatDurationCompact(0L))
    }

    @Test
    fun formatTimeRemaining_hoursAndMinutes() {
        assertEquals("-1:30", UiFormatters.formatTimeRemaining(5_400_000L))
    }

    @Test
    fun formatTimeRemaining_minutesOnly() {
        assertEquals("-5:30", UiFormatters.formatTimeRemaining(330_000L))
    }

    @Test
    fun formatTimeRemaining_zero_returnsEmpty() {
        assertEquals("", UiFormatters.formatTimeRemaining(0L))
    }

    @Test
    fun formatSpeed_wholeNumber() {
        assertEquals("1x", UiFormatters.formatSpeed(1.0f))
    }

    @Test
    fun formatSpeed_decimal() {
        assertEquals("1.5x", UiFormatters.formatSpeed(1.5f))
    }

    @Test
    fun formatSpeed_twoX() {
        assertEquals("2x", UiFormatters.formatSpeed(2.0f))
    }

    @Test
    fun formatSpeedDisplay() {
        assertEquals("1.50x", UiFormatters.formatSpeedDisplay(1.5f))
    }

    @Test
    fun formatFileSize_bytes() {
        assertEquals("500 B", UiFormatters.formatFileSize(500L))
    }

    @Test
    fun formatFileSize_kilobytes() {
        assertEquals("512 KB", UiFormatters.formatFileSize(524_288L))
    }

    @Test
    fun formatFileSize_megabytes() {
        assertEquals("1.5 MB", UiFormatters.formatFileSize(1_572_864L))
    }

    @Test
    fun formatFileSize_gigabytes() {
        assertEquals("2.0 GB", UiFormatters.formatFileSize(2_147_483_648L))
    }

    @Test
    fun formatFileSize_zeroBytes() {
        assertEquals("0 B", UiFormatters.formatFileSize(0L))
    }

    @Test
    fun formatFileSize_negative_coercesToZero() {
        assertEquals("0 B", UiFormatters.formatFileSize(-100L))
    }

    @Test
    fun formatSpeedBytes() {
        val result = UiFormatters.formatSpeedBytes(1_048_576L)
        assertTrue(result.contains("MB"))
        assertTrue(result.contains("/s"))
    }

    @Test
    fun formatPercent_zero() {
        assertEquals("0%", UiFormatters.formatPercent(0f))
    }

    @Test
    fun formatPercent_half() {
        assertEquals("50%", UiFormatters.formatPercent(0.5f))
    }

    @Test
    fun formatPercent_full() {
        assertEquals("100%", UiFormatters.formatPercent(1f))
    }

    @Test
    fun formatPercent_clampsAbove1() {
        assertEquals("100%", UiFormatters.formatPercent(1.5f))
    }

    @Test
    fun formatPercent_clampsBelow0() {
        assertEquals("0%", UiFormatters.formatPercent(-0.5f))
    }

    @Test
    fun formatChapterNumber() {
        assertEquals("3 / 10", UiFormatters.formatChapterNumber(2, 10))
    }

    // --- stripLeadingNumericPrefix ---

    @Test
    fun stripPrefix_dotSeparator() {
        assertEquals("Introduction", UiFormatters.stripLeadingNumericPrefix("1. Introduction"))
    }

    @Test
    fun stripPrefix_doubleDigitDot() {
        assertEquals("Chapter Title", UiFormatters.stripLeadingNumericPrefix("12. Chapter Title"))
    }

    @Test
    fun stripPrefix_parenthesis() {
        assertEquals("First Part", UiFormatters.stripLeadingNumericPrefix("1) First Part"))
    }

    @Test
    fun stripPrefix_dotSpace() {
        assertEquals("Beginning", UiFormatters.stripLeadingNumericPrefix("01. Beginning"))
    }

    @Test
    fun stripPrefix_noPrefix() {
        assertEquals("Just a Title", UiFormatters.stripLeadingNumericPrefix("Just a Title"))
    }

    @Test
    fun stripPrefix_emptyString() {
        assertEquals("", UiFormatters.stripLeadingNumericPrefix(""))
    }

    @Test
    fun stripPrefix_onlyNumber() {
        assertEquals("", UiFormatters.stripLeadingNumericPrefix("1."))
    }

    @Test
    fun stripPrefix_numberWithSpaces() {
        assertEquals("Title Here", UiFormatters.stripLeadingNumericPrefix("5  Title Here"))
    }
}
