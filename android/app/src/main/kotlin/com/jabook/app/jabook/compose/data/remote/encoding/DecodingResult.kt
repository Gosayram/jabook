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

package com.jabook.app.jabook.compose.data.remote.encoding

/**
 * Result of encoding detection and decoding operation.
 *
 * Contains decoded text, detected encoding, confidence score, and validation flags.
 *
 * @property text Decoded text (may contain mojibake if confidence is low)
 * @property encoding Detected/used encoding name (e.g., "windows-1251", "utf-8")
 * @property confidence Confidence score 0.0-1.0 (higher = more confident)
 * @property hasMojibake True if mojibake patterns detected in decoded text
 * @property isValid True if decoding succeeded and text appears valid
 */
public data class DecodingResult(
    public val text: String,
    public val encoding: String,
    public val confidence: Float,
    public val hasMojibake: Boolean = false,
    public val isValid: Boolean = true,
) {
    public companion object {
        /**
         * Creates an invalid result for failed decoding attempts.
         */
        public fun invalid(): DecodingResult =
            DecodingResult(
                text = "",
                encoding = "unknown",
                confidence = 0f,
                hasMojibake = false,
                isValid = false,
            )

        /**
         * Creates a successful result with high confidence.
         */
        public fun success(
            text: String,
            encoding: String,
        ): DecodingResult =
            DecodingResult(
                text = text,
                encoding = encoding,
                confidence = 0.95f,
                hasMojibake = false,
                isValid = true,
            )

        /**
         * Creates a low-confidence result (likely has issues).
         */
        public fun lowConfidence(
            text: String,
            encoding: String,
            hasMojibake: Boolean = true,
        ): DecodingResult =
            DecodingResult(
                text = text,
                encoding = encoding,
                confidence = 0.3f,
                hasMojibake = hasMojibake,
                isValid = true,
            )
    }
}
