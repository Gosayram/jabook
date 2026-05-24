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

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Minimal smoke tests for AudioPlayerServiceBootstrapper.
 * The bootstrapper heavily integrates with Android services and creates
 * dependencies internally, making full mocking impractical.
 * The core logic is tested via ForegroundNotificationCoordinatorTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioPlayerServiceBootstrapperTest {
    @Test
    fun `ForegroundStartResult enum has expected values`() {
        // Verify the enum contract used by bootstrapper
        val results = ForegroundStartResult.values()
        assert(results.isNotEmpty())
        assert(ForegroundStartResult.PRIMARY_STARTED in results)
        assert(ForegroundStartResult.FALLBACK_STARTED in results)
        assert(ForegroundStartResult.FAILED in results)
        assert(ForegroundStartResult.SKIPPED in results)
        assert(ForegroundStartResult.DENIED_BY_SYSTEM in results)
    }

    @Test
    fun `ForegroundStartResult values are distinct`() {
        val results = ForegroundStartResult.values().toList()
        // Verify no duplicates
        assert(results.size == results.toSet().size)
    }
}
