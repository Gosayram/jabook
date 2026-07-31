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

package com.jabook.app.jabook.indexing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps a terminal service auto-dismiss from leaking into a later run. */
internal class DelayedStopCoordinator(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
    private val onStop: () -> Unit,
) {
    private var pendingStop: Job? = null

    fun schedule() {
        cancel()
        pendingStop =
            scope.launch {
                delay(delayMillis)
                onStop()
            }
    }

    fun cancel() {
        pendingStop?.cancel()
        pendingStop = null
    }
}
