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

internal sealed interface PlaylistQueueOperation {
    data class Add(
        val path: String,
        val index: Int? = null,
    ) : PlaylistQueueOperation

    data class Remove(
        val index: Int,
    ) : PlaylistQueueOperation

    data class Move(
        val fromIndex: Int,
        val toIndex: Int,
    ) : PlaylistQueueOperation

    data class Replace(
        val paths: List<String>,
        val playAtIndex: Int? = null,
    ) : PlaylistQueueOperation

    data class PlayAt(
        val index: Int,
    ) : PlaylistQueueOperation
}

internal data class PlaylistQueueMutation(
    val paths: List<String>,
    val currentIndex: Int,
)

internal object PlaylistQueueMutationPolicy {
    private fun normalizeIndex(
        value: Int,
        size: Int,
    ): Int =
        when {
            size <= 0 -> 0
            else -> value.coerceIn(0, size - 1)
        }

    fun apply(
        currentPaths: List<String>,
        currentIndex: Int,
        operation: PlaylistQueueOperation,
    ): PlaylistQueueMutation {
        return when (operation) {
            is PlaylistQueueOperation.Add -> {
                val insertIndex = operation.index ?: currentPaths.size
                require(insertIndex in 0..currentPaths.size) {
                    "Index $insertIndex is out of bounds for insertion into queue of size ${currentPaths.size}"
                }
                val updated = currentPaths.toMutableList().apply { add(insertIndex, operation.path) }
                val nextIndex =
                    if (insertIndex <= currentIndex) {
                        currentIndex + 1
                    } else {
                        currentIndex
                    }
                PlaylistQueueMutation(
                    paths = updated,
                    currentIndex = normalizeIndex(nextIndex, updated.size),
                )
            }

            is PlaylistQueueOperation.Remove -> {
                require(operation.index in currentPaths.indices) {
                    "Index ${operation.index} is out of bounds for queue of size ${currentPaths.size}"
                }
                val updated = currentPaths.toMutableList().apply { removeAt(operation.index) }
                if (updated.isEmpty()) {
                    return PlaylistQueueMutation(paths = emptyList(), currentIndex = 0)
                }
                val nextIndex =
                    when {
                        operation.index < currentIndex -> currentIndex - 1
                        operation.index == currentIndex -> currentIndex.coerceAtMost(updated.lastIndex)
                        else -> currentIndex
                    }
                PlaylistQueueMutation(
                    paths = updated,
                    currentIndex = normalizeIndex(nextIndex, updated.size),
                )
            }

            is PlaylistQueueOperation.Move -> {
                require(operation.fromIndex in currentPaths.indices) {
                    "fromIndex ${operation.fromIndex} is out of bounds for queue of size ${currentPaths.size}"
                }
                require(operation.toIndex in currentPaths.indices) {
                    "toIndex ${operation.toIndex} is out of bounds for queue of size ${currentPaths.size}"
                }
                if (operation.fromIndex == operation.toIndex) {
                    return PlaylistQueueMutation(
                        paths = currentPaths,
                        currentIndex = normalizeIndex(currentIndex, currentPaths.size),
                    )
                }
                val updated = currentPaths.toMutableList()
                val moved = updated.removeAt(operation.fromIndex)
                updated.add(operation.toIndex, moved)
                val nextIndex =
                    when {
                        currentIndex == operation.fromIndex -> operation.toIndex
                        operation.fromIndex < currentIndex && operation.toIndex >= currentIndex -> currentIndex - 1
                        operation.fromIndex > currentIndex && operation.toIndex <= currentIndex -> currentIndex + 1
                        else -> currentIndex
                    }
                PlaylistQueueMutation(
                    paths = updated,
                    currentIndex = normalizeIndex(nextIndex, updated.size),
                )
            }

            is PlaylistQueueOperation.Replace -> {
                val nextIndex = operation.playAtIndex ?: currentIndex
                PlaylistQueueMutation(
                    paths = operation.paths,
                    currentIndex = normalizeIndex(nextIndex, operation.paths.size),
                )
            }

            is PlaylistQueueOperation.PlayAt -> {
                require(operation.index in currentPaths.indices) {
                    "Index ${operation.index} is out of bounds for queue of size ${currentPaths.size}"
                }
                PlaylistQueueMutation(
                    paths = currentPaths,
                    currentIndex = operation.index,
                )
            }
        }
    }
}
