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

import android.content.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class TorrentDownloadItemTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    public fun `formatBytes returns bytes for small values`() {
        val result = formatBytes(512L, resources)
        assertTrue(result.contains("512"))
    }

    @Test
    public fun `formatBytes returns KB for kilobyte values`() {
        val result = formatBytes(2048L, resources)
        assertTrue(result.contains("KB") || result.contains("kb"))
    }

    @Test
    public fun `formatBytes returns MB for megabyte values`() {
        val result = formatBytes(5_000_000L, resources)
        assertTrue(result.contains("MB") || result.contains("mb"))
    }

    @Test
    public fun `formatBytes returns GB for gigabyte values`() {
        val result = formatBytes(5_000_000_000L, resources)
        assertTrue(result.contains("GB") || result.contains("gb"))
    }

    @Test
    public fun `formatBytes handles zero bytes`() {
        val result = formatBytes(0L, resources)
        assertTrue(result.contains("0"))
    }

    @Test
    public fun `formatBytes coerces negative to zero`() {
        val result = formatBytes(-100L, resources)
        assertTrue(result.contains("0"))
    }
}