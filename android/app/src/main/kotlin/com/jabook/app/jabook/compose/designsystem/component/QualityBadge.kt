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

package com.jabook.app.jabook.compose.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.AudioQualityInfo
import com.jabook.app.jabook.audio.QualityTier
import com.jabook.app.jabook.ui.theme.JabookTheme

/**
 * Reusable quality badge for codec and quality-tier display.
 *
 * Supports compact and large sizes, optional tier label, and
 * high/mid/low tier color accents for light, dark, and AMOLED.
 *
 * @param audioQuality Audio quality info to display
 * @param showTierLabel Whether to show the tier label (e.g., "Высокое")
 * @param large Whether to use the large size variant
 * @param modifier Modifier
 */
@Composable
public fun QualityBadge(
    audioQuality: AudioQualityInfo,
    showTierLabel: Boolean = false,
    large: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tierColor =
        when (audioQuality.tier) {
            QualityTier.HIGH -> MaterialTheme.colorScheme.primary
            QualityTier.STANDARD -> MaterialTheme.colorScheme.tertiary
            QualityTier.LOW -> MaterialTheme.colorScheme.error
        }
    val tierBackgroundColor =
        when (audioQuality.tier) {
            QualityTier.HIGH -> MaterialTheme.colorScheme.primaryContainer
            QualityTier.STANDARD -> MaterialTheme.colorScheme.tertiaryContainer
            QualityTier.LOW -> MaterialTheme.colorScheme.errorContainer
        }
    val tierIcon =
        when (audioQuality.tier) {
            QualityTier.HIGH -> Icons.Filled.CheckCircle
            QualityTier.STANDARD -> Icons.Filled.Info
            QualityTier.LOW -> Icons.Filled.Warning
        }
    val tierLabel =
        when (audioQuality.tier) {
            QualityTier.HIGH -> stringResource(R.string.qualityHigh)
            QualityTier.STANDARD -> stringResource(R.string.qualityStandard)
            QualityTier.LOW -> stringResource(R.string.qualityLow)
        }
    val shortLabel = audioQuality.toShortLabel()
    val contentDescriptionText =
        stringResource(
            R.string.qualityBadgeContentDescription,
            shortLabel,
            tierLabel,
        )

    val fontSize = if (large) 13.sp else 11.sp
    val iconSize = if (large) 16.dp else 12.dp
    val horizontalPadding = if (large) 10.dp else 6.dp
    val verticalPadding = if (large) 5.dp else 3.dp

    Row(
        modifier =
            modifier
                .background(
                    color = tierBackgroundColor,
                    shape = RoundedCornerShape(6.dp),
                ).padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = tierIcon,
            contentDescription = null,
            tint = tierColor,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontSize = fontSize,
            color = tierColor,
            fontWeight = FontWeight.Bold,
        )
        if (showTierLabel) {
            Text(
                text = tierLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = fontSize,
                color = tierColor.copy(alpha = 0.8f),
            )
        }
    }
}

@Preview(name = "QualityBadge HIGH")
@Composable
private fun QualityBadgeHighPreview() {
    JabookTheme {
        QualityBadge(
            audioQuality =
                AudioQualityInfo(
                    format = "FLAC",
                    bitrateKbps = 876,
                    sampleRateHz = 44100,
                    channels = 2,
                    isLossless = true,
                ),
            showTierLabel = true,
        )
    }
}

@Preview(name = "QualityBadge STANDARD")
@Composable
private fun QualityBadgeStandardPreview() {
    JabookTheme {
        QualityBadge(
            audioQuality =
                AudioQualityInfo(
                    format = "MP3",
                    bitrateKbps = 192,
                    sampleRateHz = 44100,
                    channels = 2,
                    isLossless = false,
                ),
            showTierLabel = true,
        )
    }
}

@Preview(name = "QualityBadge LOW")
@Composable
private fun QualityBadgeLowPreview() {
    JabookTheme {
        QualityBadge(
            audioQuality =
                AudioQualityInfo(
                    format = "MP3",
                    bitrateKbps = 64,
                    sampleRateHz = 22050,
                    channels = 1,
                    isLossless = false,
                ),
            showTierLabel = true,
            large = true,
        )
    }
}
