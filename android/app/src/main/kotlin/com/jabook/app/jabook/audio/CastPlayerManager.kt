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

import android.content.Context
<<<<<<< Updated upstream
=======
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext
>>>>>>> Stashed changes
import com.jabook.app.jabook.util.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
<<<<<<< Updated upstream
 * Stub for Media3 Cast wiring (RemoteCastPlayer).
 *
 * ponytail: full Cast requires media3-cast + mediarouter + play-services-cast-framework
 * (added to libs.versions.toml; commented in app/build.gradle until cached offline).
 * Wiring deferred — large scope: CastOptionsProvider manifest, MediaRouteButton UI, session handoff.
 * This stub keeps the seam for future wiring; enable when Cast receiver is configured.
=======
 * Media3 Cast wiring: RemoteCastPlayer + CastContext probe + PlayerStateTransfer.
 *
 * ponytail: Output Switcher via MediaRouter/MediaRouteButton (androidx.mediarouter) — UI exposes
 * Cast route picker; this manager owns session probing and transferPlayback.
>>>>>>> Stashed changes
 */
@Singleton
public class CastPlayerManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Volatile
        private var initialized = false

<<<<<<< Updated upstream
        public fun initialize() {
            if (initialized) return
            try {
                Class.forName("com.google.android.gms.cast.framework.CastContext")
                LogUtils.d("CastPlayerManager", "Cast SDK present — wiring deferred until route selection")
=======
        @Volatile
        private var castContext: CastContext? = null

        @Volatile
        private var remoteCastPlayer: RemoteCastPlayer? = null

        @Volatile
        private var castPlayer: CastPlayer? = null

        public fun initialize() {
            if (initialized) return
            try {
                castContext = CastContext.getSharedInstance(context)
                // ponytail: CastContext probe — confirms play-services-cast-framework on classpath
                val cc = castContext ?: throw IllegalStateException("CastContext null")
                // RemoteCastPlayer for low-level control, CastPlayer (ForwardingPlayer) for Player API
                remoteCastPlayer = try {
                    RemoteCastPlayer.Builder(context).build()
                } catch (e: Exception) {
                    LogUtils.w("CastPlayerManager", "RemoteCastPlayer unavailable: ${e.message}")
                    null
                }
                castPlayer = try {
                    CastPlayer(cc)
                } catch (e: Exception) {
                    LogUtils.w("CastPlayerManager", "CastPlayer unavailable: ${e.message}")
                    null
                }
                LogUtils.d("CastPlayerManager", "Cast wired — CastContext + RemoteCastPlayer ready")
>>>>>>> Stashed changes
                initialized = true
            } catch (_: ClassNotFoundException) {
                LogUtils.d("CastPlayerManager", "Cast SDK not on classpath (offline/dev build) — stub no-op")
            } catch (e: Exception) {
                LogUtils.w("CastPlayerManager", "Cast init probe failed: ${e.message}")
            }
        }

<<<<<<< Updated upstream
        public fun isAvailable(): Boolean = initialized

        public fun release() {
=======
        public fun isAvailable(): Boolean = initialized && castContext != null

        public fun getCastPlayer(): Player? = castPlayer ?: remoteCastPlayer

        public fun getRemoteCastPlayer(): RemoteCastPlayer? = remoteCastPlayer

        /** Transfer playback to Cast via PlayerStateTransfer extension. */
        public fun transferToCast(fromPlayer: Player) {
            val target = getCastPlayer() ?: run {
                LogUtils.w("CastPlayerManager", "No Cast player for transfer")
                return
            }
            try {
                PlayerStateTransfer.transferPlayback(fromPlayer, target)
                LogUtils.i("CastPlayerManager", "transferPlayback to Cast done")
            } catch (e: Exception) {
                LogUtils.e("CastPlayerManager", "transferPlayback failed", e)
            }
        }

        /** Transfer back from Cast to local. */
        public fun transferFromCast(toPlayer: Player) {
            val source = getCastPlayer() ?: return
            try {
                PlayerStateTransfer.transferPlayback(source, toPlayer)
                LogUtils.i("CastPlayerManager", "transferPlayback from Cast done")
            } catch (e: Exception) {
                LogUtils.e("CastPlayerManager", "transferPlayback from Cast failed", e)
            }
        }

        public fun release() {
            try { castPlayer?.release() } catch (_: Exception) {}
            try { remoteCastPlayer?.release() } catch (_: Exception) {}
            castPlayer = null
            remoteCastPlayer = null
            castContext = null
>>>>>>> Stashed changes
            initialized = false
        }
    }
