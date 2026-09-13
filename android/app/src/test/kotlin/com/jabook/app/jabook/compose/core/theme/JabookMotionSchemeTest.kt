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

import androidx.compose.animation.core.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class JabookMotionSchemeTest {
    @Test
    fun `expressive scheme matches Material 3 spring tokens`() {
        val defaultSpatial = JabookExpressiveMotionScheme.defaultSpatialSpec<Float>() as SpringSpec<Float>
        val fastSpatial = JabookExpressiveMotionScheme.fastSpatialSpec<Float>() as SpringSpec<Float>
        val slowSpatial = JabookExpressiveMotionScheme.slowSpatialSpec<Float>() as SpringSpec<Float>
        val defaultEffects = JabookExpressiveMotionScheme.defaultEffectsSpec<Float>() as SpringSpec<Float>
        val fastEffects = JabookExpressiveMotionScheme.fastEffectsSpec<Float>() as SpringSpec<Float>
        val slowEffects = JabookExpressiveMotionScheme.slowEffectsSpec<Float>() as SpringSpec<Float>

        assertEquals(0.8f, defaultSpatial.dampingRatio, 0f)
        assertEquals(380f, defaultSpatial.stiffness, 0f)
        assertEquals(0.6f, fastSpatial.dampingRatio, 0f)
        assertEquals(800f, fastSpatial.stiffness, 0f)
        assertEquals(0.8f, slowSpatial.dampingRatio, 0f)
        assertEquals(200f, slowSpatial.stiffness, 0f)
        assertEquals(1f, defaultEffects.dampingRatio, 0f)
        assertEquals(1600f, defaultEffects.stiffness, 0f)
        assertEquals(1f, fastEffects.dampingRatio, 0f)
        assertEquals(3800f, fastEffects.stiffness, 0f)
        assertEquals(1f, slowEffects.dampingRatio, 0f)
        assertEquals(800f, slowEffects.stiffness, 0f)
    }
}
