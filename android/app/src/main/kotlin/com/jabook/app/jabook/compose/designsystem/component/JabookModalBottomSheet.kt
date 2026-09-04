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

package com.jabook.app.jabook.compose.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun JabookModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape? = null,
    content: @Composable () -> Unit,
) {
    val resolvedShape = shape ?: ExpressiveShapes.defaultSheetShape()
    // M3 spec: 28dp from top, max 640dp, 56dp margins when wider, 48dp drag handle hit, scrim dismiss
    BoxWithConstraints(modifier = modifier) {
        val maxSheetWidth = 640.dp
        // 56dp margins when viewport > 640+112: sheetMaxWidth centers via ModalBottomSheet, widthIn ensures 56dp side margins
        val sheetModifier = if (maxWidth > maxSheetWidth + 112.dp) Modifier.widthIn(max = maxSheetWidth) else Modifier.fillMaxWidth()
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            shape = resolvedShape,
            dragHandle = { BottomSheetDefaults.DragHandle() }, // 48dp hit target per M3
            sheetMaxWidth = maxSheetWidth,
            // 28dp top inset — sheet never covers status area; scrim dismiss is default onDismissRequest
            contentWindowInsets = { WindowInsets(top = 28.dp) },
            content = {
                Column(modifier = sheetModifier.fillMaxWidth().navigationBarsPadding()) { content() }
            },
        )
    }
}
