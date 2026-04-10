package com.jabook.app.jabook.compose.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolutionResolverTest {
    @Test
    fun `shouldUseIncoming returns true when local does not exist`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_LOCAL,
                localExists = false,
                localTimestamp = 100L,
                incomingTimestamp = 10L,
            )

        assertTrue(result)
    }

    @Test
    fun `keep local rejects incoming when local exists`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_LOCAL,
                localExists = true,
                localTimestamp = 200L,
                incomingTimestamp = 300L,
            )

        assertFalse(result)
    }

    @Test
    fun `keep remote always accepts incoming when local exists`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_REMOTE,
                localExists = true,
                localTimestamp = 500L,
                incomingTimestamp = 100L,
            )

        assertTrue(result)
    }

    @Test
    fun `keep newer accepts incoming when incoming is newer`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_NEWER,
                localExists = true,
                localTimestamp = 100L,
                incomingTimestamp = 101L,
            )

        assertTrue(result)
    }

    @Test
    fun `keep newer rejects incoming when local is newer`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_NEWER,
                localExists = true,
                localTimestamp = 102L,
                incomingTimestamp = 101L,
            )

        assertFalse(result)
    }
}
