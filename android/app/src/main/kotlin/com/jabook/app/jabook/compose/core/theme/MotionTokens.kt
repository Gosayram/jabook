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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Shared motion tokens for Compose animations.
 *
 * Keep fixed-rate and infinite animation tokens here. Interruptible UI transitions use
 * [JabookMotionScheme] from [LocalJabookMotionScheme].
 * ponytail: m3 1.4 motionScheme/ExpressiveMotionTokens are internal — expressive springs vendored here as public tokens
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

    /** 300ms — M3 medium2 (legacy "standard" transitions / slow effects). NOT the expressive default (200ms effects / 500ms spatial); kept for non-scheme fallbacks. */
    public const val STATE_DURATION_MS: Int = MEDIUM2

    /** 150ms — M3 fast effects (press/toggles). Effects use tween/linear, never overshoot; spatial motion belongs on springs. */
    public const val PRESS_DURATION_MS: Int = SHORT3

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

    // M3 expressive spring specs (values from material3 1.4 internal ExpressiveMotionTokens):
    // spatial motion uses bouncy springs (overshoot into place), effects use critically-damped (never overshoot)

    /** Spatial default spring — position/size transitions. */
    public fun spatialSpring(): SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Fast spatial spring — quick spatial transitions (container transforms, large moves). */
    public fun fastSpatialSpring(): SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Effects default spring — critically damped, no overshoot (color, opacity, small scale). */
    public fun effectsSpring(): SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

    /** Fast effects spring — critically damped, snap-like (state ticks, small icon changes). */
    public fun fastEffectsSpring(): SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3800f)
}
