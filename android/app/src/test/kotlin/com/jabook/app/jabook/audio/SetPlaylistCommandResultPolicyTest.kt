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

import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetPlaylistCommandResultPolicyTest {
    @Test
    fun `success returns success result code`() {
        val result = SetPlaylistCommandResultPolicy.success()

        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    }

    @Test
    fun `badValue returns bad value error with reason`() {
        val result = SetPlaylistCommandResultPolicy.badValue()

        assertEquals(SessionError.ERROR_BAD_VALUE, result.resultCode)
        assertEquals("bad_value", result.extras.getString(SetPlaylistCommandResultPolicy.EXTRA_ERROR_REASON))
    }

    @Test
    fun `timeout returns unknown error with timeout reason`() {
        val result = SetPlaylistCommandResultPolicy.timeout()

        assertEquals(SessionError.ERROR_UNKNOWN, result.resultCode)
        assertEquals("timeout", result.extras.getString(SetPlaylistCommandResultPolicy.EXTRA_ERROR_REASON))
    }

    @Test
    fun `callbackFailed returns unknown error with callback reason`() {
        val result = SetPlaylistCommandResultPolicy.callbackFailed()

        assertEquals(SessionError.ERROR_UNKNOWN, result.resultCode)
        assertEquals("callback_failed", result.extras.getString(SetPlaylistCommandResultPolicy.EXTRA_ERROR_REASON))
    }

    @Test
    fun `exception includes exception marker and type`() {
        val result = SetPlaylistCommandResultPolicy.exception(IllegalStateException("boom"))

        assertEquals(SessionError.ERROR_UNKNOWN, result.resultCode)
        val reason = result.extras.getString(SetPlaylistCommandResultPolicy.EXTRA_ERROR_REASON).orEmpty()
        assertTrue(reason.startsWith("exception:"))
        assertTrue(reason.contains("IllegalStateException"))
    }
}
