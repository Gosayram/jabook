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

package com.jabook.app.jabook.compose.feature.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.domain.model.Book
import java.time.LocalTime

@Immutable
public data class DiscoveryUiState(
    val continueListening: List<Book> = emptyList(),
    val trending: List<Book> = emptyList(),
    val personalized: List<Book> = emptyList(),
    val genres: List<DiscoveryGenre> = emptyList(),
)

public enum class ListeningMood(
    public val emoji: String,
    public val label: String,
) {
    WALKING("🚶", "Иду пешком"),
    DRIVING("🚗", "В машине"),
    SLEEPING("🛌", "Перед сном"),
    WORKOUT("🏃", "Тренировка"),
    RELAXING("☕", "Отдыхаю"),
    WORKING("💼", "Фоном за работой"),
}

@Immutable
public data class DiscoveryGenre(
    val id: String,
    val title: String,
    val color: Color,
    val coverHints: List<String> = emptyList(),
)

@Composable
public fun DiscoveryScreen(
    uiState: DiscoveryUiState,
    selectedMood: ListeningMood,
    onMoodChange: (ListeningMood) -> Unit,
    onBookClick: (Book) -> Unit,
    onGenreClick: (DiscoveryGenre) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item {
            GreetingHeader()
        }
        item {
            ListeningMoodChips(
                selectedMood = selectedMood,
                onMoodChange = onMoodChange,
            )
        }
        item {
            DiscoveryShelf(
                title = "Продолжить",
                books = uiState.continueListening,
                onBookClick = onBookClick,
            )
        }
        item {
            DiscoveryShelf(
                title = "Популярное",
                books = uiState.trending,
                onBookClick = onBookClick,
            )
        }
        item {
            DiscoveryShelf(
                title = "Для вас",
                books = uiState.personalized,
                onBookClick = onBookClick,
            )
        }
        if (uiState.genres.isNotEmpty()) {
            item {
                Text(
                    text = "Жанры",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(uiState.genres, key = { it.id }) { genre ->
                GenreTile(
                    genre = genre,
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                    onClick = { onGenreClick(genre) },
                )
            }
        }
    }
}

@Composable
private fun ListeningMoodChips(
    selectedMood: ListeningMood,
    onMoodChange: (ListeningMood) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) {
        items(ListeningMood.entries, key = { it.name }) { mood ->
            FilterChip(
                selected = selectedMood == mood,
                onClick = { onMoodChange(mood) },
                label = { Text("${mood.emoji} ${mood.label}") },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
            )
        }
    }
}

@Composable
private fun GreetingHeader() {
    val greeting =
        when (LocalTime.now().hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
    Text(
        text = greeting,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DiscoveryShelf(
    title: String,
    books: List<Book>,
    onBookClick: (Book) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(books, key = { it.id }) { book ->
            DiscoveryBookCard(
                book = book,
                onClick = { onBookClick(book) },
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun DiscoveryBookCard(
    book: Book,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(148.dp)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = book.title.take(1).ifBlank { "?" },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GenreTile(
    genre: DiscoveryGenre,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(84.dp)
                .background(genre.color, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = genre.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (genre.color.luminance() > 0.45f) Color.Black else Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
        GenreTiltedCovers(
            hints = genre.coverHints,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun GenreTiltedCovers(
    hints: List<String>,
    modifier: Modifier = Modifier,
) {
    val cards = if (hints.isNotEmpty()) hints.take(2) else listOf("A", "B")
    Box(modifier = modifier) {
        cards.forEachIndexed { index, hint ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = (22 * index).dp, bottom = 4.dp)
                        .width(46.dp)
                        .height(62.dp)
                        .shadow(4.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .rotate(15f - index * 8f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = hint.take(1).ifBlank { "?" },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
