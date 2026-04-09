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

package com.jabook.app.jabook.compose.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryResultSizeGuardPolicyTest {
    @Test
    fun `shouldWarn returns false on threshold boundary`() {
        assertFalse(QueryResultSizeGuardPolicy.shouldWarn(QueryResultSizeGuardPolicy.WARN_THRESHOLD_ROWS))
    }

    @Test
    fun `shouldWarn returns true when row count exceeds threshold`() {
        assertTrue(QueryResultSizeGuardPolicy.shouldWarn(QueryResultSizeGuardPolicy.WARN_THRESHOLD_ROWS + 1))
    }
}
