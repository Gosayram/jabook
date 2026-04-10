package com.jabook.app.jabook.compose.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RutrackerCertificatePinningPolicyTest {
    @Test
    fun `hostPins include all default mirrors with at least two pins`() {
        assertEquals(
            setOf("rutracker.org", "rutracker.net", "rutracker.me"),
            RutrackerCertificatePinningPolicy.pinnedHosts,
        )
        RutrackerCertificatePinningPolicy.hostPins.values.forEach { pins ->
            assertTrue("Each host should include leaf + backup pin", pins.size >= 2)
            pins.forEach { pin ->
                assertTrue(pin.startsWith("sha256/"))
            }
        }
    }
}
