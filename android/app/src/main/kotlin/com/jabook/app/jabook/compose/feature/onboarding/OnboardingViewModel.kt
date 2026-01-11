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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steps in the onboarding process.
 */
public enum class OnboardingStep {
    WELCOME,
    FEATURES,
    PERMISSIONS,
}

/**
 * UI state for the onboarding flow.
 */
public data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val isFinished: Boolean = false,
)

/**
 * ViewModel for the Onboarding feature.
 */
@HiltViewModel
public class OnboardingViewModel
    @Inject
    constructor(
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        public val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        public fun nextStep() {
            val current = _uiState.value.currentStep
            val next =
                when (current) {
                    OnboardingStep.WELCOME -> OnboardingStep.FEATURES
                    OnboardingStep.FEATURES -> OnboardingStep.PERMISSIONS
                    OnboardingStep.PERMISSIONS -> {
                        finishOnboarding()
                        return
                    }
                }
            _uiState.value = _uiState.value.copy(currentStep = next)
        }

        public fun previousStep() {
            val current = _uiState.value.currentStep
            val prev =
                when (current) {
                    OnboardingStep.WELCOME -> return
                    OnboardingStep.FEATURES -> OnboardingStep.WELCOME
                    OnboardingStep.PERMISSIONS -> OnboardingStep.FEATURES
                }
            _uiState.value = _uiState.value.copy(currentStep = prev)
        }

        public fun finishOnboarding() {
            viewModelScope.launch {
                userPreferencesRepository.setOnboardingCompleted(true)
                _uiState.value = _uiState.value.copy(isFinished = true)
            }
        }
    }
