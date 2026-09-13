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

package com.jabook.app.jabook.compose.data.repository

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * JSON codec for the indexing page-cursor map (forumId → next page to crawl),
 * persisted as a single proto string field. Corrupt/legacy payloads decode to
 * an empty map — cursors are an optimization, never a source of truth.
 */
internal object IndexingPageCursorsJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), Int.serializer())

    public fun decode(raw: String?): Map<String, Int> =
        if (raw.isNullOrBlank()) {
            emptyMap()
        } else {
            runCatching { json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
        }

    public fun encode(cursors: Map<String, Int>): String = json.encodeToString(mapSerializer, cursors)
}
