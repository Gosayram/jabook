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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistUriResolutionPolicyTest {
    @Test
    fun `resolve keeps remote uri and does not warn`() {
        val resolved =
            PlaylistUriResolutionPolicy.resolve(
                path = "https://example.com/book.mp3",
                localPathExists = { false },
            )

        assertThat(resolved.uri.scheme).isEqualTo("https")
        assertThat(resolved.shouldWarnMissingLocalPath).isFalse()
    }

    @Test
    fun `resolve wraps local path as file uri and warns when file is missing`() {
        val resolved =
            PlaylistUriResolutionPolicy.resolve(
                path = "/storage/emulated/0/Books/book.mp3",
                localPathExists = { false },
            )

        assertThat(resolved.uri.scheme).isEqualTo("file")
        assertThat(resolved.shouldWarnMissingLocalPath).isTrue()
    }

    @Test
    fun `resolve wraps local path as file uri and does not warn when file exists`() {
        val resolved =
            PlaylistUriResolutionPolicy.resolve(
                path = "/storage/emulated/0/Books/book.mp3",
                localPathExists = { true },
            )

        assertThat(resolved.uri.scheme).isEqualTo("file")
        assertThat(resolved.shouldWarnMissingLocalPath).isFalse()
    }
}
