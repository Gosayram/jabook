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

import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileUtilsTest {
    // Primary root comes from the same seam FileUtils uses, so the test holds on
    // any user/work profile (primary volume is /storage/emulated/<userId>).
    private fun primaryRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    @Test
    fun resolvePathFromUri_primaryStorage() {
        assertEquals(
            "${primaryRoot()}/Music/A.mp3",
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
            "${primaryRoot()}/Books",
            FileUtils.resolvePathFromUri(
                "content://com.android.externalstorage.documents/tree/primary%3ABooks",
            ),
        )
    }

    @Test
    fun resolvePathFromUri_folderNameContainingColon_resolvesFully() {
        assertEquals(
            "${primaryRoot()}/Music/A:B/track.mp3",
            FileUtils.resolvePathFromUri(
                "content://com.android.externalstorage.documents/document/primary%3AMusic%2FA%3AB%2Ftrack.mp3",
            ),
        )
    }

    @Test
    fun resolvePathFromUri_sdTree_resolvesToVolumePath() {
        val resolved =
            FileUtils.resolvePathFromUri(
                "content://com.android.externalstorage.documents/tree/1234-5678%3ABooks",
            )
        assertEquals("/storage/1234-5678/Books", resolved)
        assertFalse(resolved.contains("emulated/0"))
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
