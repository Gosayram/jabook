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

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode
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

@OptIn(ExperimentalCoroutinesApi::class)
class MirrorManagerTest {
    @Test
    fun `switchToNextMirror switches to next healthy mirror and persists selection`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(
                    initial =
                        UserPreferences
                            .newBuilder()
                            .setSelectedMirror("rutracker.org")
                            .build(),
                )
            val mirrorManager =
                MirrorManager(
                    settingsRepository = settingsRepository,
                    okHttpClient =
                        createHealthCheckClient(
                            statusByHost =
                                mapOf(
                                    "rutracker.org" to 503,
                                    "rutracker.net" to 200,
                                    "rutracker.me" to 503,
                                ),
                        ),
                    loggerFactory = noOpLoggerFactory(),
                )

            val switched = mirrorManager.switchToNextMirror()

            assertTrue(switched)
            assertEquals("rutracker.net", mirrorManager.getCurrentMirrorDomain())
            assertEquals("rutracker.net", settingsRepository.latestSelectedMirror)
        }

    @Test
    fun `switchToNextMirror returns false when all mirrors are unhealthy`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(
                    initial =
                        UserPreferences
                            .newBuilder()
                            .setSelectedMirror("rutracker.org")
                            .build(),
                )
            val mirrorManager =
                MirrorManager(
                    settingsRepository = settingsRepository,
                    okHttpClient =
                        createHealthCheckClient(
                            statusByHost =
                                mapOf(
                                    "rutracker.org" to 503,
                                    "rutracker.net" to 503,
                                    "rutracker.me" to 503,
                                ),
                        ),
                    loggerFactory = noOpLoggerFactory(),
                )

            val switched = mirrorManager.switchToNextMirror()

            assertFalse(switched)
            assertEquals("rutracker.org", mirrorManager.getCurrentMirrorDomain())
            assertEquals("rutracker.org", settingsRepository.latestSelectedMirror)
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

private class FakeSettingsRepository(
    initial: UserPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    var latestSelectedMirror: String = initial.selectedMirror
        private set

    override val userPreferences: Flow<UserPreferences> = state

    override suspend fun updateThemeMode(themeMode: ThemeMode) = Unit

    override suspend fun updateDynamicColors(enabled: Boolean) = Unit

    override suspend fun updatePlaybackSpeed(speed: Float) = Unit

    override suspend fun updateAudioSettings(
        rewindSeconds: Int?,
        forwardSeconds: Int?,
        resumeRewindSeconds: Int?,
        sleepTimerShakeExtendEnabled: Boolean?,
        volumeBoost: String?,
        drcLevel: String?,
        speechEnhancer: Boolean?,
        autoVolumeLeveling: Boolean?,
        normalizeVolume: Boolean?,
        skipSilence: Boolean?,
        skipSilenceThresholdDb: Float?,
        skipSilenceMinMs: Int?,
        skipSilenceMode: SkipSilenceMode?,
        crossfadeEnabled: Boolean?,
        crossfadeDurationMs: Long?,
    ) = Unit

    override suspend fun updateLanguage(languageCode: String) = Unit

    override suspend fun updateNotificationSettings(
        notificationsEnabled: Boolean?,
        downloadNotifications: Boolean?,
        playerNotifications: Boolean?,
    ) = Unit

    override suspend fun updateSelectedMirror(domain: String) {
        latestSelectedMirror = domain
        state.update { prefs -> prefs.toBuilder().setSelectedMirror(domain).build() }
    }

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

    override suspend fun updateLimitDownloadSpeed(enabled: Boolean) = Unit

    override suspend fun updateMaxDownloadSpeed(speedKb: Int) = Unit

    override suspend fun updateMaxConcurrentDownloads(count: Int) = Unit

    override suspend fun updateLibrarySortOrder(sortOrder: String) = Unit

    override suspend fun updateOnboardingCompleted(completed: Boolean) = Unit

    override suspend fun resetToDefaults() = Unit
}
