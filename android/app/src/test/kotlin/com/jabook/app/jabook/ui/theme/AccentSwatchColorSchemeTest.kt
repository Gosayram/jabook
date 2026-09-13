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

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.jabook.app.jabook.compose.core.theme.getAccentSwatch
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentSwatchColorSchemeTest {
    @Test
    fun `default index returns null`() {
        assertNull(accentSwatchColorScheme(0, isDark = false))
        assertNull(accentSwatchColorScheme(0, isDark = true))
    }

    @Test
    fun `out of range index returns null`() {
        assertNull(accentSwatchColorScheme(-1, isDark = true))
        assertNull(accentSwatchColorScheme(99, isDark = true))
    }

    @Test
    fun `non-default index builds scheme preserving seed hue`() {
        val swatch = getAccentSwatch(1)!! // Indigo 0xFF4F46E5
        val seedHue = Hct.fromInt(swatch.primary.toArgb()).hue

        val light = accentSwatchColorScheme(1, isDark = false)!!
        val dark = accentSwatchColorScheme(1, isDark = true)!!

        assertEquals(
            "Light primary should preserve seed hue",
            seedHue,
            Hct.fromInt(light.primary.toArgb()).hue,
            2.0,
        )
        assertEquals(
            "Dark primary should preserve seed hue",
            seedHue,
            Hct.fromInt(dark.primary.toArgb()).hue,
            2.0,
        )
        assertTrue(
            "Dark background should be darker than light background",
            dark.background.luminance() < light.background.luminance(),
        )
        assertNotEquals(
            "Swatch scheme should differ from Material baseline primary",
            androidx.compose.material3
                .lightColorScheme()
                .primary,
            light.primary,
        )
    }
}
