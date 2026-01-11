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

package com.jabook.app.jabook.compose.feature.player.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
public fun LyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Find current line index (last line that started before currentPosition)
    val currentIndex =
        remember(currentPosition, lyrics) {
            lyrics.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
        }

    // Auto-scroll logic
    LaunchedEffect(currentIndex) {
        // Offset to try centering the item.
        // Logic: Screen height / 2. This is approximate.
        // We assume item height is ~60dp.
        // listState.layoutInfo is not stable during scroll.
        // We simply scroll to index.
        try {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -with(density) { 150.dp.toPx() }.roundToInt(), // Offset to keep it somewhat centered
            )
        } catch (e: Exception) {
            // Ignore scroll errors
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isCurrent = index == currentIndex
            val alpha = if (isCurrent) 1f else 0.5f
            val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            // Dynamic text resizing
            val fontSize = if (isCurrent) 24.sp else 18.sp

            Text(
                text = line.text,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.timeMs) }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        textAlign = TextAlign.Center,
                    ),
                color = Color.White.copy(alpha = alpha),
            )
        }
    }
}
