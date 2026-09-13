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

package com.jabook.app.jabook.compose.data.permissions

import android.net.Uri

/**
 * Makes persisted SAF tree grants safe to use from picker callbacks.
 *
 * Providers may revoke a previously selected tree or omit persistable access. In either case the
 * selected URI must not be saved as an application path because it cannot be reopened after a
 * process restart.
 */
internal class PersistedTreeUriPermissionGuard(
    private val takePermission: (Uri) -> Unit,
    private val releasePermission: (Uri) -> Unit,
    private val isPermissionPersisted: (Uri) -> Boolean,
) {
    fun take(uri: Uri): Boolean =
        try {
            takePermission(uri)
            isPermissionPersisted(uri)
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    fun release(uri: Uri): Boolean =
        try {
            releasePermission(uri)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
}
