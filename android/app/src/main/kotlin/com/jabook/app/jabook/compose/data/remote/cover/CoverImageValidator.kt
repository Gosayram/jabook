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

package com.jabook.app.jabook.compose.data.remote.cover

import android.graphics.BitmapFactory

/**
 * Byte-level plausibility gate for downloaded cover images: rejects placeholders,
 * tracking pixels, banners and logos before they can be stored as a book cover.
 *
 * Decodes bounds only — no bitmap pixels are ever allocated.
 */
public object CoverImageValidator {
    /**
     * @param bytes raw image payload (already size-capped by the caller)
     * @return true when the payload looks like a portrait book cover
     */
    public fun isPlausibleCover(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_BYTES) return false
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) return false
        val aspect = width.toFloat() / height.toFloat()
        return aspect in MIN_ASPECT..MAX_ASPECT
    }

    private const val MIN_BYTES = 3000
    private const val MIN_DIMENSION = 200
    private const val MIN_ASPECT = 0.45f
    private const val MAX_ASPECT = 0.95f
}
