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

package com.jabook.app.jabook.compose.feature.settings

import com.jabook.app.jabook.compose.feature.library.ProductivePeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelRecapPolicyTest {
    @Test
    fun `resolveProductivePeriodFromHour maps hours to periods`() {
        val cases =
            mapOf(
                -1 to ProductivePeriod.UNKNOWN,
                5 to ProductivePeriod.MORNING,
                11 to ProductivePeriod.MORNING,
                12 to ProductivePeriod.DAY,
                16 to ProductivePeriod.DAY,
                17 to ProductivePeriod.EVENING,
                22 to ProductivePeriod.EVENING,
                23 to ProductivePeriod.NIGHT,
                0 to ProductivePeriod.NIGHT,
                4 to ProductivePeriod.NIGHT,
            )
        cases.forEach { (hour, expected) ->
            assertEquals("hour=$hour", expected, resolveProductivePeriodFromHour(hour))
        }
    }
}
