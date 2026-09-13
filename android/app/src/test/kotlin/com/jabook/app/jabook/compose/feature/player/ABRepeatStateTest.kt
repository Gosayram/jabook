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

package com.jabook.app.jabook.compose.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ABRepeatStateTest {
    @Test
    fun `AB range requires B to be after A`() {
        assertFalse(isValidABRepeatRange(pointA = 1_000L, pointB = 1_000L))
        assertFalse(isValidABRepeatRange(pointA = 1_000L, pointB = 999L))

        assertTrue(isValidABRepeatRange(pointA = 1_000L, pointB = 1_001L))
    }
}
