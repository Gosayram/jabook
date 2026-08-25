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
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.ErrorSupportFragment
import androidx.leanback.app.PlaybackSupportFragment
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.ControlButtonPresenterSelector
import androidx.leanback.widget.PlaybackControlsRow
import androidx.leanback.widget.PlaybackControlsRow.PlayPauseAction
import androidx.leanback.widget.PlaybackControlsRow.SkipNextAction
import androidx.leanback.widget.PlaybackControlsRow.SkipPreviousAction
import androidx.leanback.widget.PlaybackControlsRowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import com.jabook.app.jabook.util.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * TV Player Activity for playing audiobooks on Android TV.
 *
 * Provides a 10-foot UI for playback controls using Leanback.
 */
@AndroidEntryPoint
public class TvPlayerActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bookId = intent.getStringExtra("book_id")
        if (bookId.isNullOrBlank()) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, TvPlaybackFragment.newInstance(bookId))
                .commitNow()
        }
    }
}

/**
 * Playback fragment for TV with transport controls.
 */
@AndroidEntryPoint
public class TvPlaybackFragment : PlaybackSupportFragment() {
    @Inject
    public lateinit var booksRepository: BooksRepository

    @Inject
    public lateinit var audioPlayerController: AudioPlayerController

    private var book: Book? = null
    private lateinit var playPauseAction: PlayPauseAction
    private lateinit var skipNextAction: SkipNextAction
    private lateinit var skipPreviousAction: SkipPreviousAction
    private lateinit var controlsRow: PlaybackControlsRow
    private lateinit var primaryActionsAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bookId = arguments?.getString(ARG_BOOK_ID) ?: return

        lifecycleScope.launch {
            try {
                book = booksRepository.getBook(bookId).first()
                val loaded = book
                if (loaded == null) {
                    showErrorState()
                } else {
                    setupPlayback(loaded)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                LogUtils.e("TvPlaybackFragment", "Error loading book", e)
                showErrorState()
            }
        }
    }

    private fun showErrorState() {
        if (!isAdded) return
        parentFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, TvErrorFragment())
            .commitAllowingStateLoss()
    }

    private fun setupPlayback(book: Book) {
        // Create playback controls
        playPauseAction = PlayPauseAction(requireContext())
        skipNextAction = SkipNextAction(requireContext())
        skipPreviousAction = SkipPreviousAction(requireContext())

        // Create controls row
        controlsRow = PlaybackControlsRow(book)

        // Primary actions adapter
        val presenterSelector = ControlButtonPresenterSelector()
        primaryActionsAdapter = ArrayObjectAdapter(presenterSelector)
        primaryActionsAdapter.add(skipPreviousAction)
        primaryActionsAdapter.add(playPauseAction)
        primaryActionsAdapter.add(skipNextAction)
        controlsRow.primaryActionsAdapter = primaryActionsAdapter

        // Create rows adapter
        val rowsAdapter =
            ArrayObjectAdapter(
                ClassPresenterSelector().apply {
                    addClassPresenter(PlaybackControlsRow::class.java, PlaybackControlsRowPresenter())
                },
            )
        rowsAdapter.add(controlsRow)
        adapter = rowsAdapter

        // Set up action click listener
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Action) {
                handleAction(item)
            }
        }

        // Load book into player
        lifecycleScope.launch {
            val chapters = booksRepository.getChapters(book.id).first()
            val filePaths = chapters.mapNotNull { chapter -> chapter.fileUrl }
            if (filePaths.isNotEmpty()) {
                audioPlayerController.loadBook(
                    filePaths = filePaths,
                    initialChapterIndex = book.currentChapterIndex,
                    initialPosition = book.currentPosition.inWholeMilliseconds,
                    autoPlay = true,
                    bookId = book.id,
                )
            }
        }

        // Observe playback state
        observePlaybackState()
    }

    private fun handleAction(action: Action) {
        when (action) {
            playPauseAction -> {
                val isPlaying = audioPlayerController.isPlaying.value
                if (isPlaying) {
                    audioPlayerController.pause()
                } else {
                    audioPlayerController.play()
                }
            }
            skipNextAction -> {
                audioPlayerController.skipToNext()
            }
            skipPreviousAction -> {
                audioPlayerController.skipToPrevious()
            }
        }
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                audioPlayerController.isPlaying.collect { isPlaying ->
                    playPauseAction.index =
                        if (isPlaying) {
                            PlayPauseAction.INDEX_PAUSE
                        } else {
                            PlayPauseAction.INDEX_PLAY
                        }
                    primaryActionsAdapter.notifyArrayItemRangeChanged(
                        primaryActionsAdapter.indexOf(playPauseAction),
                        1,
                    )
                }
            }
        }
    }

    public companion object {
        private const val ARG_BOOK_ID = "book_id"

        public fun newInstance(bookId: String): TvPlaybackFragment =
            TvPlaybackFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_BOOK_ID, bookId)
                    }
            }
    }
}

/**
 * Error screen shown on TV when the requested book cannot be loaded.
 * Provides a focusable Back button so the screen is never a dead end for d-pad users.
 */
public class TvErrorFragment : ErrorSupportFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.error)
        message = getString(R.string.book_not_found)
        buttonText = getString(R.string.backAction)
        buttonClickListener = View.OnClickListener { requireActivity().finish() }
    }
}
