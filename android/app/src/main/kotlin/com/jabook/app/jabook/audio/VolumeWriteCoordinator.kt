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

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identifies which subsystem currently owns exclusive writes to a player's volume.
 */
public enum class VolumeOwner {
    SLEEP_FADE,
    CROSSFADE,
    CHAPTER_LOUDNESS,
    BOOK_COMPENSATION,
    DEBUG_FOCUS,
}

/**
 * Coordinates exclusive volume writes across the five subsystems that move
 * [Player.setVolume]: sleep-timer fade ([AudioFader]), [CrossFadePlayer],
 * chapter loudness transitions, book loudness compensation, and the debug
 * focus simulator.
 *
 * Semantics: a writer acquires a per-player claim for the whole animation and
 * releases it at the end (completion or cancellation). Claims are keyed by
 * player identity — claims on different players are fully independent. When a
 * new owner acquires a player that is already claimed, the previous owner's
 * [tryAcquire]-supplied revoke callback runs first (e.g. finalize the crossfade
 * instantly or cancel the fade), so two writers never fight over the volume.
 * This is what lets a SLEEP_FADE started during an in-flight CROSSFADE finalize
 * the transition and then fade the (new) current player cleanly.
 *
 * The user-resume path stays intact: cancelling a fade triggers its end
 * listener, which releases the claim.
 *
 * All methods tolerate any thread; state is guarded by a plain lock.
 */
@Singleton
public class VolumeWriteCoordinator
    @Inject
    public constructor() {
        private data class ActiveClaim(
            val owner: VolumeOwner,
            val revoke: () -> Unit,
        )

        private val claims = IdentityHashMap<Player, ActiveClaim>()

        /**
         * Acquires volume ownership of [player] for [owner].
         *
         * If the player is already claimed, the existing claim's revoke callback
         * runs (new owner wins) and is replaced. Claims on different players do
         * not interact. Always returns true; callers may treat the boolean as a
         * "claim stored" confirmation.
         */
        @Synchronized
        public fun tryAcquire(
            player: Player,
            owner: VolumeOwner,
            revoke: () -> Unit,
        ): Boolean {
            claims.remove(player)?.revoke?.invoke()
            claims[player] = ActiveClaim(owner, revoke)
            return true
        }

        /**
         * Releases the claim on [player] if it still belongs to [owner].
         * No-op when the claim was already replaced by another owner or cleared.
         */
        @Synchronized
        public fun release(
            player: Player,
            owner: VolumeOwner,
        ) {
            val claim = claims[player] ?: return
            if (claim.owner == owner) claims.remove(player)
        }
    }

/**
 * Process-wide bridge that publishes the service's active player getter.
 *
 * The service resolves the real player (custom processor player or crossfade
 * player) via its internal facade; non-service owners such as
 * ChapterLoudnessTransitionPolicy need the same target for their volume
 * writes. [AudioPlayerServiceInitializer] publishes the getter once on service
 * startup; before that (or if the service is not running) [get] returns null
 * and callers fall back to the idle Hilt singleton player.
 */
@Singleton
public class ActivePlayerRef
    @Inject
    public constructor() {
        @Volatile
        private var getter: (() -> ExoPlayer)? = null

        public fun set(getter: (() -> ExoPlayer)?) {
            this.getter = getter
        }

        public fun get(): ExoPlayer? = getter?.invoke()
    }
