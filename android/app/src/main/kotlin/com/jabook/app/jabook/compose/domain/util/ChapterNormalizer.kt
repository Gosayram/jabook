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

package com.jabook.app.jabook.compose.domain.util

public object ChapterNormalizer {
    /**
     * Normalizes chapter titles for a collection of chapters.
     * Preserves special titles like "Prologue", "Epilogue", "Intro", "Outro".
     * Other chapters are renamed to "Chapter X".
     *
     * @param titles List of existing chapter titles in order.
     * @return List of normalized titles.
     */
    public fun normalizeTitles(titles: List<String>): List<String> =
        titles.mapIndexed { index, title ->
            val lower = title.lowercase()
            when {
                lower.contains("prologue") || lower.contains("prolog") -> "Prologue"
                lower.contains("epilogue") || lower.contains("epilog") -> "Epilogue"
                lower.contains("intro") -> "Intro"
                lower.contains("outro") -> "Outro"
                else -> "Chapter ${index + 1}"
            }
        }
}
