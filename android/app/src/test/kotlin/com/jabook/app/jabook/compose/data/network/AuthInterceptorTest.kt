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

class AuthInterceptorTest {
    @Test
    fun `expired response is returned without re-login attempt or duplicate request`() {
        val request = Request.Builder().url("https://mirror.example/forum/viewtopic.php?t=1").build()
        val expiredResponse = response(request, 401)
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(expiredResponse)
        val interceptor = AuthInterceptor(NoOpLoggerFactory)

        val result = interceptor.intercept(chain)

        assertSame(expiredResponse, result)
        verify(chain).request()
        verify(chain).proceed(request)
        verifyNoMoreInteractions(chain)
    }

    @Test
    fun `login endpoint bypasses session-expiry detection`() {
        val request = Request.Builder().url("https://mirror.example/forum/login.php").build()
        val loginResponse = response(request, 403)
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(loginResponse)
        val interceptor = AuthInterceptor(NoOpLoggerFactory)

        val result = interceptor.intercept(chain)

        assertSame(loginResponse, result)
        verify(chain).request()
        verify(chain).proceed(request)
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
