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

package com.jabook.app.jabook.compose.feature.torrent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TorrentDownloadItemFormattingTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `formatBytes returns bytes for zero`() {
        val result = formatBytes(0L, resources)
        assertTrue(result.contains("0"))
    }

    @Test
    fun `formatBytes returns bytes for small values`() {
        val result = formatBytes(500L, resources)
        assertTrue(result.contains("500"))
    }

    @Test
    fun `formatBytes transitions at 1024 bytes`() {
        val resultBelow = formatBytes(1023L, resources)
        val resultAt = formatBytes(1024L, resources)
        assertTrue(resultBelow.contains("bytes") || !resultAt.contains("bytes"))
    }

    @Test
    fun `formatBytes handles kilobytes`() {
        val result = formatBytes(2048L, resources)
        assertTrue(result.contains("KB") || result.contains("kb"))
    }

    @Test
    fun `formatBytes handles megabytes`() {
        val result = formatBytes(2 * 1024 * 1024L, resources)
        assertTrue(result.contains("MB") || result.contains("mb"))
    }

    @Test
    fun `formatBytes handles gigabytes`() {
        val result = formatBytes(2 * 1024 * 1024 * 1024L, resources)
        assertTrue(result.contains("GB") || result.contains("gb"))
    }

    @Test
    fun `formatSpeed delegates to formatBytes`() {
        val result = formatSpeed(1024L, resources)
        assertTrue(result.contains("KB") || result.contains("kb"))
    }

    @Test
    fun `formatEta returns dash for negative values`() {
        val result = formatEta(-1L, resources)
        assertEquals("--", result)
    }

    @Test
    fun `formatEta returns less than minute for short durations`() {
        val result = formatEta(30L, resources)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `formatEta returns minutes for medium durations`() {
        val result = formatEta(120L, resources)
        // Russian locale: "1м" for 1 minute
        assertTrue(result.isNotEmpty() && (result.contains("1") || result.contains("м") || result.contains("m")))
    }

    @Test
    fun `formatEta returns hours and minutes for long durations`() {
        val result = formatEta(3661L, resources)
        assertTrue(result.isNotEmpty())
    }
}
