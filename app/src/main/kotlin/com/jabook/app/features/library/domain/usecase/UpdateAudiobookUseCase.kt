package com.jabook.app.features.library.domain.usecase

import com.jabook.app.core.domain.repository.AudiobookRepository
import javax.inject.Inject

/**
 * Use case for updating audiobook properties.
 * Handles various audiobook update operations.
 */
class UpdateAudiobookUseCase
    @Inject
    constructor(
        private val repository: AudiobookRepository,
    ) {
        /**
         * Update playback position for an audiobook.
         */
        suspend fun updatePlaybackPosition(
            id: String,
            positionMs: Long,
        ) {
            repository.updatePlaybackPosition(id, positionMs)
        }

        /**
         * Toggle favorite status of an audiobook.
         */
        suspend fun toggleFavorite(
            id: String,
            isFavorite: Boolean,
        ) {
            repository.updateFavoriteStatus(id, isFavorite)
        }

        /**
         * Mark audiobook as completed.
         */
        suspend fun markAsCompleted(id: String) {
            repository.updateCompletionStatus(id, true)
        }

        /**
         * Mark audiobook as not completed.
         */
        suspend fun markAsNotCompleted(id: String) {
            repository.updateCompletionStatus(id, false)
        }

        /**
         * Update user rating for an audiobook.
         */
        suspend fun updateRating(
            id: String,
            rating: Float?,
        ) {
            repository.updateUserRating(id, rating)
        }

        /**
         * Update playback speed for an audiobook.
         */
        suspend fun updatePlaybackSpeed(
            id: String,
            speed: Float,
        ) {
            repository.updatePlaybackSpeed(id, speed)
        }

        /**
         * Reset playback position to beginning.
         */
        suspend fun resetPlayback(id: String) {
            repository.updatePlaybackPosition(id, 0)
            repository.updateCompletionStatus(id, false)
        }
    }
