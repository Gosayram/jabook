package com.jabook.app.core.di

import com.jabook.app.core.data.repository.TorrentRepositoryImpl
import com.jabook.app.core.domain.repository.TorrentRepository
import com.jabook.app.core.storage.FileManager
import com.jabook.app.core.torrent.TorrentManager
import com.jabook.app.core.torrent.TorrentManagerImpl
import com.jabook.app.shared.debug.IDebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for torrent system dependencies Provides torrent repository and related services */
@Module
@InstallIn(SingletonComponent::class)
object TorrentModule {

    /**
     * Provides torrent repository Uses mock implementation for now
     *
     * FIXME: Replace with actual LibTorrent implementation
     */
    @Provides
    @Singleton
    fun provideTorrentRepository(debugLogger: IDebugLogger): TorrentRepository {
        return TorrentRepositoryImpl(debugLogger)
    }

    /** Provides torrent manager for direct torrent operations */
    @Provides
    @Singleton
    fun provideTorrentManager(fileManager: FileManager, debugLogger: IDebugLogger): TorrentManager {
        return TorrentManagerImpl(fileManager, debugLogger)
    }
}
