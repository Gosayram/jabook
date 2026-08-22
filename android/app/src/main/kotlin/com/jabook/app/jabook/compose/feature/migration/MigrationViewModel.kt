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

package com.jabook.app.jabook.compose.feature.migration

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.R
import com.jabook.app.jabook.migration.DataMigrationManager
import com.jabook.app.jabook.migration.MigrationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for migration screen.
 */
@HiltViewModel
public class MigrationViewModel
    @Inject
    constructor(
        private val migrationManager: DataMigrationManager,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow<MigrationUiState>(MigrationUiState.Checking)
        public val state: StateFlow<MigrationUiState> = _state.asStateFlow()

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
                            val msg = result.error.message ?: context.getString(R.string.unknown_error)
                            _state.value =
                                MigrationUiState.Error(message = msg)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = e.message ?: context.getString(R.string.unknown_error)
                    _state.value =
                        MigrationUiState.Error(message = msg)
                }
            }
        }
    }

/**
 * UI state for migration screen.
 */
public sealed class MigrationUiState {
    public data object Checking : MigrationUiState()

    public data object Migrating : MigrationUiState()

    public data class Success(
        val booksCount: Int,
        val chaptersCount: Int,
    ) : MigrationUiState()

    public data class Error(
        val message: String,
    ) : MigrationUiState()

    public data object NotNeeded : MigrationUiState()
}
