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

import com.jabook.app.jabook.compose.data.remote.RuTrackerError

/**
 * Validation functions for HTML content before parsing.
 *
 * Based on Flow project analysis - provides centralized validation
 * logic to detect common error conditions before attempting to parse.
 *
 * These validators help identify:
 * - Missing topics
 * - Regional blocks
 * - Access restrictions
 * - Invalid responses
 */
internal object ParsingValidators {
    /**
     * Check if topic exists in HTML content.
     *
     * @param html HTML content to check
     * @return true if topic exists, false otherwise
     */
    public fun isTopicExists(html: String): Boolean {
        val lowerHtml = html.lowercase()
        return !lowerHtml.contains("тема не найдена") &&
            !lowerHtml.contains("тема находится в мусорке") &&
            !lowerHtml.contains("ошибочный запрос: не указан topic_id") &&
            !lowerHtml.contains("topic not found") &&
            !lowerHtml.contains("topic deleted") &&
            !lowerHtml.contains("invalid topic id")
    }

    /**
     * Check if content is blocked for user's region.
     *
     * @param html HTML content to check
     * @return true if blocked for region, false otherwise
     */
    public fun isBlockedForRegion(html: String): Boolean {
        val lowerHtml = html.lowercase()
        return lowerHtml.contains("извините, раздача недоступна для вашего региона") ||
            lowerHtml.contains("недоступна для вашего региона") ||
            lowerHtml.contains("region blocked") ||
            lowerHtml.contains("geo-blocked")
    }

    /**
     * Check if user needs to be authenticated to access content.
     *
     * @param html HTML content to check
     * @return true if authentication required, false otherwise
     */
    public fun requiresAuthentication(html: String): Boolean {
        val lowerHtml = html.lowercase()
        return lowerHtml.contains("войдите в систему") ||
            lowerHtml.contains("авторизация") ||
            lowerHtml.contains("profile.php?mode=register") ||
            lowerHtml.contains("login.php") &&
            lowerHtml.contains("name=\"login_username\"") ||
            lowerHtml.contains("please login") ||
            lowerHtml.contains("authentication required")
    }

    /**
     * Check if content is access forbidden (403).
     *
     * @param html HTML content to check
     * @return true if access forbidden, false otherwise
     */
    public fun isAccessForbidden(html: String): Boolean {
        val lowerHtml = html.lowercase()
        return lowerHtml.contains("доступ запрещен") ||
            lowerHtml.contains("access forbidden") ||
            lowerHtml.contains("403") &&
            lowerHtml.contains("forbidden") ||
            lowerHtml.contains("у вас нет прав для просмотра")
    }

    /**
     * Check if response indicates a bad request (400).
     *
     * @param html HTML content to check
     * @return true if bad request, false otherwise
     */
    public fun isBadRequest(html: String): Boolean {
        val lowerHtml = html.lowercase()
        // More specific patterns to avoid false positives
        // Check for explicit error messages, not just presence of "400" and "error" separately
        // Check for specific RuTracker error patterns (usually in a message box or title)
        // More strict to avoid false positives in forum posts
        return (lowerHtml.contains("неверный запрос") && lowerHtml.contains("class=\"maintitle\"")) ||
            (lowerHtml.contains("bad request") && lowerHtml.contains("<title>rubet.org :: 400 bad request</title>")) ||
            (lowerHtml.contains("bad request") && lowerHtml.contains("<h1>400 bad request</h1>")) ||
            (lowerHtml.contains("error 400") && lowerHtml.contains("bad request"))
    }

    /**
     * Check if HTML content is empty or invalid.
     *
     * @param html HTML content to check
     * @return true if content is valid, false otherwise
     */
    public fun isValidContent(html: String): Boolean =
        html.isNotBlank() &&
            html.length > 100 &&
            // Minimum reasonable HTML size
            html.contains("<html", ignoreCase = true) ||
            html.contains("<body", ignoreCase = true)

    /**
     * Validate HTML content and return appropriate error if validation fails.
     *
     * @param html HTML content to validate
     * @return RuTrackerError if validation fails, null if valid
     */
    public fun validateContent(html: String): RuTrackerError? {
        if (!isValidContent(html)) {
            return RuTrackerError.NoData
        }

        if (requiresAuthentication(html)) {
            return RuTrackerError.Unauthorized
        }

        if (isAccessForbidden(html)) {
            return RuTrackerError.Forbidden
        }

        if (isBadRequest(html)) {
            return RuTrackerError.BadRequest
        }

        if (isBlockedForRegion(html)) {
            return RuTrackerError.Forbidden // Regional block is a form of forbidden access
        }

        if (!isTopicExists(html)) {
            return RuTrackerError.NotFound
        }

        return null // Content is valid
    }

    /**
     * Validate search results HTML content.
     *
     * @param html HTML content to validate
     * @return RuTrackerError if validation fails, null if valid
     */
    public fun validateSearchResults(html: String): RuTrackerError? {
        if (!isValidContent(html)) {
            return RuTrackerError.NoData
        }

        if (requiresAuthentication(html)) {
            return RuTrackerError.Unauthorized
        }

        if (isAccessForbidden(html)) {
            return RuTrackerError.Forbidden
        }

        if (isBadRequest(html)) {
            return RuTrackerError.BadRequest
        }

        return null // Search results are valid
    }

    /**
     * Validate topic details HTML content.
     *
     * @param html HTML content to validate
     * @return RuTrackerError if validation fails, null if valid
     */
    public fun validateTopicDetails(html: String): RuTrackerError? {
        return validateContent(html) // Topic details use the same validation as general content
    }

    /**
     * Validate forum page HTML content.
     *
     * Forum pages are less strict than search results - they don't need to check for topic existence.
     *
     * @param html HTML content to validate
     * @return RuTrackerError if validation fails, null if valid
     */
    public fun validateForumPage(html: String): RuTrackerError? {
        if (!isValidContent(html)) {
            return RuTrackerError.NoData
        }

        if (requiresAuthentication(html)) {
            return RuTrackerError.Unauthorized
        }

        if (isAccessForbidden(html)) {
            return RuTrackerError.Forbidden
        }

        // For forum pages, only check for explicit bad request errors, not general patterns
        // This avoids false positives from normal forum HTML content
        val lowerHtml = html.lowercase()
        val hasExplicitBadRequest =
            lowerHtml.contains("неверный запрос") ||
                (lowerHtml.contains("bad request") && (lowerHtml.contains("400") || lowerHtml.contains("http"))) ||
                (lowerHtml.contains("invalid request") && (lowerHtml.contains("400") || lowerHtml.contains("http"))) ||
                (lowerHtml.contains("ошибка") && lowerHtml.contains("400") && lowerHtml.contains("запрос"))

        if (hasExplicitBadRequest) {
            return RuTrackerError.BadRequest
        }

        return null // Forum page is valid
    }
}
