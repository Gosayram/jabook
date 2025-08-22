package com.jabook.app.core.di

import com.jabook.app.core.data.repository.AudiobookRepositoryImpl
import com.jabook.app.core.data.repository.TorrentRepositoryImpl
import com.jabook.app.core.domain.repository.AudiobookRepository
import com.jabook.app.core.domain.repository.TorrentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for binding repository implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    /** Bind AudiobookRepositoryImpl to AudiobookRepository interface. */
    @Binds @Singleton
    abstract fun bindAudiobookRepository(audiobookRepositoryImpl: AudiobookRepositoryImpl): AudiobookRepository

    /** Bind TorrentRepositoryImpl to TorrentRepository interface. */
    @Binds @Singleton
    abstract fun bindTorrentRepository(torrentRepositoryImpl: TorrentRepositoryImpl): TorrentRepository
}
