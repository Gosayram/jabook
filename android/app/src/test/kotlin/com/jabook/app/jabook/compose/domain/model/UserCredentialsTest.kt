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
