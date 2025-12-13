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
import com.jabook.app.jabook.compose.domain.model.DownloadInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving active downloads.
 *
 * Returns a reactive list of all in-progress and queued downloads.
 */
class GetActiveDownloadsUseCase
    @Inject
    constructor(
        private val downloadRepository: DownloadRepository,
    ) {
        /**
         * Get all active downloads.
         *
         * @return Flow of active download list
         */
        operator fun invoke(): Flow<List<DownloadInfo>> = downloadRepository.getActiveDownloads()
    }
