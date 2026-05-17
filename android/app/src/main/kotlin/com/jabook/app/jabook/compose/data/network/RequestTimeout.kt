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

package com.jabook.app.jabook.compose.data.network

/**
 * Per-request timeout override for Retrofit endpoints.
 *
 * Apply to suspend fun declarations in API interfaces to override
 * the global OkHttpClient timeouts for that specific endpoint.
 *
 * Example:
 * ```
 * @GET("forum/tracker.php")
 * @RequestTimeout(connectMs = 5_000, readMs = 10_000, writeMs = 5_000)
 * suspend fun searchTopics(@Query("nm") query: String): TopicSearchResponse
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class RequestTimeout(
    val connectMs: Long,
    val readMs: Long,
    val writeMs: Long,
)
