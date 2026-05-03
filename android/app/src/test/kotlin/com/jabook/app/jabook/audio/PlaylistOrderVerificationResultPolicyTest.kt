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

class PlaylistOrderVerificationResultPolicyTest {
    @Test
    fun `evaluate returns size mismatch when expected and actual sizes differ`() {
        val result =
            PlaylistOrderVerificationResultPolicy.evaluate(
                expectedPaths = listOf("a", "b", "c"),
                actualPaths = listOf("a", "b"),
            )

        assertThat(result.sizeMatches).isFalse()
        assertThat(result.expectedSize).isEqualTo(3)
        assertThat(result.actualSize).isEqualTo(2)
        assertThat(result.mismatchCount).isEqualTo(0)
    }

    @Test
    fun `evaluate returns no mismatches when order is exact`() {
        val result =
            PlaylistOrderVerificationResultPolicy.evaluate(
                expectedPaths = listOf("a", "b", "c"),
                actualPaths = listOf("a", "b", "c"),
            )

        assertThat(result.sizeMatches).isTrue()
        assertThat(result.mismatchCount).isEqualTo(0)
        assertThat(result.mismatches).isEmpty()
    }

    @Test
    fun `evaluate returns mismatch entries with indices and paths`() {
        val result =
            PlaylistOrderVerificationResultPolicy.evaluate(
                expectedPaths = listOf("a", "b", "c"),
                actualPaths = listOf("a", "x", null),
            )

        assertThat(result.sizeMatches).isTrue()
        assertThat(result.mismatchCount).isEqualTo(2)
        assertThat(result.mismatches[0].index).isEqualTo(1)
        assertThat(result.mismatches[0].expectedPath).isEqualTo("b")
        assertThat(result.mismatches[0].actualPath).isEqualTo("x")
        assertThat(result.mismatches[1].index).isEqualTo(2)
        assertThat(result.mismatches[1].expectedPath).isEqualTo("c")
        assertThat(result.mismatches[1].actualPath).isNull()
    }
}
