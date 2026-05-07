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

/**
 * Shared motion tokens for Compose animations.
 *
 * Keep all canonical durations/easings here to avoid hardcoded animation values
 * spread across screens.
 */
public object MotionTokens {
    public const val SHORT1: Int = 50
    public const val SHORT2: Int = 100
    public const val MEDIUM1: Int = 200
    public const val MEDIUM2: Int = 300
    public const val LONG1: Int = 400
    public const val LONG2: Int = 500

    public val Emphasized: CubicBezierEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    public val EmphasizedDecelerate: CubicBezierEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}
