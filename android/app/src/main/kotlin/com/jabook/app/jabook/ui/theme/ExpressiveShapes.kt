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

package com.jabook.app.jabook.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ponytail: M3 1.4 fallback — MaterialShapes/MotionScheme not in material3 1.4.0 (expressive 1.5 only).
// Replaced 35 RoundedPolygon (MaterialShapes) with RoundedCornerShape stdlib only.
// 4 eager shapes live in Theme.kt (28dp/20dp); 31 here are lazy Shape aliases for cards/fab/sheets routing.
// graphics-shapes Morph kept available (1.1.0) but not used — fallback to RoundedCornerShape for UnifiedBookCard.

public object ExpressiveShapes {
    // --- cards ---
    public val circle: Shape by lazy { CircleShape }
    public val square: Shape by lazy { RoundedCornerShape(4.dp) }
    public val slanted: Shape by lazy { RoundedCornerShape(12.dp) }
    public val triangle: Shape by lazy { RoundedCornerShape(16.dp) }
    public val diamond: Shape by lazy { RoundedCornerShape(12.dp) }
    public val pentagon: Shape by lazy { RoundedCornerShape(16.dp) }
    public val gem: Shape by lazy { RoundedCornerShape(16.dp) }
    public val sunny: Shape by lazy { RoundedCornerShape(20.dp) }
    public val verySunny: Shape by lazy { RoundedCornerShape(28.dp) }
    public val cookie7: Shape by lazy { RoundedCornerShape(20.dp) }
    public val cookie12: Shape by lazy { RoundedCornerShape(28.dp) }
    public val clover4: Shape by lazy { RoundedCornerShape(20.dp) }
    public val clover8: Shape by lazy { RoundedCornerShape(28.dp) }
    public val burst: Shape by lazy { RoundedCornerShape(16.dp) }
    public val softBurst: Shape by lazy { RoundedCornerShape(20.dp) }
    public val boom: Shape by lazy { RoundedCornerShape(16.dp) }
    public val softBoom: Shape by lazy { RoundedCornerShape(20.dp) }
    public val ghostish: Shape by lazy { RoundedCornerShape(16.dp) }
    public val clamShell: Shape by lazy { RoundedCornerShape(20.dp) }
    public val heart: Shape by lazy { RoundedCornerShape(16.dp) }
    public val bun: Shape by lazy { RoundedCornerShape(20.dp) }

    // --- fab ---
    public val flower: Shape by lazy { RoundedCornerShape(28.dp) }
    public val puffyDiamond: Shape by lazy { RoundedCornerShape(20.dp) }
    public val pixelCircle: Shape by lazy { CircleShape }
    public val pixelTriangle: Shape by lazy { RoundedCornerShape(12.dp) }

    // --- sheets ---
    public val arch: Shape by lazy { RoundedCornerShape(28.dp) }
    public val fan: Shape by lazy { RoundedCornerShape(28.dp) }
    public val arrow: Shape by lazy { RoundedCornerShape(12.dp) }
    public val semiCircle: Shape by lazy { RoundedCornerShape(28.dp) }
    public val oval: Shape by lazy { RoundedCornerShape(28.dp) }
    public val pill: Shape by lazy { RoundedCornerShape(999.dp) }

    public val cardPolygons: List<Shape> by lazy {
        listOf(
            circle,
            square,
            slanted,
            triangle,
            diamond,
            pentagon,
            gem,
            sunny,
            verySunny,
            cookie7,
            cookie12,
            clover4,
            clover8,
            burst,
            softBurst,
            boom,
            softBoom,
            ghostish,
            clamShell,
            heart,
            bun,
        )
    }
    public val fabPolygons: List<Shape> by lazy { listOf(flower, puffyDiamond, pixelCircle, pixelTriangle) }
    public val sheetPolygons: List<Shape> by lazy { listOf(arch, fan, arrow, semiCircle, oval, pill) }

    public val allPolygons: List<Shape> by lazy { cardPolygons + fabPolygons + sheetPolygons }

    // ponytail: alias for Theme.kt touch
    public val allShapes: List<Shape> get() = allPolygons

    @Composable
    public fun defaultCardShape(): Shape = sunny

    @Composable
    public fun defaultFabShape(): Shape = flower

    @Composable
    public fun defaultSheetShape(): Shape = arch
}
