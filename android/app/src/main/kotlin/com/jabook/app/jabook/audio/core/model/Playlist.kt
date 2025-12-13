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

package com.jabook.app.jabook.audio.core.model

/**
 * Represents a playlist of chapters for an audiobook.
 */
data class Playlist(
    val bookId: String,
    val bookTitle: String,
    val chapters: List<Chapter>,
    val currentIndex: Int = 0,
) {
    /**
     * Returns the current chapter, if available.
     */
    val currentChapter: Chapter?
        get() = chapters.getOrNull(currentIndex)

    /**
     * Returns the total number of chapters.
     */
    val size: Int
        get() = chapters.size

    /**
     * Checks if there is a next chapter.
     */
    fun hasNext(): Boolean = currentIndex < chapters.size - 1

    /**
     * Checks if there is a previous chapter.
     */
    fun hasPrevious(): Boolean = currentIndex > 0

    /**
     * Returns the next chapter index, or null if there is no next chapter.
     */
    fun getNextIndex(): Int? = if (hasNext()) currentIndex + 1 else null

    /**
     * Returns the previous chapter index, or null if there is no previous chapter.
     */
    fun getPreviousIndex(): Int? = if (hasPrevious()) currentIndex - 1 else null

    /**
     * Returns the chapter at the specified index, or null if out of bounds.
     */
    fun getChapter(index: Int): Chapter? = chapters.getOrNull(index)

    /**
     * Creates a copy of this playlist with a new current index.
     */
    fun withCurrentIndex(index: Int): Playlist {
        require(index in chapters.indices) { "Index $index is out of bounds for playlist of size ${chapters.size}" }
        return copy(currentIndex = index)
    }
}
