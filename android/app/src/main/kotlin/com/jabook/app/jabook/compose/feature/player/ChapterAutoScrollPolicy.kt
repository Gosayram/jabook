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

package com.jabook.app.jabook.compose.feature.player

internal object ChapterAutoScrollPolicy {
    private const val SNAP_DISTANCE: Int = 20

    internal enum class ScrollAction {
        NONE,
        ANIMATE,
        SNAP,
    }

    internal fun resolve(
        targetIndex: Int,
        firstVisibleIndex: Int,
        lastVisibleIndex: Int,
    ): ScrollAction =
        when {
            targetIndex in firstVisibleIndex..lastVisibleIndex -> ScrollAction.NONE
            minOf(
                kotlin.math.abs(targetIndex - firstVisibleIndex),
                kotlin.math.abs(targetIndex - lastVisibleIndex),
            ) <= SNAP_DISTANCE -> ScrollAction.ANIMATE
            else -> ScrollAction.SNAP
        }
}
