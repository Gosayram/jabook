// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.designsystem.component.BookCard
import com.jabook.app.jabook.compose.domain.model.Book

/**
 * Grid list of books using Material3 LazyVerticalGrid.
 *
 * Displays books in an adaptive grid with 2 columns minimum.
 * Uses the BookCard component from the design system.
 *
 * @param books List of books to display
 * @param onBookClick Callback when a book is clicked
 * @param modifier Modifier for the grid
 */
@Composable
fun BooksList(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = books,
            key = { book -> book.id },
        ) { book ->
            BookCard(
                title = book.title,
                author = book.author,
                coverUrl = book.coverUrl,
                onClick = { onBookClick(book.id) },
            )
        }
    }
}
