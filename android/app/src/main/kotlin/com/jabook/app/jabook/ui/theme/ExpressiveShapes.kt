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

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.RoundedPolygon

// ponytail: 35 MaterialShapes total — 4 eager in Theme.kt (Cookie9/4/6 + Puffy),
// 31 lazy here. Wired to real components beyond demo list (cards/fab/sheets).
// Stored as RoundedPolygon (non-composable) to allow lazy init outside @Composable;
// call .toShape() inside @Composable when a Shape is needed.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
public object ExpressiveShapes {
    // --- cards ---
    public val circle: RoundedPolygon by lazy { MaterialShapes.Circle }
    public val square: RoundedPolygon by lazy { MaterialShapes.Square }
    public val slanted: RoundedPolygon by lazy { MaterialShapes.Slanted }
    public val triangle: RoundedPolygon by lazy { MaterialShapes.Triangle }
    public val diamond: RoundedPolygon by lazy { MaterialShapes.Diamond }
    public val pentagon: RoundedPolygon by lazy { MaterialShapes.Pentagon }
    public val gem: RoundedPolygon by lazy { MaterialShapes.Gem }
    public val sunny: RoundedPolygon by lazy { MaterialShapes.Sunny }
    public val verySunny: RoundedPolygon by lazy { MaterialShapes.VerySunny }
    public val cookie7: RoundedPolygon by lazy { MaterialShapes.Cookie7Sided }
    public val cookie12: RoundedPolygon by lazy { MaterialShapes.Cookie12Sided }
    public val clover4: RoundedPolygon by lazy { MaterialShapes.Clover4Leaf }
    public val clover8: RoundedPolygon by lazy { MaterialShapes.Clover8Leaf }
    public val burst: RoundedPolygon by lazy { MaterialShapes.Burst }
    public val softBurst: RoundedPolygon by lazy { MaterialShapes.SoftBurst }
    public val boom: RoundedPolygon by lazy { MaterialShapes.Boom }
    public val softBoom: RoundedPolygon by lazy { MaterialShapes.SoftBoom }
    public val ghostish: RoundedPolygon by lazy { MaterialShapes.Ghostish }
    public val clamShell: RoundedPolygon by lazy { MaterialShapes.ClamShell }
    public val heart: RoundedPolygon by lazy { MaterialShapes.Heart }
    public val bun: RoundedPolygon by lazy { MaterialShapes.Bun }

    // --- fab ---
    public val flower: RoundedPolygon by lazy { MaterialShapes.Flower }
    public val puffyDiamond: RoundedPolygon by lazy { MaterialShapes.PuffyDiamond }
    public val pixelCircle: RoundedPolygon by lazy { MaterialShapes.PixelCircle }
    public val pixelTriangle: RoundedPolygon by lazy { MaterialShapes.PixelTriangle }

    // --- sheets ---
    public val arch: RoundedPolygon by lazy { MaterialShapes.Arch }
    public val fan: RoundedPolygon by lazy { MaterialShapes.Fan }
    public val arrow: RoundedPolygon by lazy { MaterialShapes.Arrow }
    public val semiCircle: RoundedPolygon by lazy { MaterialShapes.SemiCircle }
    public val oval: RoundedPolygon by lazy { MaterialShapes.Oval }
    public val pill: RoundedPolygon by lazy { MaterialShapes.Pill }

    public val cardPolygons: List<RoundedPolygon> by lazy {
        listOf(circle, square, slanted, triangle, diamond, pentagon, gem, sunny, verySunny, cookie7, cookie12, clover4, clover8, burst, softBurst, boom, softBoom, ghostish, clamShell, heart, bun)
    }
    public val fabPolygons: List<RoundedPolygon> by lazy { listOf(flower, puffyDiamond, pixelCircle, pixelTriangle) }
    public val sheetPolygons: List<RoundedPolygon> by lazy { listOf(arch, fan, arrow, semiCircle, oval, pill) }

    @Suppress("UNCHECKED_CAST")
    public val allPolygons: List<RoundedPolygon> by lazy { cardPolygons + fabPolygons + sheetPolygons as List<RoundedPolygon> }
    // ponytail: alias for Theme.kt touch
    public val allShapes: List<RoundedPolygon> get() = allPolygons

    @Composable
    public fun defaultCardShape(): Shape = sunny.toShape()

    @Composable
    public fun defaultFabShape(): Shape = flower.toShape()

    @Composable
    public fun defaultSheetShape(): Shape = arch.toShape()
}
