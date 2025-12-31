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

package com.jabook.app.jabook.compose.data.remote.parser

import android.util.Log
import com.jabook.app.jabook.compose.data.remote.model.AudioTrack
import com.jabook.app.jabook.compose.data.remote.model.GeneralInfo
import com.jabook.app.jabook.compose.data.remote.model.MediaInfo
import com.jabook.app.jabook.compose.data.remote.model.SubtitleTrack
import com.jabook.app.jabook.compose.data.remote.model.VideoTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for MediaInfo text output.
 *
 * Converts unstructured MediaInfo text into structured data models.
 */
@Singleton
class MediaInfoParser
    @Inject
    constructor() {
        companion object {
            private const val TAG = "MediaInfoParser"
        }

        /**
         * Parse MediaInfo text into structured data.
         *
         * @param mediaInfoText Raw MediaInfo text output
         * @return Parsed MediaInfo or null if parsing fails
         */
        fun parse(mediaInfoText: String): MediaInfo? {
            try {
                val sections = splitIntoSections(mediaInfoText)

                val generalSection = sections["general"] ?: sections["общее"]
                val videoSection = sections["video"] ?: sections["видео"]
                val audioSections = findAllAudioSections(sections)
                val textSections = findAllTextSections(sections)

                val general = parseGeneral(generalSection ?: "")
                val video = videoSection?.let { parseVideo(it) }?.let { listOf(it) } ?: emptyList()
                val audio = audioSections.mapNotNull { parseAudio(it) }
                val text = textSections.mapNotNull { parseSubtitle(it) }

                return MediaInfo(
                    general = general,
                    video = video,
                    audio = audio,
                    text = text,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse MediaInfo", e)
                return null
            }
        }

        /**
         * Split MediaInfo text into sections (General, Video, Audio, Text).
         */
        private fun splitIntoSections(text: String): Map<String, String> {
            val sections = mutableMapOf<String, String>()
            val lines = text.lines()

            var currentSection = "unknown"
            val currentContent = StringBuilder()

            for (line in lines) {
                val trimmed = line.trim()

                // Detect section headers (rus/eng)
                when {
                    trimmed.matches(Regex("^(General|Общее|Общая информация).*", RegexOption.IGNORE_CASE)) -> {
                        if (currentSection != "unknown") {
                            sections[currentSection.lowercase()] = currentContent.toString()
                        }
                        currentSection = "general"
                        currentContent.clear()
                    }
                    trimmed.matches(Regex("^(Video|Видео).*", RegexOption.IGNORE_CASE)) -> {
                        if (currentSection != "unknown") {
                            sections[currentSection.lowercase()] = currentContent.toString()
                        }
                        currentSection = "video"
                        currentContent.clear()
                    }
                    trimmed.matches(Regex("^(Audio|Аудио).*[#№]?\\s*\\d*", RegexOption.IGNORE_CASE)) -> {
                        if (currentSection != "unknown") {
                            sections[currentSection.lowercase()] = currentContent.toString()
                        }
                        // Store audio tracks separately
                        val trackNum = Regex("\\d+").find(trimmed)?.value ?: "1"
                        currentSection = "audio_$trackNum"
                        currentContent.clear()
                    }
                    trimmed.matches(Regex("^(Text|Текст|Субтитры).*[#№]?\\s*\\d*", RegexOption.IGNORE_CASE)) -> {
                        if (currentSection != "unknown") {
                            sections[currentSection.lowercase()] = currentContent.toString()
                        }
                        val trackNum = Regex("\\d+").find(trimmed)?.value ?: "1"
                        currentSection = "text_$trackNum"
                        currentContent.clear()
                    }
                    else -> {
                        currentContent.appendLine(line)
                    }
                }
            }

            // Add last section
            if (currentSection != "unknown") {
                sections[currentSection.lowercase()] = currentContent.toString()
            }

            return sections
        }

        private fun findAllAudioSections(sections: Map<String, String>): List<String> =
            sections
                .filterKeys {
                    it.startsWith("audio")
                }.values
                .toList()

        private fun findAllTextSections(sections: Map<String, String>): List<String> =
            sections.filterKeys { it.startsWith("text") }.values.toList()

        private fun parseGeneral(text: String): GeneralInfo =
            GeneralInfo(
                format = extractField(text, "format", "формат"),
                duration = extractField(text, "duration", "продолжительность", "время звучания"),
                fileSize = extractField(text, "file size", "размер", "размер файла"),
                bitrate = extractField(text, "overall bit rate", "общий битрейт", "битрейт"),
                title = extractField(text, "title", "название", "имя"),
            )

        private fun parseVideo(text: String): VideoTrack =
            VideoTrack(
                codec = extractField(text, "codec", "codec id", "кодек", "видео кодек", "format"),
                profile = extractField(text, "format profile", "профиль"),
                resolution = extractResolution(text),
                frameRate = extractField(text, "frame rate", "частота кадров", "fps"),
                bitrate = extractField(text, "bit rate", "битрейт"),
                aspectRatio = extractField(text, "display aspect ratio", "aspect ratio", "соотношение сторон"),
                colorSpace = extractField(text, "color space", "цветовое пространство"),
            )

        private fun parseAudio(text: String): AudioTrack =
            AudioTrack(
                codec = extractField(text, "codec", "codec id", "кодек", "аудио кодек", "format"),
                codecInfo = extractField(text, "format/Info", "commercial name"),
                bitrate = extractField(text, "bit rate", "битрейт"),
                channels = extractField(text, "channel", "каналы", "канал"),
                samplingRate = extractField(text, "sampling rate", "частота дискретизации", "частота"),
                language = extractField(text, "language", "язык"),
                title = extractField(text, "title", "название"),
            )

        private fun parseSubtitle(text: String): SubtitleTrack {
            val forced = text.contains("forced", ignoreCase = true) || text.contains("принудительно", ignoreCase = true)

            return SubtitleTrack(
                format = extractField(text, "codec", "format", "формат"),
                language = extractField(text, "language", "язык"),
                title = extractField(text, "title", "название"),
                forced = forced,
            )
        }

        /**
         * Extract a field value from MediaInfo text by trying multiple field name patterns.
         */
        private fun extractField(
            text: String,
            vararg fieldNames: String,
        ): String? {
            for (fieldName in fieldNames) {
                // Try exact match with colon
                val pattern1 = Regex("$fieldName\\s*[:：]\\s*(.+?)(?=(\\n|$))", RegexOption.IGNORE_CASE)
                val match1 = pattern1.find(text)
                if (match1 != null) {
                    return match1.groupValues[1].trim()
                }

                // Try with "..." at end
                val pattern2 = Regex("$fieldName\\s*[:：]\\s*(.+?)\\.\\.\\..*", RegexOption.IGNORE_CASE)
                val match2 = pattern2.find(text)
                if (match2 != null) {
                    return match2.groupValues[1].trim()
                }
            }

            return null
        }

        /**
         * Extract resolution from video section.
         * Tries to build from width + height or find direct resolution string.
         */
        private fun extractResolution(text: String): String? {
            // Try direct resolution pattern (e.g., "1920 x 1080")
            val directPattern = Regex("(\\d+)\\s*[xх×]\\s*(\\d+)", RegexOption.IGNORE_CASE)
            val directMatch = directPattern.find(text)
            if (directMatch != null) {
                return "${directMatch.groupValues[1]} x ${directMatch.groupValues[2]}"
            }

            // Try width/height separately
            val width = extractField(text, "width", "ширина")
            val height = extractField(text, "height", "высота")

            return if (width != null && height != null) {
                "$width x $height"
            } else {
                null
            }
        }
    }
