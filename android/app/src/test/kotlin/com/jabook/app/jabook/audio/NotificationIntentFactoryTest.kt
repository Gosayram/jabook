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

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class NotificationIntentFactoryTest {
    @Test
    fun `player notification route includes the current book id`() {
        assertEquals("jabook://player/book%2F1", playerNotificationRoute("book/1"))
    }

    @Test
    fun `player notification route is absent without a book`() {
        assertNull(playerNotificationRoute(" "))
    }

    @Test
    fun `route encodes special characters in book id`() {
        val route = playerNotificationRoute("book 1/2&3")
        assertEquals("jabook://player/book%201%2F2%263", route)
    }

    @Test
    fun `route returns null for blank id`() {
        assertNull(playerNotificationRoute(""))
    }

    @Test
    fun `route returns null for null id`() {
        assertNull(playerNotificationRoute(null))
    }

    @Test
    fun `route handles normal alphanumeric id`() {
        assertEquals("jabook://player/abc123", playerNotificationRoute("abc123"))
    }
}
