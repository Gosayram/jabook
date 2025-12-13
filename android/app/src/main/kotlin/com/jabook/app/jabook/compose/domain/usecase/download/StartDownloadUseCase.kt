// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.domain.usecase.download

import com.jabook.app.jabook.compose.data.repository.DownloadRepository
import com.jabook.app.jabook.compose.domain.model.DownloadState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for starting a book download.
 *
 * Enqueues a download task and returns a Flow to observe download progress.
 */
class StartDownloadUseCase
    @Inject
    constructor(
        private val downloadRepository: DownloadRepository,
    ) {
        /**
         * Start downloading a book.
         *
         * @param bookId ID of the book to download
         * @param torrentUrl URL of the torrent file
         * @return Flow of download state updates
         */
        operator fun invoke(
            bookId: String,
            torrentUrl: String,
        ): Flow<DownloadState> = downloadRepository.startDownload(bookId, torrentUrl)
    }
