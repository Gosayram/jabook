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

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Invocation
import java.util.concurrent.TimeUnit

private interface StubApi {
    @RequestTimeout(connectMs = 5_000L, readMs = 10_000L, writeMs = 15_000L)
    fun annotatedEndpoint(query: String)

    @RequestTimeout(connectMs = -1L, readMs = 0L, writeMs = Long.MAX_VALUE)
    fun edgeCaseTimeouts()
}

class DynamicTimeoutInterceptorTest {
    private val interceptor = DynamicTimeoutInterceptor()

    private fun buildChain(
        request: Request,
        response: Response = buildResponse(request),
    ): Interceptor.Chain {
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(response)
        whenever(chain.withConnectTimeout(any<Int>(), any<TimeUnit>())).thenReturn(chain)
        whenever(chain.withReadTimeout(any<Int>(), any<TimeUnit>())).thenReturn(chain)
        whenever(chain.withWriteTimeout(any<Int>(), any<TimeUnit>())).thenReturn(chain)
        return chain
    }

    private fun buildResponse(request: Request): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

    @Test
    fun `request without Invocation tag proceeds without timeout modifications`() {
        val request =
            Request
                .Builder()
                .url("https://example.com/api")
                .build()
        val chain = buildChain(request)

        val result = interceptor.intercept(chain)

        verify(chain).proceed(request)
        verify(chain, never()).withConnectTimeout(any<Int>(), any<TimeUnit>())
        verify(chain, never()).withReadTimeout(any<Int>(), any<TimeUnit>())
        verify(chain, never()).withWriteTimeout(any<Int>(), any<TimeUnit>())
        assertEquals(200, result.code)
    }

    @Test
    fun `request with RequestTimeout annotation applies specified timeouts`() {
        val method = StubApi::class.java.getDeclaredMethod("annotatedEndpoint", String::class.java)
        val invocation = Invocation.of(method, listOf("test"))
        val request =
            Request
                .Builder()
                .url("https://example.com/api")
                .tag(Invocation::class.java, invocation)
                .build()
        val chain = buildChain(request)

        interceptor.intercept(chain)

        verify(chain).withConnectTimeout(5_000, TimeUnit.MILLISECONDS)
        verify(chain).withReadTimeout(10_000, TimeUnit.MILLISECONDS)
        verify(chain).withWriteTimeout(15_000, TimeUnit.MILLISECONDS)
        verify(chain).proceed(request)
    }

    @Test
    fun `negative and zero timeout values do not crash and overflow is clamped`() {
        val method = StubApi::class.java.getDeclaredMethod("edgeCaseTimeouts")
        val invocation = Invocation.of(method, emptyList<Any>())
        val request =
            Request
                .Builder()
                .url("https://example.com/api")
                .tag(Invocation::class.java, invocation)
                .build()
        val chain = buildChain(request)

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        verify(chain).withConnectTimeout(-1, TimeUnit.MILLISECONDS)
        verify(chain).withReadTimeout(0, TimeUnit.MILLISECONDS)
        verify(chain).withWriteTimeout(Int.MAX_VALUE, TimeUnit.MILLISECONDS)
    }
}
