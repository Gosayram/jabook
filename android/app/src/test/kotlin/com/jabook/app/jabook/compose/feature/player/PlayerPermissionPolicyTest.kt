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

package com.jabook.app.jabook.compose.feature.player

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPermissionPolicyTest {
    @Test
    fun `entryPermissionsToRequest requests notification on android 13 plus when not granted`() {
        val result =
            PlayerPermissionPolicy.entryPermissionsToRequest(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isNotificationPermissionGranted = false,
            )

        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), result)
    }

    @Test
    fun `entryPermissionsToRequest returns empty when notification already granted`() {
        val result =
            PlayerPermissionPolicy.entryPermissionsToRequest(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isNotificationPermissionGranted = true,
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `entryPermissionsToRequest never includes record audio`() {
        val result =
            PlayerPermissionPolicy.entryPermissionsToRequest(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isNotificationPermissionGranted = false,
            )

        assertTrue(Manifest.permission.RECORD_AUDIO !in result)
    }

    @Test
    fun `entryPermissionsToRequest returns empty below android 13`() {
        val result =
            PlayerPermissionPolicy.entryPermissionsToRequest(
                sdkInt = Build.VERSION_CODES.S_V2,
                isNotificationPermissionGranted = false,
            )

        assertTrue(result.isEmpty())
    }
}
