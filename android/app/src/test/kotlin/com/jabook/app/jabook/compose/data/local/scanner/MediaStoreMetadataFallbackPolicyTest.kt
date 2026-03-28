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

package com.jabook.app.jabook.compose.data.local.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreMetadataFallbackPolicyTest {
    @Test
    fun `hasReplacementCharacter returns true for U+FFFD`() {
        assertTrue(MediaStoreMetadataFallbackPolicy.hasReplacementCharacter("Достоевски\uFFFD"))
    }

    @Test
    fun `hasReplacementCharacter returns false for valid text`() {
        assertFalse(MediaStoreMetadataFallbackPolicy.hasReplacementCharacter("Достоевский"))
    }

    @Test
    fun `hasReplacementCharacter returns false for null`() {
        assertFalse(MediaStoreMetadataFallbackPolicy.hasReplacementCharacter(null))
    }
}
