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

package com.jabook.app.jabook.compose.di

import com.jabook.app.jabook.compose.data.download.LibTorrentDownloader
import com.jabook.app.jabook.compose.data.download.TorrentDownloader
import com.jabook.app.jabook.compose.data.torrent.TorrentSession
import com.jabook.app.jabook.compose.data.torrent.TorrentSessionAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for download-related dependencies.
 *
 * Legacy WorkManager-based download system has been removed.
 * All downloads now use the torrent-based system (TorrentDownloadRepository).
 */
@Module
@InstallIn(SingletonComponent::class)
public abstract class DownloadModule {
    /**
     * Bind TorrentDownloader implementation.
     *
     * Uses production LibTorrentDownloader with libtorrent4j.
     */
    @Binds
    @Singleton
    public abstract fun bindTorrentDownloader(impl: LibTorrentDownloader): TorrentDownloader

    /**
     * Bind [TorrentSession] to the [TorrentSessionAdapter] wrapper.
     *
     * This allows production code to depend on the testable [TorrentSession]
     * abstraction while Hilt injects the real libtorrent4j-backed adapter.
     */
    @Binds
    @Singleton
    public abstract fun bindTorrentSession(impl: TorrentSessionAdapter): TorrentSession
}
