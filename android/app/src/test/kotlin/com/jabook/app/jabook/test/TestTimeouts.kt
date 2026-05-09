package com.jabook.app.jabook.test

import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

public object TestTimeouts {
    public const val DEFAULT_COROUTINE_TIMEOUT_MS: Long = 10_000L
}

public suspend inline fun <T> withTestTimeout(
    timeoutMs: Long = TestTimeouts.DEFAULT_COROUTINE_TIMEOUT_MS,
    crossinline block: suspend () -> T,
): T = withTimeout(timeoutMs) { block() }

public suspend inline fun <T> withOptionalTestTimeout(
    timeoutMs: Long = TestTimeouts.DEFAULT_COROUTINE_TIMEOUT_MS,
    crossinline block: suspend () -> T,
): T? = withTimeoutOrNull(timeoutMs) { block() }
