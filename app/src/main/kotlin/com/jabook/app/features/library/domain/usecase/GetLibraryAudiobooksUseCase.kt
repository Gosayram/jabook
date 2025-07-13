package com.jabook.app.features.library.domain.usecase

import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.repository.AudiobookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Use case for retrieving audiobooks in the user's library. Provides different filtering options for the library view. */
class GetLibraryAudiobooksUseCase @Inject constructor(private val repository: AudiobookRepository) {
    /** Get all audiobooks in the library. */
    operator fun invoke(): Flow<List<Audiobook>> {
        return repository.getAllAudiobooks()
    }

    /** Get audiobooks by category. */
    fun getByCategory(category: String): Flow<List<Audiobook>> {
        return repository.getAudiobooksByCategory(category)
    }

    /** Get favorite audiobooks. */
    fun getFavorites(): Flow<List<Audiobook>> {
        return repository.getFavoriteAudiobooks()
    }

    /** Get currently playing audiobooks. */
    fun getCurrentlyPlaying(): Flow<List<Audiobook>> {
        return repository.getCurrentlyPlayingAudiobooks()
    }

    /** Get completed audiobooks. */
    fun getCompleted(): Flow<List<Audiobook>> {
        return repository.getCompletedAudiobooks()
    }

    /** Get downloaded audiobooks. */
    fun getDownloaded(): Flow<List<Audiobook>> {
        return repository.getDownloadedAudiobooks()
    }

    /** Search audiobooks by query. */
    fun search(query: String): Flow<List<Audiobook>> {
        return repository.searchAudiobooks(query)
    }

    /** Get all available categories. */
    fun getCategories(): Flow<List<String>> {
        return repository.getAllCategories()
    }
}
