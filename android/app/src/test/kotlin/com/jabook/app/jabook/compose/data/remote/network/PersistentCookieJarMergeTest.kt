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

package com.jabook.app.jabook.compose.data.remote.network

import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PersistentCookieJarMergeTest {
    private val now = 1_000L

    @Test
    fun `response updates its cookie without discarding the session`() {
        val merged =
            mergeCookies(
                existing = listOf(cookie("bb_session", "session"), cookie("csrf", "old")),
                incoming = listOf(cookie("csrf", "new")),
                nowMillis = now,
            )

        assertEquals("session", merged.single { it.name == "bb_session" }.value)
        assertEquals("new", merged.single { it.name == "csrf" }.value)
    }

    @Test
    fun `expired response cookie removes its stored counterpart`() {
        val merged =
            mergeCookies(
                existing = listOf(cookie("bb_session", "session")),
                incoming = listOf(cookie("bb_session", "expired", expiresAt = now)),
                nowMillis = now,
            )

        assertFalse(merged.any { it.name == "bb_session" })
    }

    private fun cookie(
        name: String,
        value: String,
        expiresAt: Long = now + 60_000,
    ): Cookie =
        Cookie
            .Builder()
            .name(name)
            .value(value)
            .domain("rutracker.org")
            .path("/")
            .expiresAt(expiresAt)
            .build()
}
