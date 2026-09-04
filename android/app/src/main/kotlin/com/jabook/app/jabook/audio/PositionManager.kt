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

/**
 * Manages playback position operations.
 *
 * Actual persistence happens via [PeriodicPositionSaver] and [CrashSafePositionWriter].
 */
internal class PositionManager {
    /**
     * Saves current playback position.
     *
     * No-op: kept for API compatibility with existing call sites.
     * Real persistence is handled by the periodic and crash-safe savers.
     */
    public fun saveCurrentPosition() {
        LogUtils.d("AudioPlayerService", "saveCurrentPosition: handled by periodic/crash-safe savers")
    }
}
