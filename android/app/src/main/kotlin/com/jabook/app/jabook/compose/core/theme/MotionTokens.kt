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

package com.jabook.app.jabook.compose.core.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Shared motion tokens for Compose animations.
 *
 * Keep all canonical durations/easings here to avoid hardcoded animation values
 * spread across screens.
 * ponytail: fallback for non-scheme cases (shimmer/rotation infinite) — scheme springs via MaterialTheme.motionScheme elsewhere
 */
public object MotionTokens {
    public const val SHORT1: Int = 50
    public const val SHORT2: Int = 100
    public const val SHORT3: Int = 150
    public const val SHORT4: Int = 200
    public const val MEDIUM1: Int = 250
    public const val MEDIUM2: Int = 300
    public const val MEDIUM3: Int = 350
    public const val MEDIUM4: Int = 400
    public const val LONG1: Int = 450
    public const val LONG2: Int = 500
    public const val LONG3: Int = 550
    public const val LONG4: Int = 600
    public const val EXTRA_LONG1: Int = 700
    public const val EXTRA_LONG2: Int = 800
    public const val EXTRA_LONG3: Int = 900
    public const val EXTRA_LONG4: Int = 1000

    /** M3 Standard easing — used for short and medium transitions (chips, toggles, FAB). */
    public val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // ponytail: true M3 Emphasized is multi-point PathInterpolator(0.05,0,0.1333,0.06,0.1666,0.4,0.2083,0.82,0.25,1) with no CSS equivalent — Web/Compose fallback is Standard curve
    /** M3 Emphasized easing — primary easing for medium/long transitions (fallback to Standard on Compose/Web). */
    public val Emphasized: CubicBezierEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** M3 Emphasized Decelerate — for incoming elements (hero, shared element). */
    public val EmphasizedDecelerate: CubicBezierEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** M3 Emphasized Accelerate — for outgoing elements. */
    public val EmphasizedAccelerate: CubicBezierEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Linear easing — used for micro state changes (icon toggles, badges). */
    public val Linear: Easing = CubicBezierEasing(0f, 0f, 1f, 1f)
}
