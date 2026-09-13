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

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Public replacement for Material 3's internal MotionScheme in Material 3 1.4.
 *
 * Keep custom motion on this contract until Material exposes its motion scheme.
 */
@Immutable
public interface JabookMotionScheme {
    public fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T>

    public fun <T> fastSpatialSpec(): FiniteAnimationSpec<T>

    public fun <T> slowSpatialSpec(): FiniteAnimationSpec<T>

    public fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T>

    public fun <T> fastEffectsSpec(): FiniteAnimationSpec<T>

    public fun <T> slowEffectsSpec(): FiniteAnimationSpec<T>
}

public val LocalJabookMotionScheme: ProvidableCompositionLocal<JabookMotionScheme> =
    staticCompositionLocalOf<JabookMotionScheme> { JabookExpressiveMotionScheme }

public object JabookExpressiveMotionScheme : JabookMotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}

public object JabookReducedMotionScheme : JabookMotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
