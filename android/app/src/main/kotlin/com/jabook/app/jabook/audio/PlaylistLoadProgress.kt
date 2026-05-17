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

/**
 * Progress of playlist loading for UI display.
 *
 * P-09: Provides progress feedback during large playlist loading.
 */
public data class PlaylistLoadProgress(
    public val loaded: Int,
    public val total: Int,
    public val phase: Phase,
) {
    public val fraction: Float get() = if (total > 0) loaded.toFloat() / total else 0f

    public enum class Phase {
        IDLE,
        LOADING_FIRST,
        LOADING_CRITICAL,
        LOADING_BACKGROUND,
        DONE,
    }
}
