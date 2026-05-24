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
 * Policy for deciding whether inactivity timer should be reset for a source.
 */
internal object InactivityResetPolicy {
    internal fun shouldResetBySource(source: InactivityCommandSource): Boolean =
        when (source) {
            InactivityCommandSource.USER_UI,
            InactivityCommandSource.NOTIFICATION,
            InactivityCommandSource.PLAYBACK_INTERNAL,
            -> true

            InactivityCommandSource.HEADSET_BUTTON,
            InactivityCommandSource.ANDROID_AUTO,
            InactivityCommandSource.WEAR_OS,
            InactivityCommandSource.SLEEP_TIMER,
            -> false
        }

    fun shouldReset(source: InactivityCommandSource): Boolean = shouldResetBySource(source)
}
