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

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterAutoScrollPolicyTest {
    @Test
    fun `resolve returns none when target already visible`() {
        val action =
            ChapterAutoScrollPolicy.resolve(
                targetIndex = 6,
                firstVisibleIndex = 4,
                lastVisibleIndex = 10,
            )

        assertEquals(ChapterAutoScrollPolicy.ScrollAction.NONE, action)
    }

    @Test
    fun `resolve returns animate for nearby offscreen target`() {
        val action =
            ChapterAutoScrollPolicy.resolve(
                targetIndex = 12,
                firstVisibleIndex = 4,
                lastVisibleIndex = 10,
            )

        assertEquals(ChapterAutoScrollPolicy.ScrollAction.ANIMATE, action)
    }

    @Test
    fun `resolve returns animate when edge distance equals snap threshold`() {
        val action =
            ChapterAutoScrollPolicy.resolve(
                targetIndex = 30,
                firstVisibleIndex = 10,
                lastVisibleIndex = 20,
            )

        assertEquals(ChapterAutoScrollPolicy.ScrollAction.ANIMATE, action)
    }

    @Test
    fun `resolve returns animate for nearby target before visible range`() {
        val action =
            ChapterAutoScrollPolicy.resolve(
                targetIndex = 2,
                firstVisibleIndex = 4,
                lastVisibleIndex = 10,
            )

        assertEquals(ChapterAutoScrollPolicy.ScrollAction.ANIMATE, action)
    }

    @Test
    fun `resolve returns snap for far offscreen target`() {
        val action =
            ChapterAutoScrollPolicy.resolve(
                targetIndex = 80,
                firstVisibleIndex = 4,
                lastVisibleIndex = 10,
            )

        assertEquals(ChapterAutoScrollPolicy.ScrollAction.SNAP, action)
    }
}
