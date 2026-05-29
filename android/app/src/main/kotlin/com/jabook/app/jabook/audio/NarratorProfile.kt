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

package com.jabook.app.jabook.audio

/**
 * P-73: Narrator profile data model.
 *
 * Audiobook users often choose books by narrator, not just author.
 * This data class represents a narrator's profile aggregated from
 * book metadata.
 *
 * @property id Unique identifier (hash of name)
 * @property name Narrator's display name
 * @property photoUri Optional photo URI
 * @property booksCount Number of books narrated
 * @property averageRating Average rating across narrated books
 * @property genres List of genres the narrator works in
 * @property description Optional bio/description
 */
public data class NarratorProfile(
    val id: String,
    val name: String,
    val photoUri: String? = null,
    val booksCount: Int = 0,
    val averageRating: Float? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
) {
    /**
     * Whether this narrator has a significant catalog.
     */
    public fun isProlific(): Boolean = booksCount >= 5

    /**
     * Returns a display-friendly label.
     */
    public fun toLabel(): String = if (booksCount > 0) "$name ($booksCount книг)" else name

    public companion object {
        /**
         * Creates a profile from a narrator name.
         */
        public fun fromName(name: String): NarratorProfile =
            NarratorProfile(
                id = name.hashCode().toString(),
                name = name,
            )

        /**
         * Aggregates multiple book entries into narrator profiles.
         *
         * @param narratorNames List of narrator names from book metadata
         * @return List of narrator profiles sorted by book count
         */
        public fun aggregate(narratorNames: List<String>): List<NarratorProfile> =
            narratorNames
                .groupBy { it }
                .map { (name, occurrences) ->
                    NarratorProfile(
                        id = name.hashCode().toString(),
                        name = name,
                        booksCount = occurrences.size,
                    )
                }.sortedByDescending { it.booksCount }
    }
}
