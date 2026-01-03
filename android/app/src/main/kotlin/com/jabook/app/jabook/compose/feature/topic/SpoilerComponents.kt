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

package com.jabook.app.jabook.compose.feature.topic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R

@Composable
internal fun DescriptionBlockRenderer(
    block: com.jabook.app.jabook.compose.core.util.DescriptionBlock,
    onNavigateToTopic: (String) -> Unit,
) {
    when (block) {
        is com.jabook.app.jabook.compose.core.util.DescriptionBlock.Text -> {
            val textWithListeners =
                remember(block.content) {
                    val builder =
                        androidx.compose.ui.text.AnnotatedString
                            .Builder(block.content)
                    block.content.getStringAnnotations("TOPIC_ID", 0, block.content.length).forEach { range ->
                        builder.addLink(
                            androidx.compose.ui.text.LinkAnnotation.Clickable(
                                tag = range.item,
                                linkInteractionListener = { _ -> onNavigateToTopic(range.item) },
                            ),
                            range.start,
                            range.end,
                        )
                    }
                    builder.toAnnotatedString()
                }

            Text(
                text = textWithListeners,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            )
        }
        is com.jabook.app.jabook.compose.core.util.DescriptionBlock.Spoiler -> {
            ExpandableSpoiler(block, onNavigateToTopic)
        }
    }
}

@Composable
internal fun ExpandableSpoiler(
    spoiler: com.jabook.app.jabook.compose.core.util.DescriptionBlock.Spoiler,
    onNavigateToTopic: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        // Spoiler Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = spoiler.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Spoiler Content
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                spoiler.content.forEach { block ->
                    DescriptionBlockRenderer(block, onNavigateToTopic)
                }
            }
        }
    }
}
