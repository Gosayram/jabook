package com.jabook.app.features.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jabook.app.features.settings.presentation.components.AvailabilityCheckSection
import com.jabook.app.features.settings.presentation.components.LogExportSection
import com.jabook.app.features.settings.presentation.components.LoginCard
import com.jabook.app.features.settings.presentation.components.LoginCardState
import com.jabook.app.features.settings.presentation.components.ModeToggleCard
import com.jabook.app.features.settings.presentation.components.SafLogFolderSection
import com.jabook.app.features.settings.presentation.components.StatusMessageCard
import com.jabook.app.features.settings.presentation.components.TestLogEntrySection

@Composable
fun RuTrackerSettingsScreen(viewModel: RuTrackerSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModeToggleCard(
            isGuestMode = state.isGuestMode,
            onModeChange = viewModel::setGuestMode,
        )

        if (!state.isGuestMode) {
            LoginCard(
                state =
                    LoginCardState(
                        username = state.username,
                        password = state.password,
                        isAuthorized = state.isAuthorized,
                        isLoading = state.isLoading,
                    ),
                onUsernameChange = viewModel::setUsername,
                onPasswordChange = viewModel::setPassword,
                onLogin = viewModel::login,
                onLogout = viewModel::logout,
            )
        }

        StatusMessageCard(
            errorMessage = state.error,
            successMessage = state.successMessage,
        )

        LogExportSection(viewModel = viewModel)

        SafLogFolderSection(viewModel = viewModel)

        TestLogEntrySection(viewModel = viewModel)

        AvailabilityCheckSection(viewModel = viewModel)
    }
}
