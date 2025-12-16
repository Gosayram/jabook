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

package com.jabook.app.jabook.compose.feature.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jabook.app.jabook.compose.data.debug.AuthDebugInfo
import com.jabook.app.jabook.compose.data.debug.ValidationResults
import com.jabook.app.jabook.compose.data.debug.toIcon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Card

/**
 * Debug screen with tabs for Logs, Mirrors, Cache, and RuTracker Diagnostics.
 * Allows users to view and export debug information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.logsTab),
        stringResource(R.string.rutrackerTab),
        stringResource(R.string.mirrorsTooltip),
        stringResource(R.string.cacheSectionTitle)
    )

    val uiState by viewModel.uiState.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error messages
    LaunchedEffect(uiState) {
        if (uiState is DebugUiState.Error) {
            snackbarHostState.showSnackbar((uiState as DebugUiState.Error).message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navDebugText)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDebugData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                // Show Share FAB only on Logs tab
                FloatingActionButton(onClick = { viewModel.shareLogs() }) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.shareLogs))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Tab Row
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> LogsTab(logs, uiState)
                1 -> RutrackerTab(viewModel)
                2 -> MirrorsTab(viewModel)
                3 -> CacheTab()
            }
        }
    }
}

@Composable
private fun LogsTab(
    logs: String,
    uiState: DebugUiState,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        when (uiState) {
            is DebugUiState.Loading -> {
                Text(stringResource(R.string.loadingLogs))
            }
            is DebugUiState.Error -> {
                Text(
                    "Error: ${uiState.message}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                if (logs.isEmpty()) {
                    Text(stringResource(R.string.noLogsAvailable))
                } else {
                    Text(
                        text = logs,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MirrorsTab(viewModel: DebugViewModel) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(stringResource(R.string.mirrorsTestingComingSoon))
        // TODO: Implement mirrors list and testing
    }
}

@Composable
private fun CacheTab() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(stringResource(R.string.cacheStatisticsComingSoon))
        // TODO: Implement cache statistics
    }
}

@Composable
private fun RutrackerTab(viewModel: DebugViewModel) {
    val authInfo by viewModel.authDebugInfo.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Auth Status
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.authStatusTitle),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.padding(4.dp))
                
                Text(
                    text = if (authInfo?.isAuthenticated == true) 
                        stringResource(R.string.authenticated) 
                    else 
                        stringResource(R.string.notAuthenticated)
                )
                
                authInfo?.lastAuthError?.let { error ->
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = stringResource(R.string.lastErrorPrefix, error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.padding(8.dp))
        
        // Validation Results
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.validationResultsTitle),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.padding(4.dp))
                
                Text(stringResource(R.string.profilePageCheck, authInfo?.validationResults?.profilePageCheck.toIcon()))
                Text(stringResource(R.string.searchPageCheck, authInfo?.validationResults?.searchPageCheck.toIcon()))
                Text(stringResource(R.string.indexPageCheck, authInfo?.validationResults?.indexPageCheck.toIcon()))
            }
        }
        
        Spacer(modifier = Modifier.padding(8.dp))
        
        // Mirror Connectivity
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.mirrorConnectivityTitle),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.padding(4.dp))
                
                authInfo?.mirrorConnectivity?.forEach { (mirror, isReachable) ->
                    Row {
                        Text(
                            text = "${if (isReachable) "✅" else "❌"} $mirror",
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.padding(16.dp))
        
        // Refresh button
        Button(
            onClick = { viewModel.refreshAuthDebugInfo() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text(stringResource(R.string.refresh))
        }
    }
}
