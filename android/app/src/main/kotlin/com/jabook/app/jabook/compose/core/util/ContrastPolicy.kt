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

package com.jabook.app.jabook.compose.core.util

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG contrast helpers for dynamic foreground/background adaptation.
 */
public object ContrastPolicy {
    public const val MIN_AA_NORMAL_TEXT: Double = 4.5

    public fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val l1 = luminance(foreground)
        val l2 = luminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Picks white or black text based on stronger WCAG contrast against a given background.
     */
    public fun preferredOnColor(background: Color): Color {
        val whiteContrast = contrastRatio(Color.White, background)
        val blackContrast = contrastRatio(Color.Black, background)
        return if (whiteContrast >= blackContrast) Color.White else Color.Black
    }

    private fun luminance(color: Color): Double {
        fun linearize(value: Double): Double =
            if (value <= 0.04045) {
                value / 12.92
            } else {
                ((value + 0.055) / 1.055).pow(2.4)
            }

        val r = linearize(color.red.toDouble())
        val g = linearize(color.green.toDouble())
        val b = linearize(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
