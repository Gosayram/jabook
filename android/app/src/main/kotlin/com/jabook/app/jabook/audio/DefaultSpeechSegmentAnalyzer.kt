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
 * Conservative sentence-boundary fallback for Smart Resume.
 *
 * Until waveform-backed sentence boundaries are wired, we rewind at most
 * [lookbackMs] from [positionMs]. This keeps resume contextual without hard
 * failures and allows [ContextualResumeManager] to be enabled in production.
 */
internal class DefaultSpeechSegmentAnalyzer : SpeechSegmentAnalyzer {
    override fun findLastSentenceStart(
        bookId: String,
        positionMs: Long,
        lookbackMs: Long,
    ): Long = (positionMs - lookbackMs).coerceAtLeast(0L)
}
