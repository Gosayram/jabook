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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.feature.permissions.PermissionScreen

/**
 * Main entry point for the onboarding flow.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    isBeta: Boolean = false,
    viewModel: OnboardingViewModel =
        androidx.hilt.lifecycle.viewmodel.compose
            .hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            onFinish()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Crossfade(targetState = uiState.currentStep, label = "OnboardingTransition") { step ->
            when (step) {
                OnboardingStep.WELCOME ->
                    WelcomeStep(
                        isBeta = isBeta,
                        onNext = { viewModel.nextStep() },
                    )
                OnboardingStep.FEATURES ->
                    FeaturesStep(
                        isBeta = isBeta,
                        onNext = { viewModel.nextStep() },
                        onBack = { viewModel.previousStep() },
                    )
                OnboardingStep.PERMISSIONS ->
                    OnboardingPermissionStep(
                        onNext = { viewModel.finishOnboarding() },
                        onBack = { viewModel.previousStep() },
                    )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    isBeta: Boolean,
    onNext: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val imageRes = if (isBeta) R.drawable.onboarding_welcome_beta else R.drawable.onboarding_welcome_prod

        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.onboardingWelcomeTitle),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboardingWelcomeSubtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onNext,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
        ) {
            Text(stringResource(R.string.onboardingGetStarted))
        }
    }
}

@Composable
private fun FeaturesStep(
    isBeta: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) { page ->
            FeaturePage(page, isBeta)
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.onboardingBack))
            }

            // Page indicators
            Row {
                repeat(3) { index ->
                    val color =
                        if (pagerState.currentPage == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                                .padding(2.dp)
                                .size(8.dp)
                                .background(
                                    color = color,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ),
                    )
                }
            }

            Button(onClick = onNext) {
                Text(stringResource(R.string.onboardingNext))
            }
        }
    }
}

@Composable
private fun FeaturePage(
    page: Int,
    isBeta: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val imageRes =
            when (page) {
                0 -> if (isBeta) R.drawable.onboarding_features_beta else R.drawable.onboarding_features_prod
                1 -> if (isBeta) R.drawable.onboarding_welcome_beta else R.drawable.onboarding_welcome_prod
                else -> if (isBeta) R.drawable.onboarding_features_beta else R.drawable.onboarding_features_prod
            }

        val titleRes =
            when (page) {
                0 -> R.string.onboardingFeatureDiscoveryTitle
                1 -> R.string.onboardingFeatureThemesTitle
                else -> R.string.onboardingFeatureAdvancedTitle
            }

        val descRes =
            when (page) {
                0 -> R.string.onboardingFeatureDiscoveryDesc
                1 -> R.string.onboardingFeatureThemesDesc
                else -> R.string.onboardingFeatureAdvancedDesc
            }

        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(250.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(descRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingPermissionStep(
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            PermissionScreen(onPermissionsGranted = onNext)
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.onboardingBack))
            }

            TextButton(onClick = onNext) {
                Text(stringResource(R.string.onboardingSkip))
            }
        }
    }
}
