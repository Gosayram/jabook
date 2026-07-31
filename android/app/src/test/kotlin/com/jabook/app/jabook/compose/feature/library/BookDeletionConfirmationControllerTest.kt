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

package com.jabook.app.jabook.compose.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookDeletionConfirmationControllerTest {
    @Test
    fun `confirmation consumes a pending delete only once`() {
        val controller = BookDeletionConfirmationController()

        assertEquals("book-1", controller.request("book-1"))
        assertEquals("book-1", controller.confirm())
        assertNull(controller.confirm())
    }

    @Test
    fun `a second request cannot replace the pending book`() {
        val controller = BookDeletionConfirmationController()

        assertEquals("book-1", controller.request("book-1"))
        assertNull(controller.request("book-2"))
        assertEquals("book-1", controller.confirm())
    }

    @Test
    fun `dismiss discards the pending delete`() {
        val controller = BookDeletionConfirmationController()

        controller.request("book-1")
        controller.dismiss()

        assertNull(controller.confirm())
    }
}
