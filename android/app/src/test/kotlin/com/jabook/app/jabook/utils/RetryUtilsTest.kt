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

package com.jabook.app.jabook.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RetryUtilsTest {
    @Test
    fun `retryWithBackoff uses retry-after delay override when provided`() =
        runTest {
            val delays = mutableListOf<Long>()
            var nowMs = 0L
            var attempts = 0

            val result =
                retryWithBackoff(
                    RetryConfig(
                        maxRetries = 2,
                        initialDelayMs = 10,
                        shouldRetry = { it is RetryableHttpException },
                        delayOverrideMs = { throwable, _ ->
                            (throwable as? RetryableHttpException)?.retryAfterMs
                        },
                        nowMsProvider = { nowMs },
                        delayProvider = { delayMs ->
                            delays += delayMs
                            nowMs += delayMs
                        },
                    ),
                ) {
                    attempts++
                    if (attempts == 1) {
                        throw RetryableHttpException(statusCode = 429, retryAfterMs = 1_500L)
                    }
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(listOf(1_500L), delays)
            assertEquals(2, attempts)
        }

    @Test
    fun `retryWithBackoff stops when next delay exceeds retry budget`() =
        runTest {
            var attempts = 0
            var nowMs = 0L
            val expected = IOException("transient")

            try {
                retryWithBackoff(
                    RetryConfig(
                        maxRetries = 4,
                        initialDelayMs = 1_000L,
                        maxElapsedTimeMs = 1_500L,
                        shouldRetry = { it is IOException },
                        nowMsProvider = { nowMs },
                        delayProvider = { delayMs -> nowMs += delayMs },
                    ),
                ) {
                    attempts++
                    throw expected
                }
                fail("Expected exception to be thrown")
            } catch (actual: IOException) {
                assertSame(expected, actual)
                assertEquals(2, attempts)
            }
        }

    @Test
    fun `retryWithBackoff retries and returns successful result`() =
        runTest {
            var attempts = 0
            val result =
                retryWithBackoff(
                    RetryConfig(
                        maxRetries = 2,
                        initialDelayMs = 10,
                        shouldRetry = { it is IOException },
                    ),
                ) {
                    attempts++
                    if (attempts < 3) {
                        throw IOException("transient")
                    }
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(3, attempts)
        }

    @Test
    fun `retryWithBackoff does not retry non-retriable exception`() =
        runTest {
            var attempts = 0
            val expected = IllegalStateException("fatal")

            try {
                retryWithBackoff(
                    RetryConfig(
                        maxRetries = 3,
                        initialDelayMs = 10,
                        shouldRetry = { it is IOException },
                    ),
                ) {
                    attempts++
                    throw expected
                }
                fail("Expected exception to be thrown")
            } catch (actual: IllegalStateException) {
                assertSame(expected, actual)
                assertEquals(1, attempts)
            }
        }

    @Test
    fun `retryWithBackoff rethrows last exception after max retries`() =
        runTest {
            var attempts = 0
            val expected = IOException("still failing")

            try {
                retryWithBackoff(
                    RetryConfig(
                        maxRetries = 2,
                        initialDelayMs = 10,
                        shouldRetry = { it is IOException },
                    ),
                ) {
                    attempts++
                    throw expected
                }
                fail("Expected exception to be thrown")
            } catch (actual: IOException) {
                assertSame(expected, actual)
                assertEquals(3, attempts)
            }
        }
}
