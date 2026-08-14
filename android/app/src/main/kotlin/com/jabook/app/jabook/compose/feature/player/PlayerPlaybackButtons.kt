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

package com.jabook.app.jabook.compose.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.designsystem.component.CircularIconButton

/**
 * Primary playback control buttons: skip previous, seek backward, play/pause, seek forward, skip next.
 * Used in both portrait and landscape layouts.
 */
@Composable
internal fun PlayerPlaybackButtons(
    isPlaying: Boolean,
    rewindInterval: Int,
    forwardInterval: Int,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    isCompact: Boolean,
    playPauseButtonScale: Float = 1f,
    playPauseIconScale: Float = 1f,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onPrimaryColor: Color = MaterialTheme.colorScheme.onPrimary,
    modifier: Modifier = Modifier,
) {
    val skipButtonSize = if (isCompact) 56.dp else 64.dp
    val seekButtonSize = if (isCompact) 48.dp else 56.dp
    val playPauseButtonSize = if (isCompact) 72.dp else 80.dp
    val skipIconSize = if (isCompact) 40.dp else 48.dp
    val seekIconSize = if (isCompact) 32.dp else 40.dp
    val playPauseIconSize = if (isCompact) 40.dp else 48.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularIconButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = stringResource(R.string.previousChapter),
            onClick = onSkipPrevious,
            modifier = Modifier.size(skipButtonSize),
            size = skipIconSize,
        )

        CircularIconButton(
            icon = Icons.Filled.Replay,
            contentDescription = stringResource(R.string.seekBackwardDescription, rewindInterval),
            onClick = onSeekBackward,
            modifier = Modifier.size(seekButtonSize),
            size = seekIconSize,
        )

        Spacer(modifier = Modifier.width(16.dp))

        val playbackStateDescription =
            if (isPlaying) {
                stringResource(R.string.playbackStatePlaying)
            } else {
                stringResource(R.string.playbackStatePaused)
            }

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(playPauseButtonSize * 1.2f)
                    .graphicsLayer {
                        scaleX = playPauseButtonScale
                        scaleY = playPauseButtonScale
                    },
        ) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .semantics {
                            stateDescription = playbackStateDescription
                        },
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = primaryColor,
                        contentColor = onPrimaryColor,
                    ),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription =
                        if (isPlaying) {
                            stringResource(R.string.pauseButton)
                        } else {
                            stringResource(R.string.playButton)
                        },
                    modifier =
                        Modifier
                            .size(playPauseIconSize * 1.2f)
                            .graphicsLayer {
                                scaleX = playPauseIconScale
                                scaleY = playPauseIconScale
                            },
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        CircularIconButton(
            icon = Icons.Filled.FastForward,
            contentDescription = stringResource(R.string.seekForwardDescription, forwardInterval),
            onClick = onSeekForward,
            modifier = Modifier.size(seekButtonSize),
            size = seekIconSize,
        )

        CircularIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = stringResource(R.string.nextChapter),
            onClick = onSkipNext,
            modifier = Modifier.size(skipButtonSize),
            size = skipIconSize,
        )
    }
}
