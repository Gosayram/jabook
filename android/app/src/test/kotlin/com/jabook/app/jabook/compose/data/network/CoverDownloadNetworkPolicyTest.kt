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

class CoverDownloadNetworkPolicyTest {
    @Test
    fun `returns false when no network`() {
        assertFalse(CoverDownloadNetworkPolicy.canAutoLoadCovers(NetworkType.NONE, allowOnCellular = true))
    }

    @Test
    fun `returns false on cellular when disabled in settings`() {
        assertFalse(CoverDownloadNetworkPolicy.canAutoLoadCovers(NetworkType.CELLULAR, allowOnCellular = false))
    }

    @Test
    fun `returns true on wifi and ethernet`() {
        assertTrue(CoverDownloadNetworkPolicy.canAutoLoadCovers(NetworkType.WIFI, allowOnCellular = false))
        assertTrue(CoverDownloadNetworkPolicy.canAutoLoadCovers(NetworkType.ETHERNET, allowOnCellular = false))
    }

    @Test
    fun `returns true on cellular when enabled in settings`() {
        assertTrue(CoverDownloadNetworkPolicy.canAutoLoadCovers(NetworkType.CELLULAR, allowOnCellular = true))
    }
}
