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

import com.jabook.app.jabook.audio.processors.VolumeBoostLevel
import com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlayerReducerTest {
    @Test
    fun `reduce keeps loading state when play intent received`() {
        val state = PlayerState.Loading

        val reduced = PlayerReducer.reduce(state, PlayerIntent.Play)

        assertSame(state, reduced)
    }

    @Test
    fun `reduce clamps seek intent to chapter duration in active state`() {
        val state =
            PlayerState.Active(
                book = Book.preview().copy(id = "book-1", title = "Title", author = "Author"),
                chapters =
                    listOf(
                        Chapter.preview().copy(
                            id = "c1",
                            bookId = "book-1",
                            title = "Chapter 1",
                            chapterIndex = 0,
                            fileIndex = 0,
                            duration = 1.minutes,
                            position = 0.seconds,
                        ),
                    ).toImmutableList(),
                isPlaying = true,
                currentPosition = 10_000L,
                currentChapterIndex = 0,
                currentChapter = Chapter.preview().copy(id = "c1", bookId = "book-1", duration = 1.minutes),
                rewindInterval = 10,
                forwardInterval = 30,
                playbackSpeed = 1.0f,
                sleepTimerMode = PlayerSleepTimerMode.IDLE,
                sleepTimerRemainingSeconds = null,
                chapterRepeatMode = ChapterRepeatMode.OFF,
            )

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SeekTo(positionMs = 120_000L))

        require(reduced is PlayerState.Active)
        assertEquals(60_000L, reduced.currentPosition)
    }

    @Test
    fun `reduce transitions to error state on report error intent`() {
        val reduced = PlayerReducer.reduce(PlayerState.Loading, PlayerIntent.ReportError("boom"))

        require(reduced is PlayerState.Error)
        assertEquals("boom", reduced.message)
    }

    @Test
    fun `reduce toggles play state with toggle intent`() {
        val paused = activeStateTemplate().copy(isPlaying = false)
        val playing = activeStateTemplate().copy(isPlaying = true)

        val pausedToPlaying = PlayerReducer.reduce(paused, PlayerIntent.TogglePlayPause)
        val playingToPaused = PlayerReducer.reduce(playing, PlayerIntent.TogglePlayPause)

        require(pausedToPlaying is PlayerState.Active)
        require(playingToPaused is PlayerState.Active)
        assertTrue(pausedToPlaying.isPlaying)
        assertFalse(playingToPaused.isPlaying)
    }

    @Test
    fun `reduce seek forward uses forward interval and clamps by duration`() {
        val state = activeStateTemplate().copy(currentPosition = 50_000L, forwardInterval = 15)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SeekForward)

        require(reduced is PlayerState.Active)
        assertEquals(60_000L, reduced.currentPosition)
    }

    @Test
    fun `reduce seek backward uses rewind interval and clamps to zero`() {
        val state = activeStateTemplate().copy(currentPosition = 5_000L, rewindInterval = 10)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SeekBackward)

        require(reduced is PlayerState.Active)
        assertEquals(0L, reduced.currentPosition)
    }

    @Test
    fun `reduce clamps playback speed to allowed range`() {
        val state = activeStateTemplate().copy(playbackSpeed = 1.0f)

        val tooLow = PlayerReducer.reduce(state, PlayerIntent.SetPlaybackSpeed(speed = 0.1f)) as PlayerState.Active
        val tooHigh = PlayerReducer.reduce(state, PlayerIntent.SetPlaybackSpeed(speed = 3.5f)) as PlayerState.Active

        assertEquals(0.5f, tooLow.playbackSpeed)
        assertEquals(2.0f, tooHigh.playbackSpeed)
    }

    @Test
    fun `reduce keeps state when playback speed is already clamped target`() {
        val state = activeStateTemplate().copy(playbackSpeed = 2.0f)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SetPlaybackSpeed(speed = 9.0f))

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce select chapter clamps index and resets position`() {
        val chapters =
            listOf(
                Chapter.preview().copy(id = "c1", chapterIndex = 0),
                Chapter.preview().copy(id = "c2", chapterIndex = 1),
                Chapter.preview().copy(id = "c3", chapterIndex = 2),
            ).toImmutableList()
        val state =
            activeStateTemplate().copy(
                chapters = chapters,
                currentChapterIndex = 0,
                currentChapter = chapters.first(),
                currentPosition = 42_000L,
            )

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SelectChapter(chapterIndex = 99))

        require(reduced is PlayerState.Active)
        assertEquals(2, reduced.currentChapterIndex)
        assertEquals("c3", reduced.currentChapter?.id)
        assertEquals(0L, reduced.currentPosition)
    }

    @Test
    fun `reduce toggle chapter repeat cycles repeat mode in active state`() {
        val off = activeStateTemplate().copy(chapterRepeatMode = ChapterRepeatMode.OFF)
        val once = PlayerReducer.reduce(off, PlayerIntent.ToggleChapterRepeat) as PlayerState.Active
        val infinite = PlayerReducer.reduce(once, PlayerIntent.ToggleChapterRepeat) as PlayerState.Active
        val backToOff = PlayerReducer.reduce(infinite, PlayerIntent.ToggleChapterRepeat) as PlayerState.Active

        assertEquals(ChapterRepeatMode.ONCE, once.chapterRepeatMode)
        assertEquals(ChapterRepeatMode.INFINITE, infinite.chapterRepeatMode)
        assertEquals(ChapterRepeatMode.OFF, backToOff.chapterRepeatMode)
    }

    @Test
    fun `reduce keeps state when fixed sleep timer request is idempotent`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.FIXED, sleepTimerRemainingSeconds = 300)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.StartSleepTimer(minutes = 5))

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce keeps state when end-of-track timer already active`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.END_OF_TRACK, sleepTimerRemainingSeconds = null)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.StartSleepTimerEndOfTrack)

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce updates state when fixed sleep timer request differs`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.FIXED, sleepTimerRemainingSeconds = 120)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.StartSleepTimer(minutes = 5))

        require(reduced is PlayerState.Active)
        assertEquals(PlayerSleepTimerMode.FIXED, reduced.sleepTimerMode)
        assertEquals(300, reduced.sleepTimerRemainingSeconds)
    }

    @Test
    fun `reduce keeps state when end-of-chapter timer already active`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.END_OF_CHAPTER, sleepTimerRemainingSeconds = null)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.StartSleepTimerEndOfChapter)

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce cancel sleep timer keeps state when already idle`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.IDLE, sleepTimerRemainingSeconds = null)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.CancelSleepTimer)

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce cancel sleep timer resets mode and remaining when active`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.FIXED, sleepTimerRemainingSeconds = 600)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.CancelSleepTimer)

        require(reduced is PlayerState.Active)
        assertEquals(PlayerSleepTimerMode.IDLE, reduced.sleepTimerMode)
        assertEquals(null, reduced.sleepTimerRemainingSeconds)
    }

    @Test
    fun `reduce keeps state when book seek settings intent does not change values`() {
        val state = activeStateTemplate().copy(rewindInterval = 10, forwardInterval = 30)

        val reduced =
            PlayerReducer.reduce(
                state,
                PlayerIntent.UpdateBookSeekSettings(rewindSeconds = 10, forwardSeconds = 30),
            )

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce updates state when book seek settings intent changes values`() {
        val state =
            activeStateTemplate().copy(
                rewindInterval = 10,
                forwardInterval = 30,
                defaultRewindInterval = 10,
                defaultForwardInterval = 30,
                hasBookSeekOverride = false,
            )

        val reduced =
            PlayerReducer.reduce(
                state,
                PlayerIntent.UpdateBookSeekSettings(rewindSeconds = 15, forwardSeconds = 45),
            )

        require(reduced is PlayerState.Active)
        assertEquals(15, reduced.rewindInterval)
        assertEquals(45, reduced.forwardInterval)
        assertTrue(reduced.hasBookSeekOverride)
    }

    @Test
    fun `reduce keeps state when reset seek settings intent is idempotent`() {
        val state =
            activeStateTemplate().copy(
                rewindInterval = 10,
                forwardInterval = 30,
                defaultRewindInterval = 10,
                defaultForwardInterval = 30,
                hasBookSeekOverride = false,
            )

        val reduced = PlayerReducer.reduce(state, PlayerIntent.ResetBookSeekSettings)

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce resets seek settings to defaults when override is active`() {
        val state =
            activeStateTemplate().copy(
                rewindInterval = 15,
                forwardInterval = 45,
                defaultRewindInterval = 10,
                defaultForwardInterval = 30,
                hasBookSeekOverride = true,
            )

        val reduced = PlayerReducer.reduce(state, PlayerIntent.ResetBookSeekSettings)

        require(reduced is PlayerState.Active)
        assertEquals(10, reduced.rewindInterval)
        assertEquals(30, reduced.forwardInterval)
        assertFalse(reduced.hasBookSeekOverride)
    }

    @Test
    fun `reduce keeps state when audio settings intent does not change values`() {
        val state =
            activeStateTemplate().copy(
                volumeBoostLevel = VolumeBoostLevel.Boost50,
                skipSilence = true,
                skipSilenceThresholdDb = -30f,
                skipSilenceMinMs = 220,
                skipSilenceMode = SkipSilenceMode.SPEED_UP,
                normalizeVolume = false,
                speechEnhancer = true,
                autoVolumeLeveling = true,
            )

        val reduced =
            PlayerReducer.reduce(
                state,
                PlayerIntent.UpdateAudioSettings(
                    volumeBoostLevel = VolumeBoostLevel.Boost50,
                    skipSilence = true,
                    skipSilenceThresholdDb = -30f,
                    skipSilenceMinMs = 220,
                    skipSilenceMode = SkipSilenceMode.SPEED_UP,
                    normalizeVolume = false,
                    speechEnhancer = true,
                    autoVolumeLeveling = true,
                ),
            )

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce updates state when audio settings intent changes values`() {
        val state = activeStateTemplate()

        val reduced =
            PlayerReducer.reduce(
                state,
                PlayerIntent.UpdateAudioSettings(
                    volumeBoostLevel = VolumeBoostLevel.Boost200,
                    skipSilence = true,
                    skipSilenceThresholdDb = -28f,
                    skipSilenceMinMs = 180,
                    skipSilenceMode = SkipSilenceMode.SPEED_UP,
                    normalizeVolume = false,
                    speechEnhancer = true,
                    autoVolumeLeveling = true,
                ),
            )

        require(reduced is PlayerState.Active)
        assertEquals(VolumeBoostLevel.Boost200, reduced.volumeBoostLevel)
        assertTrue(reduced.skipSilence)
        assertEquals(-28f, reduced.skipSilenceThresholdDb)
        assertEquals(180, reduced.skipSilenceMinMs)
        assertEquals(SkipSilenceMode.SPEED_UP, reduced.skipSilenceMode)
        assertFalse(reduced.normalizeVolume)
        assertTrue(reduced.speechEnhancer)
        assertTrue(reduced.autoVolumeLeveling)
    }

    @Test
    fun `nextChapterRepeatMode cycles through all modes`() {
        assertEquals(ChapterRepeatMode.ONCE, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.OFF))
        assertEquals(ChapterRepeatMode.INFINITE, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.ONCE))
        assertEquals(ChapterRepeatMode.OFF, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.INFINITE))
    }

    @Test
    fun `reduceChapterEnded in OFF mode never repeats and resets flag`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.OFF,
                hasRepeatedOnce = true,
            )

        assertFalse(reduction.shouldRepeat)
        assertFalse(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in ONCE mode repeats once when not repeated yet`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.ONCE,
                hasRepeatedOnce = false,
            )

        assertTrue(reduction.shouldRepeat)
        assertTrue(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in ONCE mode stops repeating after first repeat`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.ONCE,
                hasRepeatedOnce = true,
            )

        assertFalse(reduction.shouldRepeat)
        assertFalse(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in INFINITE mode always repeats and keeps flag`() {
        val reductionWithFalseFlag =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.INFINITE,
                hasRepeatedOnce = false,
            )
        val reductionWithTrueFlag =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.INFINITE,
                hasRepeatedOnce = true,
            )

        assertTrue(reductionWithFalseFlag.shouldRepeat)
        assertFalse(reductionWithFalseFlag.hasRepeatedOnce)
        assertTrue(reductionWithTrueFlag.shouldRepeat)
        assertTrue(reductionWithTrueFlag.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterChanged resets repeat flag`() {
        assertFalse(PlayerReducer.reduceChapterChanged())
    }

    @Test
    fun `reduce is deterministic for same input`() {
        val state = activeStateTemplate().copy(currentPosition = 12_345L, playbackSpeed = 1.25f)
        val intent = PlayerIntent.SeekForward

        val first = PlayerReducer.reduce(state, intent)
        val second = PlayerReducer.reduce(state, intent)

        assertEquals(first, second)
    }

    @Test
    fun `reduce does not mutate source state`() {
        val source =
            activeStateTemplate().copy(
                currentPosition = 9_000L,
                rewindInterval = 10,
                forwardInterval = 30,
            )
        val snapshot = source.copy()

        val reduced =
            PlayerReducer.reduce(
                source,
                PlayerIntent.UpdateBookSeekSettings(rewindSeconds = 15, forwardSeconds = 45),
            )

        require(reduced is PlayerState.Active)
        assertEquals(snapshot, source)
        assertNotSame(source, reduced)
        assertEquals(9_000L, source.currentPosition)
        assertEquals(10, source.rewindInterval)
        assertEquals(30, source.forwardInterval)
    }

    @Test
    fun `reduce transitions error to loading on initialize intent`() {
        val reduced = PlayerReducer.reduce(PlayerState.Error("oops"), PlayerIntent.InitializePlayer)

        assertEquals(PlayerState.Loading, reduced)
    }

    @Test
    fun `reduce replaces error message when report error on error state`() {
        val reduced = PlayerReducer.reduce(PlayerState.Error("old"), PlayerIntent.ReportError("new"))

        require(reduced is PlayerState.Error)
        assertEquals("new", reduced.message)
    }

    @Test
    fun `reduce transitions active to error on report error intent`() {
        val reduced = PlayerReducer.reduce(activeStateTemplate(), PlayerIntent.ReportError("boom"))

        require(reduced is PlayerState.Error)
        assertEquals("boom", reduced.message)
    }

    @Test
    fun `loading state matrix keeps state for all non error intents`() {
        val loading = PlayerState.Loading

        nonErrorIntents().forEach { intent ->
            val reduced = PlayerReducer.reduce(loading, intent)
            assertSame("Expected Loading state to stay unchanged for $intent", loading, reduced)
        }
    }

    @Test
    fun `error state matrix keeps state for all non recovery intents`() {
        val error = PlayerState.Error("boom")

        nonErrorAndInitializeIntents().forEach { intent ->
            val reduced = PlayerReducer.reduce(error, intent)
            assertSame("Expected Error state to stay unchanged for $intent", error, reduced)
        }
    }

    @Test
    fun `loading state never transitions directly to active for any intent`() {
        val loading = PlayerState.Loading

        allIntentsForMatrix().forEach { intent ->
            val reduced = PlayerReducer.reduce(loading, intent)
            assertFalse("Loading must not transition to Active for $intent", reduced is PlayerState.Active)
        }
    }

    @Test
    fun `error state never transitions directly to active for any intent`() {
        val error = PlayerState.Error("boom")

        allIntentsForMatrix().forEach { intent ->
            val reduced = PlayerReducer.reduce(error, intent)
            assertFalse("Error must not transition to Active for $intent", reduced is PlayerState.Active)
        }
    }

    @Test
    fun `active state never transitions to loading for any intent`() {
        val active = activeStateTemplate()

        allIntentsForMatrix().forEach { intent ->
            val reduced = PlayerReducer.reduce(active, intent)
            assertFalse("Active must not transition to Loading for $intent", reduced == PlayerState.Loading)
        }
    }

    @Test
    fun `full reducer matrix does not crash and preserves state invariants`() {
        val states =
            listOf<PlayerState>(
                PlayerState.Loading,
                PlayerState.Error("matrix"),
                activeStateTemplate(),
            )
        val intents = allIntentsForMatrix()

        states.forEach { state ->
            intents.forEach { intent ->
                val reduced = PlayerReducer.reduce(state, intent)
                assertNotNull("Reducer returned null for state=$state intent=$intent", reduced)

                when (state) {
                    PlayerState.Loading -> {
                        // Loading can only stay Loading or become Error.
                        assertFalse(
                            "Loading must not transition to Active for intent=$intent",
                            reduced is PlayerState.Active,
                        )
                    }
                    is PlayerState.Error -> {
                        // Error can only stay Error or become Loading.
                        assertFalse(
                            "Error must not transition to Active for intent=$intent",
                            reduced is PlayerState.Active,
                        )
                    }
                    is PlayerState.Active -> {
                        // Active can only stay Active or become Error.
                        assertFalse(
                            "Active must not transition to Loading for intent=$intent",
                            reduced == PlayerState.Loading,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `active state matrix keeps state for command-only intents`() {
        val active = activeStateTemplate()

        commandOnlyNoOpIntents().forEach { intent ->
            val reduced = PlayerReducer.reduce(active, intent)
            assertSame("Expected Active state to stay unchanged for $intent", active, reduced)
        }
    }

    @Test
    fun `start sleep timer clamps zero minutes to one minute`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.IDLE, sleepTimerRemainingSeconds = null)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.StartSleepTimer(minutes = 0))

        require(reduced is PlayerState.Active)
        assertEquals(PlayerSleepTimerMode.FIXED, reduced.sleepTimerMode)
        assertEquals(60, reduced.sleepTimerRemainingSeconds)
    }

    private fun activeStateTemplate(): PlayerState.Active =
        PlayerState.Active(
            book = Book.preview().copy(id = "book-1"),
            chapters =
                listOf(
                    Chapter.preview().copy(
                        id = "c1",
                        bookId = "book-1",
                        chapterIndex = 0,
                        fileIndex = 0,
                        duration = 1.minutes,
                        position = 0.seconds,
                    ),
                ).toImmutableList(),
            isPlaying = true,
            currentPosition = 10_000L,
            currentChapterIndex = 0,
            currentChapter = Chapter.preview().copy(id = "c1", bookId = "book-1", duration = 1.minutes),
            rewindInterval = 10,
            forwardInterval = 30,
            playbackSpeed = 1.0f,
            sleepTimerMode = PlayerSleepTimerMode.IDLE,
            sleepTimerRemainingSeconds = null,
            chapterRepeatMode = ChapterRepeatMode.OFF,
        )

    private fun nonErrorIntents(): List<PlayerIntent> =
        listOf(
            PlayerIntent.InitializePlayer,
            PlayerIntent.TogglePlayPause,
            PlayerIntent.Play,
            PlayerIntent.Pause,
            PlayerIntent.SkipNext,
            PlayerIntent.SkipPrevious,
            PlayerIntent.SeekTo(positionMs = 1234L),
            PlayerIntent.SeekForward,
            PlayerIntent.SeekBackward,
            PlayerIntent.SelectChapter(chapterIndex = 2),
            PlayerIntent.ToggleChapterRepeat,
            PlayerIntent.InitializeVisualizer,
            PlayerIntent.SetVisualizerEnabled(enabled = true),
            PlayerIntent.SetPlaybackSpeed(speed = 1.75f),
            PlayerIntent.SetPitchCorrectionEnabled(enabled = false),
            PlayerIntent.StartSleepTimer(minutes = 10),
            PlayerIntent.StartSleepTimerEndOfChapter,
            PlayerIntent.StartSleepTimerEndOfTrack,
            PlayerIntent.CancelSleepTimer,
            PlayerIntent.UpdateBookSeekSettings(rewindSeconds = 15, forwardSeconds = 45),
            PlayerIntent.ResetBookSeekSettings,
            PlayerIntent.UpdateAudioSettings(
                skipSilence = true,
                skipSilenceThresholdDb = -30f,
            ),
        )

    private fun nonErrorAndInitializeIntents(): List<PlayerIntent> = nonErrorIntents().filterNot { it == PlayerIntent.InitializePlayer }

    private fun commandOnlyNoOpIntents(): List<PlayerIntent> =
        listOf(
            PlayerIntent.InitializePlayer,
            PlayerIntent.SkipNext,
            PlayerIntent.SkipPrevious,
            PlayerIntent.InitializeVisualizer,
            PlayerIntent.SetVisualizerEnabled(enabled = true),
            PlayerIntent.SetPitchCorrectionEnabled(enabled = false),
        )

    private fun allIntentsForMatrix(): List<PlayerIntent> = nonErrorIntents() + PlayerIntent.ReportError("matrix-error")
}
