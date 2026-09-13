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

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class CoverImageValidatorTest {
    @Test
    fun `valid portrait jpeg is plausible`() {
        val bytes = encode(Bitmap.CompressFormat.JPEG, 200, 300)
        assertTrue(CoverImageValidator.isPlausibleCover(bytes))
    }

    @Test
    fun `valid portrait png is plausible`() {
        val bytes = encode(Bitmap.CompressFormat.PNG, 400, 600)
        assertTrue(CoverImageValidator.isPlausibleCover(bytes))
    }

    @Test
    fun `portrait below dimension floor is rejected`() {
        // 100x150 is portrait but under the 200px floor; dense noise keeps the
        // payload above the byte gate so only the dimension check can reject.
        val bytes = encode(Bitmap.CompressFormat.PNG, 100, 150, noiseDensity = 1)
        assertTrue(bytes.size >= 3000)
        assertFalse(CoverImageValidator.isPlausibleCover(bytes))
    }

    @Test
    fun `tiny image is rejected`() {
        val bytes = encode(Bitmap.CompressFormat.JPEG, 10, 10)
        assertFalse(CoverImageValidator.isPlausibleCover(bytes))
    }

    @Test
    fun `square image is rejected`() {
        val bytes = encode(Bitmap.CompressFormat.JPEG, 300, 300)
        assertTrue(bytes.size >= 3000)
        assertFalse(CoverImageValidator.isPlausibleCover(bytes))
    }

    @Test
    fun `landscape banner is rejected`() {
        val bytes = encode(Bitmap.CompressFormat.JPEG, 600, 300)
        assertFalse(CoverImageValidator.isPlausibleCover(bytes))
    }

    @Test
    fun `tiny payload is rejected before decoding`() {
        val plausible = encode(Bitmap.CompressFormat.JPEG, 200, 300)
        assertFalse(CoverImageValidator.isPlausibleCover(plausible.copyOfRange(0, 100)))
    }

    @Test
    fun `garbage bytes are rejected`() {
        val garbage = Random(42).nextBytes(4096)
        assertFalse(CoverImageValidator.isPlausibleCover(garbage))
    }

    private fun encode(
        format: Bitmap.CompressFormat,
        width: Int,
        height: Int,
        noiseDensity: Int = 4,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.DKGRAY)
        // Scatter noise so compressed payloads clear the minimum-byte threshold.
        val random = Random(7)
        repeat(width * height / noiseDensity) {
            bitmap.setPixel(
                random.nextInt(width),
                random.nextInt(height),
                Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)),
            )
        }
        val output = ByteArrayOutputStream()
        assertTrue(bitmap.compress(format, 90, output))
        bitmap.recycle()
        return output.toByteArray()
    }
}
