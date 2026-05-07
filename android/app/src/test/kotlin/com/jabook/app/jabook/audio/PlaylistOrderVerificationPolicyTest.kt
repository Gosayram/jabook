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

class PlaylistOrderVerificationPolicyTest {
    @Test
    fun `decide returns verification delay when generation is current`() {
        val decision = PlaylistOrderVerificationPolicy.decide(isCurrentGeneration = true)

        assertThat(decision.shouldProceed).isTrue()
        assertThat(decision.waitBeforeVerificationMs).isEqualTo(2_000L)
    }

    @Test
    fun `decide returns no-op decision when generation is stale`() {
        val decision = PlaylistOrderVerificationPolicy.decide(isCurrentGeneration = false)

        assertThat(decision.shouldProceed).isFalse()
        assertThat(decision.waitBeforeVerificationMs).isEqualTo(0L)
    }
}
