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
import okhttp3.Response
import retrofit2.Invocation
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * OkHttp interceptor that reads @RequestTimeout annotations from Retrofit
 * interface methods and applies per-request timeouts.
 *
 * Without this, all requests share the same OkHttpClient timeouts.
 * With this, search endpoints can timeout quickly while torrent downloads
 * wait longer.
 *
 * Wire into OkHttpClient via addInterceptor() in NetworkModule.
 */
@Singleton
public class DynamicTimeoutInterceptor
    @Inject
    constructor() : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val timeout =
                chain
                    .request()
                    .tag(Invocation::class.java)
                    ?.method()
                    ?.getAnnotation(RequestTimeout::class.java)

            return if (timeout != null) {
                // Clamp timeout values to Int.MAX_VALUE to prevent overflow
                chain
                    .withConnectTimeout(min(timeout.connectMs, Int.MAX_VALUE.toLong()).toInt(), TimeUnit.MILLISECONDS)
                    .withReadTimeout(min(timeout.readMs, Int.MAX_VALUE.toLong()).toInt(), TimeUnit.MILLISECONDS)
                    .withWriteTimeout(min(timeout.writeMs, Int.MAX_VALUE.toLong()).toInt(), TimeUnit.MILLISECONDS)
                    .proceed(chain.request())
            } else {
                chain.proceed(chain.request())
            }
        }
    }
