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

import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val userPreferencesRepository: UserPreferencesRepository = mock()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OnboardingViewModel(userPreferencesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is welcome and not finished`() {
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertFalse(state.isFinished)
    }

    @Test
    fun `next step advances welcome to features then permissions`() {
        viewModel.nextStep()
        assertEquals(OnboardingStep.FEATURES, viewModel.uiState.value.currentStep)

        viewModel.nextStep()
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `next step on last step finishes onboarding`() =
        runTest {
            whenever(userPreferencesRepository.setOnboardingCompleted(true)).thenReturn(true)

            viewModel.nextStep()
            viewModel.nextStep()
            viewModel.nextStep()

            verify(userPreferencesRepository).setOnboardingCompleted(true)
            assertTrue(viewModel.uiState.value.isFinished)
        }

    @Test
    fun `previous step goes back and stops at welcome`() {
        viewModel.nextStep()
        assertEquals(OnboardingStep.FEATURES, viewModel.uiState.value.currentStep)

        viewModel.previousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)

        viewModel.previousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `failed persistence keeps onboarding unfinished`() =
        runTest {
            whenever(userPreferencesRepository.setOnboardingCompleted(true)).thenReturn(false)

            viewModel.finishOnboarding()

            verify(userPreferencesRepository).setOnboardingCompleted(true)
            assertFalse(viewModel.uiState.value.isFinished)
        }

    @Test
    fun `navigating steps does not persist completion`() =
        runTest {
            viewModel.nextStep()
            viewModel.previousStep()

            verify(userPreferencesRepository, never()).setOnboardingCompleted(true)
            assertFalse(viewModel.uiState.value.isFinished)
        }
}
