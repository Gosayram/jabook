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

package com.jabook.app.jabook.compose.feature.indexing

/**
 * Pure mapping between the persisted forum-selection string
 * (`selected_forum_ids`, comma-separated; blank = ALL forums) and the
 * checkbox state shown in the settings forum selector.
 *
 * Persistence quirk preserved: an empty stored string means "all forums",
 * so checking every checkbox normalizes back to blank, and unchecking the
 * last checkbox yields blank too — the worker then indexes everything
 * rather than nothing (offline search stays usable).
 */
public object ForumSelection {
    /**
     * Checkbox state for a stored value: blank → every forum checked.
     */
    public fun checkedIds(
        stored: String,
        allForumIds: List<String>,
    ): Set<String> =
        if (stored.isBlank()) {
            allForumIds.toSet()
        } else {
            stored
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        }

    /**
     * Toggle one checkbox. Toggling a forum out of the implicit "all" state
     * materializes the explicit complement first.
     */
    public fun toggle(
        currentChecked: Set<String>,
        forumId: String,
    ): Set<String> = if (forumId in currentChecked) currentChecked - forumId else currentChecked + forumId

    /**
     * Checkbox state → stored string. All-checked normalizes to blank
     * (canonical "all"); output preserves [allForumIds] ordering.
     */
    public fun toStored(
        checked: Set<String>,
        allForumIds: List<String>,
    ): String {
        val ordered = allForumIds.filter { it in checked }
        return if (ordered.size == allForumIds.size) "" else ordered.joinToString(",")
    }
}
