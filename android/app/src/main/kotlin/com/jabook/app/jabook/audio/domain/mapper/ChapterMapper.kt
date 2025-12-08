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

package com.jabook.app.jabook.audio.domain.mapper

import com.jabook.app.jabook.audio.core.model.Chapter
import com.jabook.app.jabook.audio.data.local.database.entity.ChapterMetadataEntity

/**
 * Mapper for converting between Chapter domain models and ChapterMetadataEntity.
 */
object ChapterMapper {
    /**
     * Converts ChapterMetadataEntity to Chapter domain model.
     */
    fun toDomain(entity: ChapterMetadataEntity): Chapter =
        Chapter(
            id = entity.id,
            title = entity.title,
            fileIndex = entity.fileIndex,
            filePath = entity.filePath,
            startTime = entity.startTime,
            endTime = entity.endTime,
            duration = entity.duration,
        )

    /**
     * Converts list of ChapterMetadataEntity to list of Chapter domain models.
     */
    fun toDomainList(entities: List<ChapterMetadataEntity>): List<Chapter> = entities.map { toDomain(it) }

    /**
     * Converts Chapter domain model to ChapterMetadataEntity.
     */
    fun toEntity(
        chapter: Chapter,
        bookId: String,
    ): ChapterMetadataEntity =
        ChapterMetadataEntity(
            id = chapter.id,
            bookId = bookId,
            fileIndex = chapter.fileIndex,
            title = chapter.title,
            filePath = chapter.filePath,
            startTime = chapter.startTime,
            endTime = chapter.endTime,
            duration = chapter.duration,
            lastUpdated = System.currentTimeMillis(),
        )

    /**
     * Converts list of Chapter domain models to list of ChapterMetadataEntity.
     */
    fun toEntityList(
        chapters: List<Chapter>,
        bookId: String,
    ): List<ChapterMetadataEntity> = chapters.map { toEntity(it, bookId) }
}
