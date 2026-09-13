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

package com.jabook.app.jabook.compose.core

/**
 * One-shot UI side effects (snackbars, navigation) emitted by ViewModels.
 *
 * Delivered through a `Channel` so each event is consumed exactly once and
 * buffered while no collector is attached (no event loss across recomposition
 * or configuration changes, unlike `SharedFlow(replay = 0)`).
 *
 * ViewModels with richer domain-specific effects may keep their own sealed
 * hierarchy (e.g. `PlayerEffect`) backed by a `Channel` — this interface is the
 * shared contract for simple consumers.
 *
 * ponytail: `ShowDialog` omitted — no `AppDialog` type exists yet; add when a
 * real dialog-as-effect consumer appears.
 */
public sealed interface SideEffect {
    public data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
    ) : SideEffect

    public data class Navigate(
        val route: String,
    ) : SideEffect
}
