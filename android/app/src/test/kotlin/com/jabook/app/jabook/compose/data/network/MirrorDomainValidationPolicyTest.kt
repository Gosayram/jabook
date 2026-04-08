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

package com.jabook.app.jabook.compose.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MirrorDomainValidationPolicy].
 *
 * Validates domain sanitization, rejection of dangerous inputs,
 * and warning detection for non-rutracker domains.
 */
class MirrorDomainValidationPolicyTest {
    // --- Sanitization ---

    @Test
    fun `bare domain passes sanitization`() {
        val result = MirrorDomainValidationPolicy.validate("rutracker.nl")
        assertTrue(result.isValid)
        assertEquals("rutracker.nl", result.sanitizedDomain)
        assertFalse(result.isWarning)
        assertNull(result.rejectionReason)
    }

    @Test
    fun `https prefix is stripped`() {
        val result = MirrorDomainValidationPolicy.validate("https://rutracker.org")
        assertTrue(result.isValid)
        assertEquals("rutracker.org", result.sanitizedDomain)
        assertFalse(result.isWarning)
    }

    @Test
    fun `http prefix is stripped`() {
        val result = MirrorDomainValidationPolicy.validate("http://rutracker.net")
        assertTrue(result.isValid)
        assertEquals("rutracker.net", result.sanitizedDomain)
        assertFalse(result.isWarning)
    }

    @Test
    fun `trailing path is stripped`() {
        val result = MirrorDomainValidationPolicy.validate("rutracker.nl/forum/index.php")
        assertTrue(result.isValid)
        assertEquals("rutracker.nl", result.sanitizedDomain)
    }

    @Test
    fun `query string is stripped`() {
        val result = MirrorDomainValidationPolicy.validate("rutracker.org?foo=bar")
        assertTrue(result.isValid)
        assertEquals("rutracker.org", result.sanitizedDomain)
    }

    @Test
    fun `fragment is stripped`() {
        val result = MirrorDomainValidationPolicy.validate("rutracker.me#section")
        assertTrue(result.isValid)
        assertEquals("rutracker.me", result.sanitizedDomain)
    }

    @Test
    fun `port is stripped`() {
        val result = MirrorDomainValidationPolicy.validate("rutracker.org:8080")
        assertTrue(result.isValid)
        assertEquals("rutracker.org", result.sanitizedDomain)
    }

    @Test
    fun `full url is sanitized to domain`() {
        val result = MirrorDomainValidationPolicy.validate("https://rutracker.nl/forum/viewtopic.php?t=123#top")
        assertTrue(result.isValid)
        assertEquals("rutracker.nl", result.sanitizedDomain)
    }

    @Test
    fun `domain is lowercased`() {
        val result = MirrorDomainValidationPolicy.validate("Rutracker.NL")
        assertTrue(result.isValid)
        assertEquals("rutracker.nl", result.sanitizedDomain)
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        val result = MirrorDomainValidationPolicy.validate("  rutracker.org  ")
        assertTrue(result.isValid)
        assertEquals("rutracker.org", result.sanitizedDomain)
    }

    // --- Rejections ---

    @Test
    fun `blank input is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("")
        assertFalse(result.isValid)
        assertNull(result.sanitizedDomain)
        assertNotNull(result.rejectionReason)
    }

    @Test
    fun `whitespace-only input is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("   ")
        assertFalse(result.isValid)
    }

    @Test
    fun `localhost is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("localhost")
        assertFalse(result.isValid)
        assertNotNull(result.rejectionReason)
        assertTrue(result.rejectionReason!!.contains("Local", ignoreCase = true))
    }

    @Test
    fun `127 dot 0 dot 0 dot 1 is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("127.0.0.1")
        assertFalse(result.isValid)
    }

    @Test
    fun `private ip 192 dot 168 is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("192.168.1.1")
        assertFalse(result.isValid)
        assertTrue(result.rejectionReason!!.contains("Private", ignoreCase = true))
    }

    @Test
    fun `private ip 10 dot x is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("10.0.0.1")
        assertFalse(result.isValid)
    }

    @Test
    fun `private ip 172 dot 16 is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("172.16.0.1")
        assertFalse(result.isValid)
    }

    @Test
    fun `link-local 169 dot 254 is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("169.254.1.1")
        assertFalse(result.isValid)
    }

    @Test
    fun `dot local mDNS is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("myserver.local")
        assertFalse(result.isValid)
    }

    @Test
    fun `domain without TLD is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("rutracker")
        assertFalse(result.isValid)
        assertTrue(result.rejectionReason!!.contains("TLD", ignoreCase = true))
    }

    @Test
    fun `domain with spaces is rejected`() {
        val result = MirrorDomainValidationPolicy.validate("rut tracker.org")
        assertFalse(result.isValid)
    }

    // --- Warnings for non-rutracker domains ---

    @Test
    fun `non-rutracker domain triggers warning`() {
        val result = MirrorDomainValidationPolicy.validate("example.com")
        assertTrue(result.isValid)
        assertEquals("example.com", result.sanitizedDomain)
        assertTrue(result.isWarning)
        assertNotNull(result.rejectionReason)
    }

    @Test
    fun `rutracker subdomain does not trigger warning`() {
        val result = MirrorDomainValidationPolicy.validate("mirror.rutracker.org")
        assertTrue(result.isValid)
        assertFalse(result.isWarning)
    }

    @Test
    fun `rutracker in path does not cause false negative for warning`() {
        // "rutracker.nl" contains "rutracker" — no warning
        val result = MirrorDomainValidationPolicy.validate("rutracker.nl")
        assertTrue(result.isValid)
        assertFalse(result.isWarning)
    }
}
