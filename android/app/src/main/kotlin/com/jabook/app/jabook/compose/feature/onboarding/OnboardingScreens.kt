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

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.feature.permissions.PermissionScreen
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Main entry point for the onboarding flow.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    isBeta: Boolean = false,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            onFinish()
        }
    }

    androidx.compose.animation.Crossfade(
        targetState = uiState.currentStep,
        label = "OnboardingStepTransition",
        animationSpec = tween(700),
    ) { step ->
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
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
    val imageRes = if (isBeta) R.drawable.onboarding_welcome_beta else R.drawable.onboarding_welcome_prod

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Cinematic Gradient Overlay for readability
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.8f),
                                ),
                            startY = 0f,
                        ),
                    ),
        )

        // Content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.onboardingWelcomeTitle),
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        shadow =
                            Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = Offset(2f, 4f),
                                blurRadius = 8f,
                            ),
                    ),
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.onboardingWelcomeSubtitle),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        shadow =
                            Shadow(
                                color = Color.Black.copy(alpha = 0.3f),
                                offset = Offset(1f, 2f),
                                blurRadius = 4f,
                            ),
                    ),
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onNext,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.onboardingGetStarted),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
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

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // Calculate absolute offset for this page
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            FeaturePage(page, isBeta, pageOffset)
        }

        // Navigation and Indicators overlay
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.onboardingBack))
                }

                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            label = "IndicatorWidth",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .height(8.dp)
                                    .width(width)
                                    .background(
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        shape = CircleShape,
                                    ),
                        )
                    }
                }

                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onNext()
                        }
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White,
                        ),
                ) {
                    Text(
                        text =
                            if (pagerState.currentPage < 2) {
                                stringResource(R.string.onboardingNext)
                            } else {
                                stringResource(R.string.onboardingFinish)
                            },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePage(
    page: Int,
    isBeta: Boolean,
    offset: Float,
) {
    val imageRes =
        when (page) {
            0 -> if (isBeta) R.drawable.onboarding_features_beta else R.drawable.onboarding_features_prod
            1 -> if (isBeta) R.drawable.onboarding_themes_beta else R.drawable.onboarding_themes_prod
            else -> if (isBeta) R.drawable.onboarding_advanced_beta else R.drawable.onboarding_advanced_prod
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Background with 3D-like parallax and tilt
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Parallax effect: shift background slightly
                        translationX = -offset * size.width * 0.2f
                        // 3D Tilt effect
                        rotationY = offset * -20f // Inverted for more natural feel
                        scaleX = 1.1f
                        scaleY = 1.1f
                        // Better 3D perspective
                        cameraDistance = 8f * density
                    },
            contentScale = ContentScale.Crop,
        )

        // Cinematic Gradient Overlay
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.95f),
                                ),
                            startY = 200f,
                        ),
                    ),
        )

        // Content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(titleRes),
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        shadow =
                            Shadow(
                                color = Color.Black.copy(alpha = 0.6f),
                                offset = Offset(2f, 4f),
                                blurRadius = 10f,
                            ),
                    ),
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.graphicsLayer {
                        val alphaVal = (1f - offset.absoluteValue * 1.5f).coerceIn(0f, 1f)
                        alpha = alphaVal
                        translationX = offset * size.width * 0.1f
                        scaleX = 0.9f + alphaVal * 0.1f
                        scaleY = 0.9f + alphaVal * 0.1f
                    },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(descRes),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        shadow =
                            Shadow(
                                color = Color.Black.copy(alpha = 0.4f),
                                offset = Offset(1f, 2f),
                                blurRadius = 5f,
                            ),
                    ),
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .graphicsLayer {
                            val alphaVal = (1f - offset.absoluteValue * 2f).coerceIn(0f, 1f)
                            alpha = alphaVal
                            translationX = offset * size.width * 0.15f
                            translationY = offset.absoluteValue * 30f
                        }.padding(horizontal = 24.dp),
            )

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun OnboardingPermissionStep(
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PermissionScreen(
            onPermissionsGranted = onNext,
            onSkip = onNext,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun animateDpAsState(
    targetValue: androidx.compose.ui.unit.Dp,
    label: String,
): State<androidx.compose.ui.unit.Dp> =
    androidx.compose.animation.core.animateDpAsState(
        targetValue = targetValue,
        animationSpec = tween(300),
        label = label,
    )
