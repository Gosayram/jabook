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

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PermissionManagerTest {
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `getStorageAccessRequest returns full file system mode on Android 11 plus`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PermissionManager(context)

        val request = manager.getStorageAccessRequest()

        assertEquals(StorageAccessMode.FULL_FILE_SYSTEM, request.mode)
        assertTrue(request.runtimePermissions.isEmpty())
        assertNotNull(request.intent)
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, request.intent?.action)
        assertEquals("package:${context.packageName}", request.intent?.dataString)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `getStorageAccessRequest remains full file system mode for supported sdk range`() {
        val manager = PermissionManager(ApplicationProvider.getApplicationContext())

        val request = manager.getStorageAccessRequest()

        assertEquals(StorageAccessMode.FULL_FILE_SYSTEM, request.mode)
        assertTrue(request.runtimePermissions.isEmpty())
        assertNotNull(request.intent)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `getAppSettingsIntent always opens application details settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PermissionManager(context)

        val intent = manager.getAppSettingsIntent()

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }
}
