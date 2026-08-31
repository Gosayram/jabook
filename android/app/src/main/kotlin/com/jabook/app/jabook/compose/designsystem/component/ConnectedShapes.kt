// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.jabook.app.jabook.compose.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ponytail: Grit's 4dp connected + 16dp outer; 28dp variant exists in Grit for large cards — use 16dp here to match M3 large
public val CONNECTED_CORNER: Dp = 4.dp
public val END_CORNER: Dp = 16.dp

public fun leadingItemShape(): Shape =
    RoundedCornerShape(
        topStart = END_CORNER,
        topEnd = END_CORNER,
        bottomStart = CONNECTED_CORNER,
        bottomEnd = CONNECTED_CORNER,
    )

public fun middleItemShape(): Shape = RoundedCornerShape(CONNECTED_CORNER)

public fun endItemShape(): Shape =
    RoundedCornerShape(
        topStart = CONNECTED_CORNER,
        topEnd = CONNECTED_CORNER,
        bottomStart = END_CORNER,
        bottomEnd = END_CORNER,
    )

public fun detachedItemShape(): Shape = RoundedCornerShape(END_CORNER)

// ponytail: index helper keeps call-site to one line; ListItemShapes variant not needed yet
public fun connectedItemShape(
    index: Int,
    count: Int,
): Shape =
    when {
        count == 1 -> detachedItemShape()
        index == 0 -> leadingItemShape()
        index == count - 1 -> endItemShape()
        else -> middleItemShape()
    }
