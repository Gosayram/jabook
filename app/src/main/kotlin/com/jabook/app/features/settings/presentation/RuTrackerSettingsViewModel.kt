package com.jabook.app.features.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.domain.repository.RuTrackerRepository
import com.jabook.app.core.network.RuTrackerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuTrackerSettingsState(
    val isGuestMode: Boolean = true,
    val username: String = "",
    val password: String = "",
    val isAuthorized: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

sealed class RuTrackerSettingsEvent {
    data class UpdateGuestMode(val isGuestMode: Boolean) : RuTrackerSettingsEvent()
    data class UpdateUsername(val username: String) : RuTrackerSettingsEvent()
    data class UpdatePassword(val password: String) : RuTrackerSettingsEvent()
    object Login : RuTrackerSettingsEvent()
    object Logout : RuTrackerSettingsEvent()
    object ClearError : RuTrackerSettingsEvent()
    object ClearSuccess : RuTrackerSettingsEvent()
}

@HiltViewModel
class RuTrackerSettingsViewModel @Inject constructor(
    private val ruTrackerRepository: RuTrackerRepository,
    private val ruTrackerPreferences: RuTrackerPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(RuTrackerSettingsState())
    val state: StateFlow<RuTrackerSettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    fun handleEvent(event: RuTrackerSettingsEvent) {
        when (event) {
            is RuTrackerSettingsEvent.UpdateGuestMode -> {
                updateGuestMode(event.isGuestMode)
            }
            is RuTrackerSettingsEvent.UpdateUsername -> {
                updateUsername(event.username)
            }
            is RuTrackerSettingsEvent.UpdatePassword -> {
                updatePassword(event.password)
            }
            is RuTrackerSettingsEvent.Login -> {
                login()
            }
            is RuTrackerSettingsEvent.Logout -> {
                logout()
            }
            is RuTrackerSettingsEvent.ClearError -> {
                clearError()
            }
            is RuTrackerSettingsEvent.ClearSuccess -> {
                clearSuccess()
            }
        }
    }

    fun setGuestMode(isGuestMode: Boolean) {
        updateGuestMode(isGuestMode)
    }

    fun setUsername(username: String) {
        updateUsername(username)
    }

    fun setPassword(password: String) {
        updatePassword(password)
    }

    fun login() {
        val currentState = _state.value
        if (currentState.username.isBlank() || currentState.password.isBlank()) {
            _state.update { it.copy(error = "Enter login and password") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val success = ruTrackerRepository.login(
                    username = currentState.username,
                    password = currentState.password,
                )

                if (success) {
                    ruTrackerPreferences.setCredentials(
                        username = currentState.username,
                        password = currentState.password,
                    )
                    _state.update { currentState ->
                        currentState.copy(
                            isAuthorized = true,
                            isLoading = false,
                            successMessage = "Successful authorization",
                        )
                    }
                } else {
                    _state.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            error = "Invalid login or password",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = "Authorization error: ${e.message}",
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            ruTrackerRepository.logout()
            ruTrackerPreferences.clearCredentials()

            _state.update { currentState ->
                currentState.copy(
                    isAuthorized = false,
                    username = "",
                    password = "",
                    successMessage = "Logout completed",
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val isGuestMode = ruTrackerPreferences.isGuestMode()
            val credentials = ruTrackerPreferences.getCredentials()
            val username = credentials?.first ?: ""
            val isAuthorized = ruTrackerRepository.isAuthenticated()

            _state.update { currentState ->
                currentState.copy(
                    isGuestMode = isGuestMode,
                    username = username,
                    isAuthorized = isAuthorized,
                )
            }
        }
    }

    private fun updateGuestMode(isGuestMode: Boolean) {
        viewModelScope.launch {
            ruTrackerPreferences.setGuestMode(isGuestMode)
            if (isGuestMode) {
                // When switching to guest mode, clear authentication
                ruTrackerPreferences.clearCredentials()
                _state.update { currentState ->
                    currentState.copy(
                        isGuestMode = true,
                        isAuthorized = false,
                        username = "",
                        password = "",
                        successMessage = "Switched to guest mode",
                    )
                }
            } else {
                _state.update { currentState ->
                    currentState.copy(
                        isGuestMode = false,
                        successMessage = "Switched to authorization mode",
                    )
                }
            }
        }
    }

    private fun updateUsername(username: String) {
        _state.update { currentState ->
            currentState.copy(username = username)
        }
    }

    private fun updatePassword(password: String) {
        _state.update { currentState ->
            currentState.copy(password = password)
        }
    }

    private fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun clearSuccess() {
        _state.update { it.copy(successMessage = null) }
    }
}
