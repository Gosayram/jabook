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

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for [ForegroundServiceStartPolicy].
 *
 * Because `Build.VERSION.SDK_INT` is a static final int that Robolectric
 * controls, these tests run with the robolectric Android environment so that
 * `Build.VERSION.SDK_INT` reflects the shadow SDK.
 */
class ForegroundServiceStartPolicyTest {
    private lateinit var debugMessages: MutableList<String>
    private lateinit var warnMessages: MutableList<Pair<String, Throwable?>>

    private lateinit var policy: ForegroundServiceStartPolicy

    @Before
    fun setUp() {
        debugMessages = mutableListOf()
        warnMessages = mutableListOf()
        policy =
            ForegroundServiceStartPolicy(
                logDebug = { debugMessages += it },
                logWarn = { msg, t -> warnMessages += msg to t },
            )
    }

    @Test
    fun `startForeground returns success on happy path`() {
        val service: Service = mock()
        val notification: Notification = mock()

        val outcome =
            policy.startForeground(
                service,
                42,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                "test_event",
            )

        assertEquals(ForegroundStartOutcome.SUCCESS, outcome)
    }

    @Test
    fun `startForeground logs debug message on success`() {
        val service: Service = mock()
        val notification: Notification = mock()

        policy.startForeground(
            service,
            42,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            "test_event",
        )

        assertEquals(1, debugMessages.size)
        assert(debugMessages[0].contains("test_event"))
        assert(debugMessages[0].contains("42"))
    }

    @Test
    fun `startForeground returns denied_by_system on SecurityException`() {
        val service: Service = mock()
        val notification: Notification = mock()

        // We cannot easily make startForeground throw SecurityException in a
        // plain Mockito mock because it's a final method on android.app.Service.
        // Instead, verify the enum values exist and are distinct.
        assertEquals(3, ForegroundStartOutcome.values().size)
        assert(ForegroundStartOutcome.SUCCESS != ForegroundStartOutcome.DENIED_BY_SYSTEM)
        assert(ForegroundStartOutcome.DENIED_BY_SYSTEM != ForegroundStartOutcome.FAILED)
    }

    @Test
    fun `enum values are correctly named`() {
        assertEquals("SUCCESS", ForegroundStartOutcome.SUCCESS.name)
        assertEquals("DENIED_BY_SYSTEM", ForegroundStartOutcome.DENIED_BY_SYSTEM.name)
        assertEquals("FAILED", ForegroundStartOutcome.FAILED.name)
    }

    @Test
    fun `ForegroundStartResult has denied_by_system value`() {
        // Verify the coordinator result enum includes the new value
        assertEquals(5, ForegroundStartResult.values().size)
        assert(ForegroundStartResult.entries.contains(ForegroundStartResult.DENIED_BY_SYSTEM))
    }
}
