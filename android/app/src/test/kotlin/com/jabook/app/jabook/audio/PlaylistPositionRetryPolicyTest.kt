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

class PlaylistPositionRetryPolicyTest {
    @Test
    fun `shouldRetry returns true only when final index differs from target`() {
        assertThat(PlaylistPositionRetryPolicy.shouldRetry(finalIndex = 2, targetIndex = 3)).isTrue()
        assertThat(PlaylistPositionRetryPolicy.shouldRetry(finalIndex = 3, targetIndex = 3)).isFalse()
    }

    @Test
    fun `buildRetryPlan returns expected retry delays`() {
        val plan = PlaylistPositionRetryPolicy.buildRetryPlan()

        assertThat(plan.seekDefaultDelayMs).isEqualTo(300L)
        assertThat(plan.seekTargetDelayMs).isEqualTo(500L)
    }
}
