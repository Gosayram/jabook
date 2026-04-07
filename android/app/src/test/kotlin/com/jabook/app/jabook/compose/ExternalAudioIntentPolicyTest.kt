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

package com.jabook.app.jabook.compose

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalAudioIntentPolicyTest {
    @Test
    fun `extractAudioUris returns data uri for audio view action`() {
        val uri = Uri.parse("content://media/external/audio/media/book.mp3")
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/mpeg")
            }

        val uris = ExternalAudioIntentPolicy.extractAudioUris(intent)

        assertEquals(listOf(uri), uris)
    }

    @Test
    fun `extractAudioUris returns stream uri for audio send action`() {
        val uri = Uri.parse("content://share/audio/42")
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
            }

        val uris = ExternalAudioIntentPolicy.extractAudioUris(intent)

        assertEquals(listOf(uri), uris)
    }

    @Test
    fun `extractAudioUris returns multiple stream uris for audio send multiple action`() {
        val first = Uri.parse("file:///storage/emulated/0/Music/a.mp3")
        val second = Uri.parse("file:///storage/emulated/0/Music/b.mp3")
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            }

        val uris = ExternalAudioIntentPolicy.extractAudioUris(intent)

        assertEquals(listOf(first, second), uris)
    }

    @Test
    fun `extractAudioUris returns empty for non audio mime type`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://media/external/images/media/1")
                type = "image/jpeg"
            }

        val uris = ExternalAudioIntentPolicy.extractAudioUris(intent)

        assertTrue(uris.isEmpty())
    }

    @Test
    fun `buildExternalGroupPath is stable for same uri set`() {
        val uris = listOf(Uri.parse("content://media/audio/10"), Uri.parse("content://media/audio/11"))

        val first = ExternalAudioIntentPolicy.buildExternalGroupPath(uris)
        val second = ExternalAudioIntentPolicy.buildExternalGroupPath(uris)

        assertEquals(first, second)
    }
}
