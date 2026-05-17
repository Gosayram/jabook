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
 * Source of an action that may reset inactivity timeout.
 *
 * NOTE:
 * [ANDROID_AUTO], [WEAR_OS], [SLEEP_TIMER], and [NOTIFICATION] are intentionally
 * kept as explicit command sources for upcoming integration points (AA/Wear
 * transport controls and timer-originated commands). Even when not yet
 * dispatched from all call-sites, they keep policy behavior explicit and avoid
 * hidden defaults when those channels are wired.
 */
public enum class InactivityCommandSource {
    USER_UI,
    HEADSET_BUTTON,
    ANDROID_AUTO,
    WEAR_OS,
    SLEEP_TIMER,
    NOTIFICATION,
    PLAYBACK_INTERNAL,
}
