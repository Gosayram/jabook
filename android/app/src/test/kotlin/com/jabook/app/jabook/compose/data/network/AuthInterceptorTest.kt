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

import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import javax.inject.Provider

class AuthInterceptorTest {
    @Test(expected = CancellationException::class)
    fun `cancellation while loading credentials is propagated`() {
        val request = Request.Builder().url("https://rutracker.net/forum/viewtopic.php?t=1").build()
        val chain = mock<Interceptor.Chain>()
        val authRepository = mock<AuthRepository>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(response(request, 401))
        kotlinx.coroutines.runBlocking {
            whenever(authRepository.getStoredCredentials()).thenThrow(CancellationException("cancelled"))
        }
        val interceptor = AuthInterceptor(Provider { authRepository }, NoOpLoggerFactory)

        interceptor.intercept(chain)
    }

    @Test
    fun `expired response without stored credentials is returned without a duplicate request`() {
        val request = Request.Builder().url("https://rutracker.net/forum/viewtopic.php?t=1").build()
        val expiredResponse = response(request, 401)
        val chain = mock<Interceptor.Chain>()
        val authRepository = mock<AuthRepository>()
        val repositoryProvider = Provider { authRepository }
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(expiredResponse)
        kotlinx.coroutines.runBlocking { whenever(authRepository.getStoredCredentials()).thenReturn(null) }
        val interceptor = AuthInterceptor(repositoryProvider, NoOpLoggerFactory)

        val result = interceptor.intercept(chain)

        assertSame(expiredResponse, result)
        verify(chain).request()
        verify(chain).proceed(request)
        verify(authRepository).getStoredCredentials()
        verifyNoMoreInteractions(chain)
    }

    private fun response(
        request: Request,
        code: Int,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Unauthorized")
            .build()
}
