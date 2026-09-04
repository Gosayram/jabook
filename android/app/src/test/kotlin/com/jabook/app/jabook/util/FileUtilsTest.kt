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

package com.jabook.app.jabook.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileUtilsTest {
    @Test
    fun resolvePathFromUri_primaryStorage() {
        assertEquals(
            "/storage/emulated/0/Music/A.mp3",
            FileUtils.resolvePathFromUri(
                "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FA.mp3",
            ),
        )
    }

    @Test
    fun resolvePathFromUri_sdCard() {
        assertEquals(
            "/storage/1234-5678/Music/B.mp3",
            FileUtils.resolvePathFromUri(
                "content://com.android.externalstorage.documents/tree/1234-5678%3AMusic/document/1234-5678%3AMusic%2FB.mp3",
            ),
        )
    }

    @Test
    fun resolvePathFromUri_simpleTree() {
        assertEquals(
            "/storage/emulated/0/Books",
            FileUtils.resolvePathFromUri(
                "content://com.android.externalstorage.documents/tree/primary%3ABooks",
            ),
        )
    }

    @Test
    fun resolvePathFromUri_nonSafUri_passesThrough() {
        val uri = "content://some.other.provider/files/xyz"
        assertEquals(uri, FileUtils.resolvePathFromUri(uri))
    }

    @Test
    fun resolvePathFromUri_fileScheme_passesThrough() {
        val uri = "file:///storage/emulated/0/Book.mp3"
        assertEquals(uri, FileUtils.resolvePathFromUri(uri))
    }

    @Test
    fun resolvePathFromUri_noColonInPath_passesThrough() {
        val uri = "content://com.android.externalstorage.documents/tree/primary"
        assertEquals(uri, FileUtils.resolvePathFromUri(uri))
    }
}
