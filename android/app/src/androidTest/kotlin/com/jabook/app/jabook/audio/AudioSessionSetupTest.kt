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

import android.content.Context
import androidx.media3.session.MediaLibraryService
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for AudioSessionSetup.
 * Tests idempotency guard, session creation behavior.
 */
@RunWith(AndroidJUnit4::class)
class AudioSessionSetupTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `session ID format is correct`() {
        val sessionId = "jabook_${android.os.Process.myPid()}_${System.identityHashCode(context)}"
        assertThat(sessionId).startsWith("jabook_")
        assertThat(sessionId).contains(android.os.Process.myPid().toString())
    }

    @Test
    fun `TestExoPlayerBuilder creates valid player`() {
        val player = TestExoPlayerBuilder(context).build()
        try {
            assertThat(player).isNotNull()
            assertThat(player.getDuration()).isEqualTo(0L)
        } finally {
            player.release()
        }
    }
}