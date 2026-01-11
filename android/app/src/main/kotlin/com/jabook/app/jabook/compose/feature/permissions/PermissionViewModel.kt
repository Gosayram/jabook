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

package com.jabook.app.jabook.compose.feature.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.permissions.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

public data class PermissionUiState(
    public val hasStoragePermission: Boolean = false,
    public val hasNotificationPermission: Boolean = false,
)

@HiltViewModel
public class PermissionViewModel
    @Inject
    constructor(
        private val permissionManager: PermissionManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PermissionUiState())
        public val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

        init {
            checkPermissions()
        }

        public fun checkPermissions() : Unit {
            viewModelScope.launch {
                public val storage = permissionManager.hasStoragePermission()
                public val notification = permissionManager.hasNotificationPermission()

                _uiState.value =
                    PermissionUiState(
                        hasStoragePermission = storage,
                        hasNotificationPermission = notification,
                    )
            }
        }

        public fun getManageExternalStorageIntent() = permissionManager.getManageExternalStorageIntent()

        public fun getAppSettingsIntent() = permissionManager.getAppSettingsIntent()

        // Notification permission is requested via registerForActivityResult contract directly in UI
    }
