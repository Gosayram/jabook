package com.jabook.app.shared.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.TransformOrigin

/**
 * JaBook animation system following Material Design 3 motion principles. Provides consistent animations throughout the app with proper
 * timing and easing.
 */
object JaBookAnimations {
    // Standard durations following Material Design 3
    const val DURATION_SHORT = 150
    const val DURATION_MEDIUM = 300
    const val DURATION_LONG = 500
    const val DURATION_EXTRA_LONG = 700

    // Easing curves
    val EMPHASIZED_EASING = FastOutSlowInEasing
    val STANDARD_EASING = LinearOutSlowInEasing

    // Standard animation specs
    val shortAnimationSpec = tween<Float>(durationMillis = DURATION_SHORT, easing = STANDARD_EASING)

    val mediumAnimationSpec = tween<Float>(durationMillis = DURATION_MEDIUM, easing = EMPHASIZED_EASING)

    val longAnimationSpec = tween<Float>(durationMillis = DURATION_LONG, easing = EMPHASIZED_EASING)

    val springAnimationSpec =
        spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    // Enter/Exit transitions for screen navigation
    val screenEnterTransition: EnterTransition =
        slideInVertically(animationSpec = tween(DURATION_MEDIUM, easing = EMPHASIZED_EASING), initialOffsetY = { it / 3 }) +
            fadeIn(animationSpec = tween(DURATION_MEDIUM))

    val screenExitTransition: ExitTransition =
        slideOutVertically(animationSpec = tween(DURATION_SHORT, easing = STANDARD_EASING), targetOffsetY = { -it / 3 }) +
            fadeOut(animationSpec = tween(DURATION_SHORT))

    // Dialog animations
    val dialogEnterTransition: EnterTransition =
        scaleIn(
            animationSpec = tween(DURATION_MEDIUM, easing = EMPHASIZED_EASING),
            initialScale = 0.8f,
            transformOrigin = TransformOrigin.Center,
        ) + fadeIn(animationSpec = tween(DURATION_MEDIUM))

    val dialogExitTransition: ExitTransition =
        scaleOut(
            animationSpec = tween(DURATION_SHORT, easing = STANDARD_EASING),
            targetScale = 0.8f,
            transformOrigin = TransformOrigin.Center,
        ) + fadeOut(animationSpec = tween(DURATION_SHORT))

    // Bottom sheet animations
    val bottomSheetEnterTransition: EnterTransition =
        slideInVertically(animationSpec = tween(DURATION_MEDIUM, easing = EMPHASIZED_EASING), initialOffsetY = { it }) +
            fadeIn(animationSpec = tween(DURATION_MEDIUM))

    val bottomSheetExitTransition: ExitTransition =
        slideOutVertically(animationSpec = tween(DURATION_SHORT, easing = STANDARD_EASING), targetOffsetY = { it }) +
            fadeOut(animationSpec = tween(DURATION_SHORT))

    // List item animations
    val listItemEnterTransition: EnterTransition =
        expandVertically(animationSpec = tween(DURATION_MEDIUM, easing = EMPHASIZED_EASING)) +
            fadeIn(animationSpec = tween(DURATION_MEDIUM))

    val listItemExitTransition: ExitTransition =
        shrinkVertically(animationSpec = tween(DURATION_SHORT, easing = STANDARD_EASING)) + fadeOut(animationSpec = tween(DURATION_SHORT))

    // Player control animations
    val playerControlEnterTransition: EnterTransition =
        scaleIn(animationSpec = springAnimationSpec, initialScale = 0.9f) + fadeIn(animationSpec = tween(DURATION_SHORT))

    val playerControlExitTransition: ExitTransition =
        scaleOut(animationSpec = tween(DURATION_SHORT, easing = STANDARD_EASING), targetScale = 0.9f) +
            fadeOut(animationSpec = tween(DURATION_SHORT))
}

/** Transition for play/pause button state changes */
@Composable
fun playPauseTransition(isPlaying: Boolean): Transition<Boolean> = updateTransition(targetState = isPlaying, label = "playPauseTransition")

/** Scale animation for play/pause button */
@Composable
fun Transition<Boolean>.animatePlayPauseScale(): State<Float> =
    animateFloat(transitionSpec = { JaBookAnimations.springAnimationSpec }, label = "playPauseScale") { playing ->
        if (playing) 1.1f else 1.0f
    }

/** Loading state transition */
@Composable
fun loadingTransition(isLoading: Boolean): Transition<Boolean> = updateTransition(targetState = isLoading, label = "loadingTransition")

/** Fade animation for loading states */
@Composable
fun Transition<Boolean>.animateLoadingAlpha(): State<Float> =
    animateFloat(transitionSpec = { JaBookAnimations.mediumAnimationSpec }, label = "loadingAlpha") { loading ->
        if (loading) 0.6f else 1.0f
    }

/** Progress bar animation */
@Composable
fun progressTransition(progress: Float): Transition<Float> = updateTransition(targetState = progress, label = "progressTransition")

/** Smooth progress animation */
@Composable
fun Transition<Float>.animateProgressValue(): State<Float> =
    animateFloat(transitionSpec = {
        JaBookAnimations.mediumAnimationSpec
    }, label = "progressValue") { it }

/** Download status transition */
enum class DownloadAnimationState {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR,
}

@Composable
fun downloadStatusTransition(status: DownloadAnimationState): Transition<DownloadAnimationState> =
    updateTransition(targetState = status, label = "downloadStatusTransition")

/** Color animation for download status */
@Composable
fun Transition<DownloadAnimationState>.animateDownloadColor(): State<Float> =
    animateFloat(transitionSpec = { JaBookAnimations.mediumAnimationSpec }, label = "downloadColor") { state ->
        when (state) {
            DownloadAnimationState.IDLE -> 0f
            DownloadAnimationState.DOWNLOADING -> 1f
            DownloadAnimationState.PAUSED -> 0.5f
            DownloadAnimationState.COMPLETED -> 1f
            DownloadAnimationState.ERROR -> 0.8f
        }
    }

/** Bookmark animation state */
@Composable
fun bookmarkTransition(isBookmarked: Boolean): Transition<Boolean> =
    updateTransition(targetState = isBookmarked, label = "bookmarkTransition")

/** Scale animation for bookmark button */
@Composable
fun Transition<Boolean>.animateBookmarkScale(): State<Float> =
    animateFloat(transitionSpec = { JaBookAnimations.springAnimationSpec }, label = "bookmarkScale") { bookmarked ->
        if (bookmarked) 1.2f else 1.0f
    }

/** Favorite animation state */
@Composable
fun favoriteTransition(isFavorite: Boolean): Transition<Boolean> = updateTransition(targetState = isFavorite, label = "favoriteTransition")

/** Scale animation for favorite button */
@Composable
fun Transition<Boolean>.animateFavoriteScale(): State<Float> =
    animateFloat(transitionSpec = { JaBookAnimations.springAnimationSpec }, label = "favoriteScale") { favorite ->
        if (favorite) 1.15f else 1.0f
    }
