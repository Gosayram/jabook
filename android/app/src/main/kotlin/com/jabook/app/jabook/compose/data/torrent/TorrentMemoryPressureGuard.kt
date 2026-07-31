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

package com.jabook.app.jabook.compose.data.torrent

import android.content.ComponentCallbacks2
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reduces native libtorrent memory pressure before the process is killed.
 *
 * Native libtorrent allocations are outside the ART heap, so waiting for GC does
 * not reclaim them. Calls are synchronized because Android may deliver successive
 * trim callbacks while libtorrent's alert thread is still active.
 */
@Singleton
public class TorrentMemoryPressureGuard
    @Inject
    constructor(
        private val session: TorrentSession,
    ) {
        /**
         * @return `true` when the session was guarded.
         */
        @Synchronized
        public fun onTrimMemory(level: Int): Boolean {
            if (!isCritical(level)) return false

            session.pauseForMemoryPressure()
            return true
        }

        private fun isCritical(level: Int): Boolean =
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
    }
