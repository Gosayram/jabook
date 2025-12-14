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

data class PermissionUiState(
    val hasStoragePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
)

@HiltViewModel
class PermissionViewModel
    @Inject
    constructor(
        private val permissionManager: PermissionManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PermissionUiState())
        val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

        init {
            checkPermissions()
        }

        fun checkPermissions() {
            viewModelScope.launch {
                val storage = permissionManager.hasStoragePermission()
                val notification = permissionManager.hasNotificationPermission()

                _uiState.value =
                    PermissionUiState(
                        hasStoragePermission = storage,
                        hasNotificationPermission = notification,
                    )
            }
        }

        fun getManageExternalStorageIntent() = permissionManager.getManageExternalStorageIntent()

        fun getAppSettingsIntent() = permissionManager.getAppSettingsIntent()

        // Notification permission is requested via registerForActivityResult contract directly in UI
    }
