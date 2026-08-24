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
import org.junit.Assert.assertNull
import org.junit.Test

class CookieMemoryCacheTest {
    @Test
    fun `store snapshots caller list and load returns an independent snapshot`() {
        val cache = CookieMemoryCache()
        val cookies = mutableListOf(cookie(name = "session", value = "first"))

        cache.store("mirror.example", cookies)
        cookies += cookie(name = "other", value = "mutated")

        val loaded = cache.load("mirror.example")

        assertEquals(listOf(cookie(name = "session", value = "first")), loaded)
    }

    @Test
    fun `clear removes all host snapshots`() {
        val cache = CookieMemoryCache()
        cache.store("mirror.example", listOf(cookie(name = "session", value = "value")))

        cache.clear()

        assertNull(cache.load("mirror.example"))
    }

    private fun cookie(
        name: String,
        value: String,
    ): Cookie =
        Cookie
            .Builder()
            .name(name)
            .value(value)
            .domain("mirror.example")
            .build()
}
