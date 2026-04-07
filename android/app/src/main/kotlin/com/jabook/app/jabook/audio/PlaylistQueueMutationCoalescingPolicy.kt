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

internal object PlaylistQueueMutationCoalescingPolicy {
    private const val DEFAULT_WINDOW_MS = 120L

    fun operationKey(operation: PlaylistQueueOperation): String =
        when (operation) {
            is PlaylistQueueOperation.Add -> "add:${operation.path}:${operation.index ?: -1}"
            is PlaylistQueueOperation.Remove -> "remove:${operation.index}"
            is PlaylistQueueOperation.Move -> "move:${operation.fromIndex}:${operation.toIndex}"
            is PlaylistQueueOperation.Replace ->
                "replace:${operation.paths.hashCode()}:${operation.playAtIndex ?: -1}"
            is PlaylistQueueOperation.PlayAt -> "playAt:${operation.index}"
        }

    fun shouldDropDuplicate(
        previousOperationKey: String?,
        previousMutationAtMs: Long,
        operationKey: String,
        nowMs: Long,
        coalescingWindowMs: Long = DEFAULT_WINDOW_MS,
    ): Boolean {
        if (previousOperationKey == null) {
            return false
        }
        val delta = nowMs - previousMutationAtMs
        return previousOperationKey == operationKey && delta in 0..coalescingWindowMs
    }
}
