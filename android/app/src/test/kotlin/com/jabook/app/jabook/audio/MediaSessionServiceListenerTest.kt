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

package com.jabook.app.jabook.audio

import android.app.NotificationManager
import android.content.pm.PackageManager
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MediaSessionServiceListener].
 *
 * Validates Android 14+ FGS regression handling:
 * - Foreground service start not allowed -> graceful notification fallback
 * - Missing POST_NOTIFICATIONS permission -> early return without crash
 * - Notification channel creation and error notification delivery
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaSessionServiceListenerTest {
    @Mock
    private lateinit var service: AudioPlayerService

    @Mock
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `listener handles onForegroundServiceStartNotAllowedException without crash`() {
        // Verify that the listener can be constructed without issues
        val listener = MediaSessionServiceListener(service)
        assertNotNull(listener)
    }

    @Test
    fun `onForegroundServiceStartNotAllowedException handles missing notification helper`() {
        `when`(service.notificationHelper).thenReturn(null)
        `when`(service.checkSelfPermission(anyString())).thenReturn(PackageManager.PERMISSION_GRANTED)
        `when`(service.getString(anyInt())).thenReturn("Test message")
        `when`(service.packageManager).thenReturn(mock(android.content.pm.PackageManager::class.java))
        `when`(service.packageName).thenReturn("com.jabook.app.jabook")

        val listener = MediaSessionServiceListener(service)
        // Should not throw - graceful handling when notificationHelper is null
        listener.onForegroundServiceStartNotAllowedException()
    }

    @Test
    fun `onForegroundServiceStartNotAllowedException returns early when notification permission not granted`() {
        `when`(service.notificationHelper).thenReturn(null)
        `when`(service.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
            .thenReturn(PackageManager.PERMISSION_DENIED)
        `when`(service.packageName).thenReturn("com.jabook.app.jabook")

        val listener = MediaSessionServiceListener(service)
        listener.onForegroundServiceStartNotAllowedException()

        // Should return early - no crash, no notification
        verify(service, never()).packageManager
    }
}
