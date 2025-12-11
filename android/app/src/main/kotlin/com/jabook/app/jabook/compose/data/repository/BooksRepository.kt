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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for books data.
 *
 * Follows the Repository pattern from Android Architecture Components.
 * Provides a clean API for data access to ViewModels.
 */
interface BooksRepository {
    /**
     * Get all books as a Flow.
     * The Flow will emit whenever the underlying data changes.
     */
    fun getAllBooks(): Flow<List<Book>>

    /**
     * Get a single book by ID.
     * Returns null if the book doesn't exist.
     */
    fun getBook(bookId: String): Flow<Book?>

    /**
     * Get chapters for a specific book.
     */
    fun getChapters(bookId: String): Flow<List<Chapter>>

    /**
     * Search books by title or author.
     */
    fun searchBooks(query: String): Flow<List<Book>>

    /**
     * Add a new book with its chapters.
     */
    suspend fun addBook(book: Book)

    /**
     * Update an existing book.
     */
    suspend fun updateBook(book: Book)

    /**
     * Update playback position for a book.
     */
    suspend fun updatePlaybackPosition(
        bookId: String,
        position: Long,
        chapterIndex: Int,
    )

    /**
     * Update download progress for a book.
     */
    suspend fun updateDownloadProgress(
        bookId: String,
        progress: Float,
        isComplete: Boolean,
    )

    /**
     * Delete a book.
     */
    suspend fun deleteBook(bookId: String)

    /**
     * Refresh books from remote source (if applicable).
     * For now, this is a no-op as books are managed locally.
     */
    suspend fun refresh()
}
