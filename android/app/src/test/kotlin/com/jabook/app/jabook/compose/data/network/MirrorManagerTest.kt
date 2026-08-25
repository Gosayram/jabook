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

package com.jabook.app.jabook.compose.data.network

import com.jabook.app.jabook.BuildConfig
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.preferences.PlayerStateSnapshotPreference
import com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode
import com.jabook.app.jabook.compose.data.preferences.SleepTimerState
import com.jabook.app.jabook.compose.data.preferences.ThemeMode
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MirrorManagerTest {
    // Mirror names come from build config (.env), never hardcoded as literals.
    private val mirrorOrg: String =
        BuildConfig.RUTRACKER_DEFAULT_MIRRORS
            .split(',')
            .first()
            .trim()
    private val mirrorNet: String =
        BuildConfig.RUTRACKER_DEFAULT_MIRRORS
            .split(',')
            .getOrNull(1)
            ?.trim() ?: "mirror-b.example"

    @Test
    fun `switchToNextMirror switches to next healthy mirror and persists selection`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(
                    initial =
                        UserPreferences
                            .newBuilder()
                            .setSelectedMirror(mirrorOrg)
                            .build(),
                )
            val mirrorManager =
                MirrorManager(
                    settingsRepository = settingsRepository,
                    okHttpClient =
                        createHealthCheckClient(
                            statusByHost =
                                mapOf(
                                    mirrorOrg to 503,
                                    mirrorNet to 200,
                                ),
                        ),
                    loggerFactory = noOpLoggerFactory(),
                )

            val switched = mirrorManager.switchToNextMirror()

            assertTrue(switched)
            assertEquals(mirrorNet, mirrorManager.getCurrentMirrorDomain())
            assertEquals(mirrorNet, settingsRepository.latestSelectedMirror)
        }

    @Test
    fun `switchToNextMirror returns false when all mirrors are unhealthy`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(
                    initial =
                        UserPreferences
                            .newBuilder()
                            .setSelectedMirror(mirrorOrg)
                            .build(),
                )
            val mirrorManager =
                MirrorManager(
                    settingsRepository = settingsRepository,
                    okHttpClient =
                        createHealthCheckClient(
                            statusByHost =
                                mapOf(
                                    mirrorOrg to 503,
                                    mirrorNet to 503,
                                ),
                        ),
                    loggerFactory = noOpLoggerFactory(),
                )

            val switched = mirrorManager.switchToNextMirror()

            assertFalse(switched)
            assertEquals(mirrorOrg, mirrorManager.getCurrentMirrorDomain())
            assertEquals(mirrorOrg, settingsRepository.latestSelectedMirror)
        }

    @Test
    fun `switchToNextMirror does not flap right after a successful switch`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(
                    initial =
                        UserPreferences
                            .newBuilder()
                            .setSelectedMirror(mirrorOrg)
                            .build(),
                )
            val mirrorManager =
                MirrorManager(
                    settingsRepository = settingsRepository,
                    okHttpClient =
                        createHealthCheckClient(
                            statusByHost =
                                mapOf(
                                    mirrorOrg to 200,
                                    mirrorNet to 200,
                                ),
                        ),
                    loggerFactory = noOpLoggerFactory(),
                )

            assertTrue(mirrorManager.switchToNextMirror())
            assertEquals(mirrorNet, mirrorManager.getCurrentMirrorDomain())

            // Grace period: without it, this would flap back to mirrorOrg.
            assertFalse(mirrorManager.switchToNextMirror())
            assertEquals(mirrorNet, mirrorManager.getCurrentMirrorDomain())
        }

    @Test
    fun `switchToNextMirror backs off after a failed attempt and does not re-probe`() =
        runTest {
            var probeCount = 0
            val settingsRepository =
                FakeSettingsRepository(
                    initial =
                        UserPreferences
                            .newBuilder()
                            .setSelectedMirror(mirrorOrg)
                            .build(),
                )
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            probeCount++
                            Response
                                .Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(503)
                                .message("stub")
                                .body("{}".toResponseBody())
                                .build()
                        },
                    ).build()
            val mirrorManager =
                MirrorManager(
                    settingsRepository = settingsRepository,
                    okHttpClient = client,
                    loggerFactory = noOpLoggerFactory(),
                )

            assertFalse(mirrorManager.switchToNextMirror())
            assertEquals(1, probeCount) // net probed; org skipped as current mirror

            // Backoff: the second attempt must NOT re-probe dead mirrors.
            assertFalse(mirrorManager.switchToNextMirror())
            assertEquals(1, probeCount)
        }

    @Test
    fun `403 without Cloudflare markers is treated as Dead not CloudflareProtected`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(
                    initial =
                        UserPreferences
                            .newBuilder()
                            .setSelectedMirror(mirrorOrg)
                            .build(),
                )
            val mirrorManager =
                MirrorManager(
                    settingsRepository = settingsRepository,
                    okHttpClient =
                        createHealthCheckClient(
                            statusByHost =
                                mapOf(
                                    mirrorOrg to 403,
                                    mirrorNet to 200,
                                ),
                        ),
                    loggerFactory = noOpLoggerFactory(),
                )

            // Plain 403 (no cf-mitigated / challenge body) must NOT be considered
            // switchable — that misclassification caused constant mirror flapping.
            assertTrue(mirrorManager.switchToNextMirror())
            assertEquals(mirrorNet, mirrorManager.getCurrentMirrorDomain())
        }

    private fun createHealthCheckClient(statusByHost: Map<String, Int>): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val host = chain.request().url.host
                    val code = statusByHost[host] ?: 503
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("stub")
                        .body("{}".toResponseBody())
                        .build()
                },
            ).build()

    private fun noOpLoggerFactory(): LoggerFactory {
        val logger =
            object : Logger {
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
        return object : LoggerFactory {
            override fun get(tag: String): Logger = logger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = logger
        }
    }
}

