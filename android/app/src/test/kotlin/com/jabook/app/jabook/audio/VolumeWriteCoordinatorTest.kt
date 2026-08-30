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

import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for [VolumeWriteCoordinator] claim semantics.
 *
 * ExoPlayer is an interface here, so plain Mockito mocks serve as identity keys —
 * no Robolectric needed.
 */
class VolumeWriteCoordinatorTest {
    private lateinit var coordinator: VolumeWriteCoordinator
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer

    @Before
    fun setup() {
        coordinator = VolumeWriteCoordinator()
        playerA = mock()
        playerB = mock()
    }

    @Test
    fun `claims on different players are independent`() {
        var revokeA = false
        var revokeB = false

        assertTrue(coordinator.tryAcquire(playerA, VolumeOwner.SLEEP_FADE) { revokeA = true })
        assertTrue(coordinator.tryAcquire(playerB, VolumeOwner.CROSSFADE) { revokeB = true })

        assertTrue("claim on A must survive B's acquire", !revokeA)
        coordinator.release(playerA, VolumeOwner.SLEEP_FADE)
        coordinator.release(playerB, VolumeOwner.CROSSFADE)
    }

    @Test
    fun `reacquiring same player revokes previous owner and installs new one`() {
        var revokeFirst = false
        var revokeSecond = false

        coordinator.tryAcquire(playerA, VolumeOwner.CROSSFADE) { revokeFirst = true }
        coordinator.tryAcquire(playerA, VolumeOwner.SLEEP_FADE) { revokeSecond = true }

        assertTrue("first owner's revoke must run on replacement", revokeFirst)

        // Second owner's revoke must not fire from its own acquire.
        coordinator.release(playerA, VolumeOwner.CROSSFADE)
        assertTrue("wrong-owner release must not revoke the current claim", !revokeSecond)
    }

    @Test
    fun `release with wrong owner is a no-op`() {
        var revoke = false
        coordinator.tryAcquire(playerA, VolumeOwner.SLEEP_FADE) { revoke = true }

        coordinator.release(playerA, VolumeOwner.DEBUG_FOCUS)

        // Claim must still be held: a new acquire still revokes the original.
        coordinator.tryAcquire(playerA, VolumeOwner.BOOK_COMPENSATION) {}
        assertTrue("original claim should have survived wrong-owner release", revoke)
    }

    @Test
    fun `release with correct owner clears the claim`() {
        var revoke = false
        coordinator.tryAcquire(playerA, VolumeOwner.SLEEP_FADE) { revoke = true }

        coordinator.release(playerA, VolumeOwner.SLEEP_FADE)

        // Claim was cleared: a new acquire must NOT revoke the released owner.
        coordinator.tryAcquire(playerA, VolumeOwner.BOOK_COMPENSATION) {}
        assertTrue("released claim must not be revoked by a later acquire", !revoke)
    }
}
