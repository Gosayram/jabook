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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.theme.PlayerThemeColors

/**
 * Audio visualizer section for portrait layout. Shows the visualizer when permission is granted,
 * or a permission request button otherwise. Hidden on compact screens by the caller.
 */
@Composable
internal fun PlayerVisualizerSection(
    hasRecordAudioPermission: Boolean,
    isPlaying: Boolean,
    visualizerMode: Int,
    waveformData: FloatArray,
    themeColors: PlayerThemeColors?,
    onRequestRecordAudioPermission: () -> Unit,
    onInitializeVisualizer: () -> Unit,
    onSetVisualizerEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnInitializeVisualizer by rememberUpdatedState(onInitializeVisualizer)
    val currentOnSetVisualizerEnabled by rememberUpdatedState(onSetVisualizerEnabled)

    LaunchedEffect(hasRecordAudioPermission) {
        if (!hasRecordAudioPermission) {
            currentOnSetVisualizerEnabled(false)
        }
    }

    if (hasRecordAudioPermission) {
        LaunchedEffect(isPlaying, hasRecordAudioPermission) {
            if (isPlaying) {
                currentOnInitializeVisualizer()
                currentOnSetVisualizerEnabled(true)
            } else {
                currentOnSetVisualizerEnabled(false)
            }
        }

        val style =
            when (visualizerMode) {
                1 -> VisualizerStyle.BARS
                2 -> VisualizerStyle.CIRCULAR
                3 -> VisualizerStyle.MINIMAL
                else -> VisualizerStyle.WAVEFORM
            }
        AudioVisualizer(
            waveformData = waveformData,
            isPlaying = isPlaying,
            style = style,
            height = 48.dp,
            primaryColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
            secondaryColor =
                themeColors?.primaryColor?.copy(alpha = 0.5f)
                    ?: MaterialTheme.colorScheme.secondary,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        FilledTonalButton(
            onClick = onRequestRecordAudioPermission,
            modifier = modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(text = stringResource(R.string.enableVisualizer))
        }
    }
}
