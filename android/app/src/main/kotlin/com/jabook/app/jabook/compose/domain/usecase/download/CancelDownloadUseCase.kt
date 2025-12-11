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
import com.jabook.app.jabook.compose.domain.model.Result
import javax.inject.Inject

/**
 * Use case for canceling a download.
 *
 * Stops the download and removes from queue.
 */
class CancelDownloadUseCase
    @Inject
    constructor(
        private val downloadRepository: DownloadRepository,
    ) {
        /**
         * Cancel a download.
         *
         * @param bookId ID of the book download to cancel
         * @return Result indicating success or failure
         */
        suspend operator fun invoke(bookId: String): Result<Unit> =
            try {
                downloadRepository.cancelDownload(bookId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
    }
