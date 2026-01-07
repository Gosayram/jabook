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

package com.jabook.app.jabook.compose.feature.onboarding

import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val settingsRepository = FakeSettingsRepository()
    private lateinit var viewModel: OnboardingViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OnboardingViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is WELCOME`() {
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `nextStep transitions correctly`() {
        viewModel.nextStep()
        assertEquals(OnboardingStep.FEATURES, viewModel.uiState.value.currentStep)
        
        viewModel.nextStep()
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `previousStep transitions correctly`() {
        viewModel.nextStep() // To FEATURES
        viewModel.previousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `finishOnboarding updates repository and finishes state`() = runTest {
        viewModel.finishOnboarding()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(true, settingsRepository.onboardingCompleted)
        assertEquals(true, viewModel.uiState.value.isFinished)
    }

    @Test
    fun `nextStep on last step finishes onboarding`() = runTest {
        viewModel.nextStep() // To FEATURES
        viewModel.nextStep() // To PERMISSIONS
        viewModel.nextStep() // Should call finish
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(true, settingsRepository.onboardingCompleted)
        assertEquals(true, viewModel.uiState.value.isFinished)
    }

    private class FakeSettingsRepository : SettingsRepository {
        var onboardingCompleted = false
        
        override val userPreferences: kotlinx.coroutines.flow.Flow<com.jabook.app.jabook.compose.data.preferences.UserPreferences>
            get() = kotlinx.coroutines.flow.flowOf(com.jabook.app.jabook.compose.data.preferences.UserPreferences.getDefaultInstance())

        override suspend fun updateOnboardingCompleted(completed: Boolean) {
            onboardingCompleted = completed
        }

        // Implement other methods as empty/no-op
        override suspend fun updateThemeMode(themeMode: com.jabook.app.jabook.compose.data.preferences.ThemeMode) {}
        override suspend fun updateDynamicColors(enabled: Boolean) {}
        override suspend fun updatePlaybackSpeed(speed: Float) {}
        override suspend fun updateAudioSettings(rewindSeconds: Int?, forwardSeconds: Int?, volumeBoost: String?, drcLevel: String?, speechEnhancer: Boolean?, autoVolumeLeveling: Boolean?, normalizeVolume: Boolean?, skipSilence: Boolean?, crossfadeEnabled: Boolean?, crossfadeDurationMs: Long?) {}
        override suspend fun updateLanguage(languageCode: String) {}
        override suspend fun updateNotificationSettings(notificationsEnabled: Boolean?, downloadNotifications: Boolean?, playerNotifications: Boolean?) {}
        override suspend fun updateSelectedMirror(domain: String) {}
        override suspend fun addCustomMirror(domain: String) {}
        override suspend fun removeCustomMirror(domain: String) {}
        override suspend fun updateAutoSwitchMirror(enabled: Boolean) {}
        override suspend fun updateDownloadPath(path: String) {}
        override suspend fun updateWifiOnly(enabled: Boolean) {}
        override suspend fun updateLimitDownloadSpeed(enabled: Boolean) {}
        override suspend fun updateMaxDownloadSpeed(speedKb: Int) {}
        override suspend fun updateMaxConcurrentDownloads(count: Int) {}
        override suspend fun updateLibrarySortOrder(sortOrder: String) {}
        override suspend fun resetToDefaults() {}
    }
}
