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
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SleepTimerManagerTest {
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        timerPrefs().edit().clear().apply()
    }

    @After
    fun tearDown() {
        timerPrefs().edit().clear().apply()
        Dispatchers.resetMain()
    }

    @Test
    fun `restoreTimerState keeps paused remaining time after process death`() =
        runTest(testDispatcher) {
            val player = mock<ExoPlayer>()
            whenever(player.isPlaying).thenReturn(false)

            val manager =
                SleepTimerManager(
                    context = context,
                    packageName = context.packageName,
                    playerServiceScope = this,
                    getActivePlayer = { player },
                    sendBroadcast = {},
                )

            manager.setSleepTimerMinutes(2)

            // Simulate stale absolute end time from previous process;
            // paused remaining time must be the source of truth.
            timerPrefs()
                .edit()
                .putLong(SleepTimerPersistence.KEY_END_TIME, System.currentTimeMillis() - 10_000L)
                .apply()

            val restoredManager =
                SleepTimerManager(
                    context = context,
                    packageName = context.packageName,
                    playerServiceScope = this,
                    getActivePlayer = { player },
                    sendBroadcast = {},
                )

            restoredManager.restoreTimerState()

            val remainingSeconds = restoredManager.getSleepTimerRemainingSeconds()
            assertNotNull(remainingSeconds)
            assertTrue(remainingSeconds in 119..120)
            assertTrue(restoredManager.isSleepTimerActive())
        }

    @Test
    fun `triggerShakeForTesting ignores duplicate shakes in debounce window`() =
        runTest(testDispatcher) {
            val player = mock<ExoPlayer>()
            whenever(player.isPlaying).thenReturn(false)

            val manager =
                SleepTimerManager(
                    context = context,
                    packageName = context.packageName,
                    playerServiceScope = this,
                    getActivePlayer = { player },
                    sendBroadcast = {},
                )

            manager.setSleepTimerMinutes(1)
            val beforeExtension = manager.getSleepTimerRemainingSeconds()
            assertEquals(60, beforeExtension)

            manager.triggerShakeForTesting(nowMillis = 1_000L)
            advanceUntilIdle()
            val firstExtension = manager.getSleepTimerRemainingSeconds()

            manager.triggerShakeForTesting(nowMillis = 1_500L) // within 2s debounce
            advanceUntilIdle()
            val secondExtension = manager.getSleepTimerRemainingSeconds()

            assertEquals(360, firstExtension)
            assertEquals(firstExtension, secondExtension)
        }

    @Test
    fun `restoreTimerState restores end of chapter mode`() =
        runTest(testDispatcher) {
            timerPrefs()
                .edit()
                .putLong(SleepTimerPersistence.KEY_END_TIME, 0L)
                .putBoolean(SleepTimerPersistence.KEY_END_OF_CHAPTER, true)
                .putBoolean(SleepTimerPersistence.KEY_PAUSED, false)
                .putLong(SleepTimerPersistence.KEY_PAUSED_REMAINING_MILLIS, SleepTimerPersistence.NO_REMAINING_MILLIS)
                .apply()

            val player = mock<ExoPlayer>()
            whenever(player.isPlaying).thenReturn(false)

            val manager =
                SleepTimerManager(
                    context = context,
                    packageName = context.packageName,
                    playerServiceScope = this,
                    getActivePlayer = { player },
                    sendBroadcast = {},
                )

            manager.restoreTimerState()

            assertTrue(manager.isSleepTimerActive())
            assertEquals(null, manager.getSleepTimerRemainingSeconds())
        }

    @Test
    fun `restored paused timer resumes and updates persisted paused flag on playback resume`() =
        runTest(testDispatcher) {
            val player = mock<ExoPlayer>()
            whenever(player.isPlaying).thenReturn(false)

            val creator =
                SleepTimerManager(
                    context = context,
                    packageName = context.packageName,
                    playerServiceScope = this,
                    getActivePlayer = { player },
                    sendBroadcast = {},
                )
            creator.setSleepTimerMinutes(2)

            val manager =
                SleepTimerManager(
                    context = context,
                    packageName = context.packageName,
                    playerServiceScope = this,
                    getActivePlayer = { player },
                    sendBroadcast = {},
                )
            manager.restoreTimerState()

            val listenerCaptor = argumentCaptor<androidx.media3.common.Player.Listener>()
            verify(player, atLeastOnce()).addListener(listenerCaptor.capture())
            val listener = listenerCaptor.lastValue

            listener.onIsPlayingChanged(true)
            advanceUntilIdle()

            val pausedFlag = timerPrefs().getBoolean(SleepTimerPersistence.KEY_PAUSED, true)
            assertTrue(!pausedFlag)
            assertNotNull(manager.getSleepTimerRemainingSeconds())
        }

    private fun timerPrefs() = context.getSharedPreferences(SleepTimerPersistence.PREFS_NAME, Context.MODE_PRIVATE)
}
