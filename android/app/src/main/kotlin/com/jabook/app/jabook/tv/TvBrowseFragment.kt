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

import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.domain.model.Book
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main browse fragment for Android TV.
 *
 * Displays audiobook library in a row-based layout optimized for TV.
 * Uses Leanback BrowseSupportFragment for the 10-foot UI experience.
 */
@AndroidEntryPoint
public class TvBrowseFragment : BrowseSupportFragment() {
    @Inject
    public lateinit var booksRepository: BooksRepository

    private lateinit var rowsAdapter: ArrayObjectAdapter

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        // Set title and branding
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // Set search affordance (optional - can implement search later)
        // setOnSearchClickedListener { /* Navigate to search */ }

        // Row click listener
        onItemViewClickedListener =
            OnItemViewClickedListener { _: Presenter.ViewHolder?, item: Any?, _: RowPresenter.ViewHolder?, _: Row? ->
                if (item is Book) {
                    // Launch player for selected book
                    launchPlayer(item)
                }
            }

        // Initialize rows adapter
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val books = booksRepository.getAllBooks().first()

                if (books.isEmpty()) {
                    showEmptyState()
                    return@launch
                }

                // Create book presenter
                val cardPresenter = TvCardPresenter()

                // Group books by author or show all in one row
                val booksByAuthor: Map<String, List<Book>> = books.groupBy { book -> book.author }

                if (booksByAuthor.size > 1) {
                    // Multiple authors - show rows per author
                    booksByAuthor.forEach { (author: String, authorBooks: List<Book>) ->
                        val header = HeaderItem(author)
                        val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                        authorBooks.forEach { book: Book ->
                            listRowAdapter.add(book)
                        }
                        rowsAdapter.add(ListRow(header, listRowAdapter))
                    }
                } else {
                    // Single author or all books - show in one row
                    val header = HeaderItem(getString(R.string.library))
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    books.forEach { book: Book ->
                        listRowAdapter.add(book)
                    }
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }
            } catch (e: Exception) {
                android.util.Log.e("TvBrowseFragment", "Error loading books", e)
                showEmptyState()
            }
        }
    }

    private fun showEmptyState() {
        // Show empty state row
        val header = HeaderItem(getString(R.string.library))
        val emptyAdapter = ArrayObjectAdapter(TvCardPresenter())
        rowsAdapter.add(ListRow(header, emptyAdapter))
    }

    private fun launchPlayer(book: Book) {
        // Start TvPlayerActivity with selected book
        val intent =
            android.content.Intent(requireContext(), TvPlayerActivity::class.java).apply {
                putExtra("book_id", book.id)
            }
        startActivity(intent)
    }
}
