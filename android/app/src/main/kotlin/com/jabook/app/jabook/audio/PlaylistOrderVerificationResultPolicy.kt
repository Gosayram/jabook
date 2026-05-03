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

package com.jabook.app.jabook.audio

internal data class PlaylistOrderMismatch(
    val index: Int,
    val expectedPath: String,
    val actualPath: String?,
)

internal data class PlaylistOrderVerificationResult(
    val sizeMatches: Boolean,
    val expectedSize: Int,
    val actualSize: Int,
    val mismatches: List<PlaylistOrderMismatch>,
) {
    val mismatchCount: Int = mismatches.size
}

internal object PlaylistOrderVerificationResultPolicy {
    internal fun evaluate(
        expectedPaths: List<String>,
        actualPaths: List<String?>,
    ): PlaylistOrderVerificationResult {
        val expectedSize = expectedPaths.size
        val actualSize = actualPaths.size
        if (expectedSize != actualSize) {
            return PlaylistOrderVerificationResult(
                sizeMatches = false,
                expectedSize = expectedSize,
                actualSize = actualSize,
                mismatches = emptyList(),
            )
        }

        val mismatches = mutableListOf<PlaylistOrderMismatch>()
        for (index in expectedPaths.indices) {
            val expected = expectedPaths[index]
            val actual = actualPaths[index]
            if (actual != expected) {
                mismatches.add(
                    PlaylistOrderMismatch(
                        index = index,
                        expectedPath = expected,
                        actualPath = actual,
                    ),
                )
            }
        }
        return PlaylistOrderVerificationResult(
            sizeMatches = true,
            expectedSize = expectedSize,
            actualSize = actualSize,
            mismatches = mismatches,
        )
    }
}
