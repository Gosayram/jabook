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

import com.jabook.app.jabook.util.LogUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P-88: Fast in-memory cache for saved player state.
 *
 * Provides synchronous read access to the last saved playback state
 * without hitting Room. The cache is updated on every position save
 * and can be read instantly on app restart for quick resume.
 *
 * Uses the existing [SavedPlaybackState] data class.
 */
@Singleton
public class PlayerStateCache
    @Inject
    constructor() {
        @Volatile
        private var cachedState: SavedPlaybackState? = null

        /**
         * Updates the cached state.
         * Call this on every position save to keep cache warm.
         */
        public fun update(state: SavedPlaybackState) {
            cachedState = state
            LogUtils.v(TAG, "Cache updated: index=${state.currentIndex} pos=${state.currentPosition}ms")
        }

        /**
         * Returns the cached state, or null if not available.
         */
        public fun read(): SavedPlaybackState? = cachedState

        /**
         * Clears the cache (e.g., on logout or data clear).
         */
        public fun clear() {
            cachedState = null
            LogUtils.d(TAG, "Cache cleared")
        }

        /**
         * Whether the cache has a valid state.
         */
        public fun hasCachedState(): Boolean = cachedState != null

        public companion object {
            private const val TAG = "PlayerStateCache"
        }
    }