internal class FakeSettingsRepository(
    initial: UserPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    var latestSelectedMirror: String = initial.selectedMirror
        private set

    override val userPreferences: Flow<UserPreferences> = state
    override val playerStateSnapshot: Flow<PlayerStateSnapshotPreference?> = MutableStateFlow(null)

    override val bassBoostStrength: Flow<Int> = MutableStateFlow(0)

    override val audioVisualizerMode: Flow<Int> = MutableStateFlow(0)

    override val customEqBands: Flow<List<Int>> = MutableStateFlow(emptyList())

    override suspend fun updateCustomEqBands(bands: List<Int>) = Unit

    override suspend fun updateAudioVisualizerMode(mode: Int) = Unit

    override suspend fun updateThemeMode(themeMode: ThemeMode) = Unit

    override suspend fun updateDynamicColors(enabled: Boolean) = Unit

    override suspend fun updateBassBoostStrength(strength: Int) = Unit

    override suspend fun updateAudioSettings(
        rewindSeconds: Int?,
        forwardSeconds: Int?,
        resumeRewindSeconds: Int?,
        resumeRewindMode: ResumeRewindMode?,
        resumeRewindAggressiveness: Float?,
        sleepTimerShakeExtendEnabled: Boolean?,
        holdToBoostSpeed: Float?,
        autoPipEnabled: Boolean?,
        headsetAutoplayEnabled: Boolean?,
        volumeBoost: String?,
        drcLevel: String?,
        speechCompressorLevel: String?,
        speechEnhancer: Boolean?,
        autoVolumeLeveling: Boolean?,
        normalizeVolume: Boolean?,
        skipSilence: Boolean?,
        skipSilenceThresholdDb: Float?,
        skipSilenceMinMs: Int?,
        skipSilenceMode: SkipSilenceMode?,
        crossfadeEnabled: Boolean?,
        crossfadeDurationMs: Long?,
        noiseGateLevel: String?,
        singleClickAction: Int?,
        doubleClickAction: Int?,
        tripleClickAction: Int?,
        longPressAction: Int?,
        notificationActionSlots: List<Int>?,
    ) = Unit

    override suspend fun updateNotificationSettings(
        notificationsEnabled: Boolean?,
        downloadNotifications: Boolean?,
        playerNotifications: Boolean?,
    ) = Unit

    override suspend fun applyBackupSettings(
        wifiOnly: Boolean,
        autoLoadCoversOnCellular: Boolean,
        downloadPath: String,
        selectedMirror: String,
        autoSwitchMirror: Boolean,
        limitDownloadSpeed: Boolean,
        maxDownloadSpeedKb: Int,
        maxConcurrentDownloads: Int,
        rewindSeconds: Int,
        forwardSeconds: Int,
        dynamicColors: Boolean,
        notificationsEnabled: Boolean,
        downloadNotifications: Boolean,
        playerNotifications: Boolean,
        customMirrors: List<String>,
    ) = Unit

    override suspend fun updateSelectedMirror(domain: String) {
        latestSelectedMirror = domain
        state.update { prefs -> prefs.toBuilder().setSelectedMirror(domain).build() }
    }

    override suspend fun updateAccentSwatchIndex(index: Int) = Unit

    override suspend fun updatePlayerCoverMode(mode: Int) = Unit

    override suspend fun addCustomMirror(domain: String) {
        state.update { prefs ->
            val builder = prefs.toBuilder()
            if (!builder.customMirrorsList.contains(domain)) {
                builder.addCustomMirrors(domain)
            }
            builder.build()
        }
    }

    override suspend fun removeCustomMirror(domain: String) {
        state.update { prefs ->
            val filtered = prefs.customMirrorsList.filterNot { it == domain }
            prefs
                .toBuilder()
                .clearCustomMirrors()
                .addAllCustomMirrors(filtered)
                .build()
        }
    }

    override suspend fun updateAutoSwitchMirror(enabled: Boolean) {
        state.update { prefs -> prefs.toBuilder().setAutoSwitchMirror(enabled).build() }
    }

    override suspend fun updateDownloadPath(path: String) = Unit

    override suspend fun updateWifiOnly(enabled: Boolean) = Unit

    override suspend fun updateAutoLoadCoversOnCellular(enabled: Boolean) = Unit

    override suspend fun updateLimitDownloadSpeed(enabled: Boolean) = Unit

    override suspend fun updateMaxDownloadSpeed(speedKb: Int) = Unit

    override suspend fun updateMaxConcurrentDownloads(count: Int) = Unit

    override suspend fun updateLibrarySortOrder(sortOrder: String) = Unit

    override suspend fun updateSpotlightCompleted(completed: Boolean) = Unit

    override suspend fun updateEqualizerPreset(preset: String) = Unit

    override suspend fun updatePlayerStateSnapshot(snapshot: PlayerStateSnapshotPreference) = Unit

    override suspend fun clearPlayerStateSnapshot() = Unit

    override val sleepTimerState: Flow<SleepTimerState> = MutableStateFlow(SleepTimerState.getDefaultInstance())

    override suspend fun updateSleepTimerState(state: SleepTimerState) = Unit

    override suspend fun clearSleepTimerState() = Unit

    override suspend fun resetToDefaults() = Unit
}
