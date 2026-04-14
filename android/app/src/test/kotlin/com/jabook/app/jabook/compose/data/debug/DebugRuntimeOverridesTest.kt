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

package com.jabook.app.jabook.compose.data.debug

import com.jabook.app.jabook.compose.data.network.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugRuntimeOverridesTest {
    @Test
    fun `resolveNetworkType returns actual in auto mode`() {
        val overrides = DebugRuntimeOverrides()

        assertEquals(NetworkType.ETHERNET, overrides.resolveNetworkType(NetworkType.ETHERNET))
    }

    @Test
    fun `resolveNetworkType applies forced modes`() {
        val overrides = DebugRuntimeOverrides()

        overrides.setNetworkOverrideMode(DebugNetworkOverrideMode.FORCE_OFFLINE)
        assertEquals(NetworkType.NONE, overrides.resolveNetworkType(NetworkType.WIFI))

        overrides.setNetworkOverrideMode(DebugNetworkOverrideMode.FORCE_METERED)
        assertEquals(NetworkType.CELLULAR, overrides.resolveNetworkType(NetworkType.WIFI))

        overrides.setNetworkOverrideMode(DebugNetworkOverrideMode.FORCE_UNMETERED_WIFI)
        assertEquals(NetworkType.WIFI, overrides.resolveNetworkType(NetworkType.CELLULAR))
    }

    @Test
    fun `force low storage flag toggles`() {
        val overrides = DebugRuntimeOverrides()

        assertFalse(overrides.isForceLowStorageEnabled())
        overrides.setForceLowStorage(true)
        assertTrue(overrides.isForceLowStorageEnabled())
    }
}
