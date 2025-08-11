package com.jabook.app.features.settings.presentation

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.R
import com.jabook.app.core.domain.repository.RuTrackerRepository
import com.jabook.app.core.network.RuTrackerAvailabilityChecker
import com.jabook.app.core.network.RuTrackerPreferences
import com.jabook.app.shared.debug.IDebugLogger
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
    private val ruTrackerAvailabilityChecker: RuTrackerAvailabilityChecker,
    private val debugLogger: IDebugLogger, // Inject logger for log export
    application: Application,
) : ViewModel() {
    private val appContext = application.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("jabook_prefs", Context.MODE_PRIVATE)

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
            _state.update { it.copy(error = appContext.getString(R.string.rutracker_enter_credentials)) }
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
                            successMessage = appContext.getString(R.string.rutracker_auth_success),
                        )
                    }
                } else {
                    _state.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            error = appContext.getString(R.string.rutracker_invalid_credentials),
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = appContext.getString(R.string.rutracker_auth_error, e.message ?: ""),
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
                    successMessage = appContext.getString(R.string.rutracker_logout_success),
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
                        successMessage = appContext.getString(R.string.rutracker_switched_to_guest),
                    )
                }
            } else {
                _state.update { currentState ->
                    currentState.copy(
                        isGuestMode = false,
                        successMessage = appContext.getString(R.string.rutracker_switched_to_auth),
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

    /**
     * Export debug logs as a file for sharing or diagnostics.
     * Returns the log file or null if not available.
     */
    fun exportLogs(): java.io.File? {
        return debugLogger.exportLogs()
    }

    /**
     * Save the selected log folder Uri in SharedPreferences for SAF logging.
     */
    fun setLogFolderUri(uri: String) {
        prefs.edit().putString("log_folder_uri", uri).apply()
    }

    /**
     * Get the SAF log folder Uri as a string, or null if not set.
     */
    fun getLogFolderUri(): String? {
        return prefs.getString("log_folder_uri", null)
    }

    /**
     * Get the Uri of the log file in the SAF folder, or null if not found.
     */
    fun getLogFileUriFromSaf(fileName: String): Uri? {
        val logFolderUriString = getLogFolderUri() ?: return null
        val logFolderUri = Uri.parse(logFolderUriString)
        val contentResolver = appContext.contentResolver
        // Find the file in the SAF folder
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(logFolderUri, DocumentsContract.getTreeDocumentId(logFolderUri))
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(1)
                if (name == fileName) {
                    val documentId = it.getString(0)
                    return DocumentsContract.buildDocumentUriUsingTree(logFolderUri, documentId)
                }
            }
        }
        return null
    }

    /**
     * Write a test log entry to ensure the log file is created in the selected folder.
     */
    fun writeTestLogEntry() {
        debugLogger.logInfo("Test log entry: SAF folder selected and log file created.")
    }

    /**
     * Perform manual RuTracker availability check and show result to user.
     */
    fun checkRuTrackerAvailability() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, successMessage = null) }

            try {
                val result = ruTrackerAvailabilityChecker.performManualCheck()

                when {
                    result.isSuccess -> {
                        val isAvailable = result.getOrNull() ?: false
                        if (isAvailable) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    successMessage = appContext.getString(R.string.rutracker_available),
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error = appContext.getString(R.string.rutracker_not_available),
                                )
                            }
                        }
                    }
                    result.isFailure -> {
                        val exception = result.exceptionOrNull()
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = appContext.getString(
                                    R.string.rutracker_availability_check_failed,
                                    exception?.message ?: "Unknown error",
                                ),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = appContext.getString(R.string.rutracker_availability_error, e.message ?: ""),
                    )
                }
            }
        }
    }
}
