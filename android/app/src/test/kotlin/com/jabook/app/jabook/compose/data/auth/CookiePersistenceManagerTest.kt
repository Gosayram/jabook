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

package com.jabook.app.jabook.compose.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiePersistenceManagerTest {
    @Test
    fun `captured session is restricted to the trusted HTTPS host`() {
        val cookies = captureWebViewCookies("https://rutracker.org/forum/login.php", "bb_session=token; other=value")

        assertTrue(cookies.isNotEmpty())
        val bbSession = cookies.first { it.name == "bb_session" }
        assertEquals("rutracker.org", bbSession.domain)
        assertFalse(bbSession.hostOnly)
        assertTrue(bbSession.secure)
        assertTrue(bbSession.httpOnly)
    }

    @Test
    fun `captures cloudflare cookies`() {
        val cookies = captureWebViewCookies("https://rutracker.org/forum/", "__cf_bm=abc123; cf_clearance=xyz789; bb_session=tok")

        assertTrue(cookies.any { it.name == "__cf_bm" })
        assertTrue(cookies.any { it.name == "cf_clearance" })
        assertTrue(cookies.any { it.name == "bb_session" })
    }
}
