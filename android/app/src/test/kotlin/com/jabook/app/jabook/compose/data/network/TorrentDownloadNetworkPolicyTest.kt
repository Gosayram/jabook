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

package com.jabook.app.jabook.compose.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentDownloadNetworkPolicyTest {
    @Test
    fun `wifi-only off never pauses downloads`() {
        NetworkType.entries.forEach { type ->
            assertFalse(
                TorrentDownloadNetworkPolicy.shouldPauseForNetwork(
                    wifiOnlyEnabled = false,
                    networkType = type,
                ),
            )
        }
    }

    @Test
    fun `wifi and ethernet are allowed when wifi-only is on`() {
        assertFalse(TorrentDownloadNetworkPolicy.shouldPauseForNetwork(true, NetworkType.WIFI))
        assertFalse(TorrentDownloadNetworkPolicy.shouldPauseForNetwork(true, NetworkType.ETHERNET))
    }

    @Test
    fun `cellular none and unknown are restricted when wifi-only is on`() {
        assertTrue(TorrentDownloadNetworkPolicy.shouldPauseForNetwork(true, NetworkType.CELLULAR))
        assertTrue(TorrentDownloadNetworkPolicy.shouldPauseForNetwork(true, NetworkType.NONE))
        assertTrue(TorrentDownloadNetworkPolicy.shouldPauseForNetwork(true, NetworkType.UNKNOWN))
    }
}
