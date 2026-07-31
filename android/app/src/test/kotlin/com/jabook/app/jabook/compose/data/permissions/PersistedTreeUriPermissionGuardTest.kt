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

package com.jabook.app.jabook.compose.data.permissions

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class PersistedTreeUriPermissionGuardTest {
    private val treeUri: Uri = mock()

    @Test
    fun `take returns false when provider rejects a stale or non-persistable grant`() {
        val guard =
            PersistedTreeUriPermissionGuard(
                takePermission = { throw SecurityException("Grant was revoked") },
                releasePermission = {},
                isPermissionPersisted = { true },
            )

        assertFalse(guard.take(treeUri))
    }

    @Test
    fun `take returns true only after the permission operation succeeds`() {
        var granted = false
        val guard =
            PersistedTreeUriPermissionGuard(
                takePermission = { granted = true },
                releasePermission = {},
                isPermissionPersisted = { granted },
            )

        assertTrue(guard.take(treeUri))
        assertTrue(granted)
    }

    @Test
    fun `release ignores a revoked grant`() {
        val guard =
            PersistedTreeUriPermissionGuard(
                takePermission = {},
                releasePermission = { throw SecurityException("Grant was already revoked") },
                isPermissionPersisted = { true },
            )

        assertFalse(guard.release(treeUri))
    }

    @Test
    fun `take rejects a URI that is not listed among persisted grants`() {
        val guard =
            PersistedTreeUriPermissionGuard(
                takePermission = {},
                releasePermission = {},
                isPermissionPersisted = { false },
            )

        assertFalse(guard.take(treeUri))
    }
}
