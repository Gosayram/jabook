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

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorPolicyTest {
    @Test
    fun `network failed with retries left resolves to retry`() {
        val resolution =
            PlaybackErrorPolicy.resolve(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                hasRetriesLeft = true,
                canSkipTrack = true,
                fallbackMessage = null,
            )

        assertEquals(PlaybackRecoveryAction.RETRY, resolution.action)
        assertTrue(resolution.userMessage.contains("retrying", ignoreCase = true))
    }

    @Test
    fun `file not found without skip budget resolves to rescan`() {
        val resolution =
            PlaybackErrorPolicy.resolve(
                errorCode = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                hasRetriesLeft = false,
                canSkipTrack = false,
                fallbackMessage = null,
            )

        assertEquals(PlaybackRecoveryAction.RESCAN_LIBRARY, resolution.action)
        assertTrue(resolution.userMessage.contains("missing", ignoreCase = true))
    }

    @Test
    fun `decoder format error with skip budget resolves to skip track`() {
        val resolution =
            PlaybackErrorPolicy.resolve(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                hasRetriesLeft = false,
                canSkipTrack = true,
                fallbackMessage = null,
            )

        assertEquals(PlaybackRecoveryAction.SKIP_TRACK, resolution.action)
    }

    @Test
    fun `unknown error falls back to message and no recovery action`() {
        val resolution =
            PlaybackErrorPolicy.resolve(
                errorCode = 999_999,
                hasRetriesLeft = false,
                canSkipTrack = false,
                fallbackMessage = "boom",
            )

        assertEquals(PlaybackRecoveryAction.NONE, resolution.action)
        assertTrue(resolution.userMessage.contains("boom"))
    }
}
