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

/**
 * Guards chapter-detection scheduling to avoid unnecessary WorkManager churn.
 */
internal object ChapterDetectionEligibilityPolicy {
    internal const val MIN_ELIGIBLE_DURATION_MS: Long = 10L * 60L * 1000L

    internal fun shouldEnqueueSingleFileDetection(
        chapterCount: Int,
        filePath: String,
        durationMs: Long,
    ): Boolean =
        chapterCount == 1 &&
            filePath.isNotBlank() &&
            durationMs >= MIN_ELIGIBLE_DURATION_MS
}
