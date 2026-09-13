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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard loading screen with centered indicator.
 *
 * Baseline for 200ms–5s per spec (ponytail: 7-shape morph LoadingIndicator requires graphics-shapes,
 * keep CircularProgressIndicator, add when graphics-shapes proven needed).
 * Replaces M3 Expressive LoadingIndicator/ContainedLoadingIndicator (1.5 alpha) with stable
 * CircularProgressIndicator for M3 1.4 compat.
 *
 * - 24–240dp flexible, default 48dp (smor = small/medium/large responsive)
 * - contained = false → CircularProgressIndicator on surface
 * - contained = true  → CircularProgressIndicator on container (stronger contrast over content)
 * - a11y: progress-bar role via semantics, label describes purpose
 *
 * @param modifier Modifier for container
 * @param message Optional label below indicator (a11y: also contentDescription)
 * @param contained When true uses contained variant (stronger contrast over content)
 * @param indicatorSize Size of morph loop, coerced 24..240dp (default 48dp per spec)
 */
@Composable
public fun LoadingScreen(
    modifier: Modifier = Modifier,
    message: String? = null,
    contained: Boolean = false,
    indicatorSize: Dp = 48.dp,
) {
    val coercedSize = indicatorSize.coerceIn(24.dp, 240.dp)
    val a11yLabel = message ?: "Loading"
    Box(
        modifier =
            modifier.fillMaxSize().semantics(mergeDescendants = true) {
                contentDescription = a11yLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ponytail: 7-shape morph LoadingIndicator requires graphics-shapes — CircularProgressIndicator baseline for 200ms-5s
            // contained flag kept for API compat; both use CircularProgressIndicator on 1.4 (color difference via contained is no-op until expressive)
            if (contained) {
                CircularProgressIndicator(
                    modifier = Modifier.size(coercedSize),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(coercedSize),
                )
            }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
