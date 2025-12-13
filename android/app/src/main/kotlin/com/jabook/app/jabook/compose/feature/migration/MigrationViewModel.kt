// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.feature.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.migration.DataMigrationManager
import com.jabook.app.jabook.migration.MigrationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for migration screen.
 */
@HiltViewModel
class MigrationViewModel
    @Inject
    constructor(
        private val migrationManager: DataMigrationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow<MigrationUiState>(MigrationUiState.Checking)
        val state: StateFlow<MigrationUiState> = _state.asStateFlow()

        init {
            checkAndStartMigration()
        }

        private fun checkAndStartMigration() {
            viewModelScope.launch {
                try {
                    // Check if migration is needed
                    val needsMigration = migrationManager.needsMigration()

                    if (!needsMigration) {
                        _state.value = MigrationUiState.NotNeeded
                        return@launch
                    }

                    // Start migration
                    _state.value = MigrationUiState.Migrating

                    when (val result = migrationManager.migrateFromFlutter()) {
                        is MigrationResult.Success -> {
                            _state.value =
                                MigrationUiState.Success(
                                    booksCount = result.booksCount,
                                    chaptersCount = result.chaptersCount,
                                )
                        }
                        is MigrationResult.Failure -> {
                            _state.value =
                                MigrationUiState.Error(
                                    message = result.error.message ?: "Unknown error",
                                )
                        }
                    }
                } catch (e: Exception) {
                    _state.value = MigrationUiState.Error(message = e.message ?: "Unknown error")
                }
            }
        }
    }

/**
 * UI state for migration screen.
 */
sealed class MigrationUiState {
    object Checking : MigrationUiState()

    object Migrating : MigrationUiState()

    data class Success(
        val booksCount: Int,
        val chaptersCount: Int,
    ) : MigrationUiState()

    data class Error(
        val message: String,
    ) : MigrationUiState()

    object NotNeeded : MigrationUiState()
}
