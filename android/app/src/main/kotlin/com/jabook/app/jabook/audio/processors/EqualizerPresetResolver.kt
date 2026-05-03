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

package com.jabook.app.jabook.audio.processors

/**
 * Policy class that resolves which [EqualizerPreset] to use for a given book.
 *
 * Resolution order:
 * 1. Per-book override (if set)
 * 2. Global default from user preferences
 *
 * This follows the Policy pattern: the resolver encapsulates the decision logic,
 * keeping it testable and separate from the audio engine.
 *
 * ## Usage
 * ```kotlin
 * val resolver = EqualizerPresetResolver(
 *     getGlobalPreset = { settingsRepository.userPreferences.map { it.equalizerPreset } },
 *     getBookOverride = { bookId -> booksDao.getEqPresetOverride(bookId) },
 * )
 * val preset = resolver.resolve(bookId = "some-book-id")
 * ```
 *
 * TASK-VERM-11: Per-book EQ override
 */
public class EqualizerPresetResolver(
    private val getGlobalPreset: () -> EqualizerPreset,
    private val getBookOverride: (bookId: String) -> String?,
) {
    /**
     * Resolves the effective EQ preset for a given book.
     *
     * @param bookId The unique identifier for the book (typically its directory path).
     * @return The resolved [EqualizerPreset] to apply.
     */
    public fun resolve(bookId: String): EqualizerPreset {
        val overrideName = getBookOverride(bookId)
        if (overrideName != null) {
            val overridePreset = mapPresetName(overrideName)
            // Only use override if it's not DEFAULT (which means "use global")
            if (overridePreset != EqualizerPreset.DEFAULT || overrideName == "FLAT") {
                return overridePreset
            }
        }
        return getGlobalPreset()
    }

    /**
     * Checks if a book has an EQ override set.
     *
     * @param bookId The book identifier.
     * @return true if an override is set for this book.
     */
    public fun hasOverride(bookId: String): Boolean = getBookOverride(bookId) != null

    public companion object {
        /**
         * Sentinel value stored in DB to indicate "use global preset".
         * When this is the override value, the resolver falls through to global.
         */
        public const val USE_GLOBAL: String = "USE_GLOBAL"
    }
}
