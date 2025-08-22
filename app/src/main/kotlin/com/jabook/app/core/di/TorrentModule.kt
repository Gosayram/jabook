package com.jabook.app.core.di

import com.jabook.app.core.torrent.TorrentManager
import com.jabook.app.core.torrent.TorrentManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TorrentModule {
  @Binds @Singleton
  abstract fun bindTorrentManager(torrentManagerImpl: TorrentManagerImpl): TorrentManager

  // Real LibTorrent4j implementation will be added later
  // This module currently provides mock implementation for development
}
