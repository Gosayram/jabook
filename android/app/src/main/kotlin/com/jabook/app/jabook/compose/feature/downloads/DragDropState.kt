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

package com.jabook.app.jabook.compose.feature.downloads

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * State for managing drag and drop operations.
 */
public class DragDropState {
    public var draggedIndex: Int? by mutableStateOf<Int?>(null)
    public var dragOffset: Offset by mutableStateOf(Offset.Zero)

    public fun onDragStart(index: Int): Unit {
        draggedIndex = index
    }

    public fun onDrag(offset: Offset): Unit {
        dragOffset += offset
    }

    public fun onDragEnd() {
        draggedIndex = null
        dragOffset = Offset.Zero
    }
}

/**
 * Simplified modifier extension for making items draggable.
 */
public fun Modifier.draggableItem(
    index: Int,
    dragDropState: DragDropState,
    onMove: (Int, Int) -> Unit,
): Modifier {
    val isDragging = dragDropState.draggedIndex == index

    return this
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer {
            if (isDragging) {
                translationY = dragDropState.dragOffset.y
                alpha = 0.7f
                scaleX = 0.95f
                scaleY = 0.95f
            }
        }.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    dragDropState.onDragStart(index)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDropState.onDrag(dragAmount)
                },
                onDragEnd = {
                    // Simple reordering based on total drag distance
                    val draggedDistance = dragDropState.dragOffset.y

                    val itemHeight: Float = 100f // Approximate item height
                    val targetIndex = (index + (draggedDistance / itemHeight).toInt()).coerceIn(0, Int.MAX_VALUE)

                    if (targetIndex != index) {
                        onMove(index, targetIndex)
                    }

                    dragDropState.onDragEnd()
                },
                onDragCancel = {
                    dragDropState.onDragEnd()
                },
            )
        }
}
