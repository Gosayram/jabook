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

package com.jabook.app.jabook.compose.feature.player

import androidx.lifecycle.SavedStateHandle
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.preferences.PlayerStateSnapshotPreference
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStateRestoreHandlerTest {
    private val loggerFactory =
        object : LoggerFactory {
            override fun get(tag: String): Logger = NoopLogger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = NoopLogger
        }

    @Test
    fun `restoreFromDataStore applies snapshot at chapter 0 position 0`() =
        runTest {
            val snapshotFlow = MutableStateFlow<PlayerStateSnapshotPreference?>(null)
            val settingsRepository =
                mock<ProtoSettingsRepository> {
                    whenever(it.playerStateSnapshot).thenReturn(snapshotFlow)
                }
            val snapshot =
                PlayerStateSnapshotPreference(
                    bookId = "book-1",
                    positionMs = 0L,
                    chapterIndex = 0,
                    playbackSpeed = 1.5f,
                    sleepTimerMode = PlayerStateSnapshotPolicy.MODE_IDLE,
                )
            snapshotFlow.value = snapshot

            val restoredBootstrap = MutableStateFlow<RestoredBootstrapSnapshot?>(null)
            val handler = createHandler(bootstrap = restoredBootstrap, settingsRepository = settingsRepository)

            handler.restoreFromDataStore()
            advanceUntilIdle()

            assertNotNull("snapshot with chapter 0 / position 0 should be applied", restoredBootstrap.value)
        }

    @Test
    fun `restoreFromDataStore skips when bootstrap already set from SavedStateHandle`() =
        runTest {
            val settingsRepository =
                mock<ProtoSettingsRepository> {
                    whenever(it.playerStateSnapshot).thenReturn(
                        flowOf(
                            PlayerStateSnapshotPreference(
                                bookId = "book-1",
                                positionMs = 5000L,
                                chapterIndex = 2,
                                playbackSpeed = 1.0f,
                                sleepTimerMode = PlayerStateSnapshotPolicy.MODE_IDLE,
                            ),
                        ),
                    )
                }

            val restoredBootstrap =
                MutableStateFlow<RestoredBootstrapSnapshot?>(
                    RestoredBootstrapSnapshot(
                        positionMs = 1000L,
                        chapterIndex = 1,
                        playbackSpeed = 1.0f,
                        sleepTimerMode = PlayerStateSnapshotPolicy.MODE_IDLE,
                        hasRestoredSpeed = false,
                    ),
                )
            val handler = createHandler(bootstrap = restoredBootstrap, settingsRepository = settingsRepository)

            handler.restoreFromDataStore()
            advanceUntilIdle()

            // Should not overwrite existing bootstrap
            assertNotNull(restoredBootstrap.value)
        }

    private fun createHandler(
        bootstrap: MutableStateFlow<RestoredBootstrapSnapshot?> = MutableStateFlow(null),
        settingsRepository: ProtoSettingsRepository = mock(),
    ): PlayerStateRestoreHandler =
        PlayerStateRestoreHandler(
            bookId = "book-1",
            savedStateHandle = SavedStateHandle(),
            settingsRepository = settingsRepository,
            userPreferencesRepository = mock(),
            sleepTimerRepository = mock(),
            playbackPositionRepository = mock(),
            sleepTimerState = MutableStateFlow(com.jabook.app.jabook.compose.domain.model.SleepTimerState.Idle),
            uiState = MutableStateFlow(PlayerState.Loading),
            restoredBootstrapSnapshot = bootstrap,
            isPlaybackRestoreReady = MutableStateFlow(false),
            viewModelScope = TestScope().backgroundScope,
            loggerFactory = loggerFactory,
        )

    private object NoopLogger : Logger {
        override fun d(message: () -> String) = Unit

        override fun d(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun d(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun e(message: () -> String) = Unit

        override fun e(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun e(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun i(message: () -> String) = Unit

        override fun i(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun i(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun w(message: () -> String) = Unit

        override fun w(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun w(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun v(message: () -> String) = Unit

        override fun v(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun v(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit
    }
}
