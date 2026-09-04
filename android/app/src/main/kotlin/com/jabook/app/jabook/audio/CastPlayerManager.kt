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
import com.jabook.app.jabook.util.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub for Media3 Cast wiring (RemoteCastPlayer).
 *
 * ponytail: full Cast requires media3-cast + mediarouter + play-services-cast-framework
 * (added to libs.versions.toml; commented in app/build.gradle until cached offline).
 * Wiring deferred — large scope: CastOptionsProvider manifest, MediaRouteButton UI, session handoff.
 * This stub keeps the seam for future wiring; enable when Cast receiver is configured.
 */
@Singleton
public class CastPlayerManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Volatile
        private var initialized = false

        public fun initialize() {
            if (initialized) return
            try {
                Class.forName("com.google.android.gms.cast.framework.CastContext")
                LogUtils.d("CastPlayerManager", "Cast SDK present — wiring deferred until route selection")
                initialized = true
            } catch (_: ClassNotFoundException) {
                LogUtils.d("CastPlayerManager", "Cast SDK not on classpath (offline/dev build) — stub no-op")
            } catch (e: Exception) {
                LogUtils.w("CastPlayerManager", "Cast init probe failed: ${e.message}")
            }
        }

        public fun isAvailable(): Boolean = initialized

        public fun release() {
            initialized = false
        }
    }
