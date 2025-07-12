package com.jabook.app.core.di

import com.jabook.app.features.player.PlayerManager
import com.jabook.app.features.player.PlayerManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for player dependencies Provides PlayerManager implementation */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    /** Binds PlayerManagerImpl to PlayerManager interface */
    @Binds @Singleton abstract fun bindPlayerManager(playerManagerImpl: PlayerManagerImpl): PlayerManager
}
