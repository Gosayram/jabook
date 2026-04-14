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

package com.jabook.app.jabook.compose.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserCredentialsTest {
    @Test
    fun `withPasswordChars returns block result and clears char array`() {
        val credentials = UserCredentials(username = "user", password = "secret")
        lateinit var leakedReference: CharArray

        val result =
            credentials.withPasswordChars { chars ->
                leakedReference = chars
                chars.concatToString()
            }

        assertEquals("secret", result)
        assertTrue(leakedReference.all { it == '\u0000' })
    }
}
