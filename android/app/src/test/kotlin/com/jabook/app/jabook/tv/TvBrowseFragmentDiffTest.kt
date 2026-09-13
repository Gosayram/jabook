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

package com.jabook.app.jabook.tv

import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jabook.app.jabook.compose.domain.model.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class TvBrowseFragmentDiffTest {
    private fun book(
        id: Long,
        title: String,
        author: String = "Author A",
    ): Book =
        Book(
            id = id.toString(),
            title = title,
            author = author,
            coverUrl = null,
            description = null,
            totalDuration = kotlin.time.Duration.ZERO,
            currentPosition = kotlin.time.Duration.ZERO,
            progress = 0f,
            currentChapterIndex = 0,
            downloadStatus = com.jabook.app.jabook.compose.data.model.DownloadStatus.NOT_DOWNLOADED,
            downloadProgress = 1f,
            localPath = "/tmp/$id.mp3",
            addedDate = 0L,
            lastPlayedDate = null,
            isFavorite = false,
            sourceUrl = null,
        )

    private fun row(
        name: String,
        books: List<Book>,
    ): ListRow {
        val adapter = ArrayObjectAdapter(TvCardPresenter())
        books.forEach(adapter::add)
        return ListRow(HeaderItem(name), adapter)
    }

    @Test
    public fun sameHeaderIsSameItem() {
        val callback = TvBrowseFragment.BookRowDiffCallback()
        assertTrue(callback.areItemsTheSame(row("A", emptyList()), row("A", emptyList())))
        assertFalse(callback.areItemsTheSame(row("A", emptyList()), row("B", emptyList())))
    }

    @Test
    public fun sameBooksSameOrderAreSameContents() {
        val callback = TvBrowseFragment.BookRowDiffCallback()
        val books = listOf(book(1, "T1"), book(2, "T2"))
        assertTrue(callback.areContentsTheSame(row("A", books), row("A", books)))
    }

    @Test
    public fun differentBooksOrOrderAreDifferentContents() {
        val callback = TvBrowseFragment.BookRowDiffCallback()
        val old = listOf(book(1, "T1"), book(2, "T2"))
        assertFalse(callback.areContentsTheSame(row("A", old), row("A", listOf(book(1, "T1")))))
        assertFalse(callback.areContentsTheSame(row("A", old), row("A", listOf(book(2, "T2"), book(1, "T1")))))
        assertFalse(callback.areContentsTheSame(row("A", old), row("A", listOf(book(1, "T1-updated"), book(2, "T2")))))
    }

    @Test
    public fun diffReportsNoChangeForUnchangedRowsOnly() {
        // setItems stores the new instances, but focus is preserved because DiffUtil
        // dispatches no change event when areContentsTheSame=true (no re-bind happens).
        // This test pins exactly that contract per position.
        val adapter = ArrayObjectAdapter(ListRowPresenter())
        val oldRows =
            listOf(
                row("Author A", listOf(book(1, "T1"))),
                row("Author B", listOf(book(2, "Old"))),
            )
        oldRows.forEach(adapter::add)

        val newRows =
            listOf(
                row("Author A", listOf(book(1, "T1"))),
                row("Author B", listOf(book(2, "New"), book(3, "T3"))),
            )
        adapter.setItems(newRows, TvBrowseFragment.BookRowDiffCallback())

        assertEquals(2, adapter.size())
        val callback = TvBrowseFragment.BookRowDiffCallback()
        // Unchanged row → same contents → no change event → ViewHolder (and focus) kept
        assertTrue(callback.areContentsTheSame(oldRows[0], adapter.get(0) as ListRow))
        // Changed row → new contents → change event → row re-bound
        assertFalse(callback.areContentsTheSame(oldRows[1], adapter.get(1) as ListRow))
    }
}
