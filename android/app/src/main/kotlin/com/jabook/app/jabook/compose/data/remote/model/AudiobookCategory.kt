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

package com.jabook.app.jabook.compose.data.remote.model

/**
 * Represents a category of audiobooks on RuTracker.
 *
 * @property id Unique identifier (forum ID)
 * @property name Category name
 * @property url URL to category page
 * @property subcategories List of subcategories (nested forums)
 */
public data class AudiobookCategory(
    val id: String,
    val name: String,
    val url: String,
    val subcategories: List<AudiobookCategory> = emptyList(),
)
