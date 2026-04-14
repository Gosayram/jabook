/**
 * Copyright (c) 2025 JaBook Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jabook.app.jabook.compose.core.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

/**
 * Detekt rule to detect catch blocks that silently swallow CancellationException.
 *
 * ## Problem
 * Catching `Exception` or `Throwable` without rethrowing `CancellationException`
 * breaks Kotlin's structured concurrency and can cause:
 * - Resource leaks
 * - Deadlocks
 * - Incomplete cleanup
 * - Unexpected behavior
 *
 * ## Correct patterns
 * ```kotlin
 * // Pattern 1: Explicit rethrow
 * try {
 *     operation()
 * } catch (e: Exception) {
 *     if (e is CancellationException) throw e
 *     logger.e(e) { "Failed" }
 * }
 *
 * // Pattern 2: Using helper
 * try {
 *     operation()
 * } catch (e: Exception) {
 *     e.rethrowCancellation()
 *     logger.e(e) { "Failed" }
 * }
 *
 * // Pattern 3: Using runCatchingCancelable
 * runCatchingCancelable { operation() }
 *     .onFailure { logger.e(it) { "Failed" } }
 * ```
 *
 * ## Incorrect patterns (will be flagged)
 * ```kotlin
 * // ❌ Missing rethrow
 * try {
 *     operation()
 * } catch (e: Exception) {
 *     logger.e(e) { "Failed" }
 * }
 *
 * // ❌ Missing rethrow for Throwable
 * try {
 *     operation()
 * } catch (e: Throwable) {
 *     logger.e(e) { "Failed" }
 * }
 * ```
 */
public class NoSilentCancellationException(
    config: Config,
) : Rule(
        config,
        "Catch block for Exception/Throwable must rethrow CancellationException to preserve structured concurrency",
    ) {
    override fun visitCatchSection(catchClause: KtCatchClause) {
        val catchType = catchClause.catchParameter?.typeReference?.text ?: return

        // Only check catch blocks for Exception or Throwable
        if (!shouldCheckCatchType(catchType)) return

        val catchBody = catchClause.catchBody as? KtBlockExpression ?: return

        // Check if the catch body properly handles CancellationException
        if (!hasProperCancellationHandling(catchBody)) {
            report(
                Finding(
                    entity = Entity.from(catchClause),
                    message =
                        "Catch block for '$catchType' does not rethrow CancellationException. " +
                            "Add `if (e is CancellationException) throw e` or use `e.rethrowCancellation()` " +
                            "at the start of the catch block, or use `runCatchingCancelable { }` instead.",
                ),
            )
        }

        super.visitCatchSection(catchClause)
    }

    private fun shouldCheckCatchType(catchType: String): Boolean {
        val normalizedType = catchType.trim()
        return normalizedType == "Exception" ||
            normalizedType == "Throwable" ||
            normalizedType.endsWith(".Exception") ||
            normalizedType.endsWith(".Throwable")
    }

    private fun hasProperCancellationHandling(body: KtBlockExpression): Boolean {
        // Check for: if (e is CancellationException) throw e
        if (hasIfRethrowPattern(body)) return true

        // Check for: e.rethrowCancellation() or ExceptionSafety.rethrowIfCancellation(e)
        if (hasRethrowHelperPattern(body)) return true

        return false
    }

    private fun hasIfRethrowPattern(body: KtBlockExpression): Boolean {
        return body.anyDescendantOfType<KtIfExpression> { ifExpr ->
            val condition = ifExpr.condition as? KtIsExpression ?: return@anyDescendantOfType false
            if (!isCancellationCheck(condition)) return@anyDescendantOfType false

            val thenBlock = ifExpr.then
            // Check if then block contains a throw
            if (thenBlock is KtThrowExpression) return@anyDescendantOfType true
            return@anyDescendantOfType thenBlock?.anyDescendantOfType<KtThrowExpression>() == true
        }
    }

    private fun hasRethrowHelperPattern(body: KtBlockExpression): Boolean {
        return body.anyDescendantOfType<KtDotQualifiedExpression> { expr ->
            val selectorText = expr.selectorExpression?.text ?: return@anyDescendantOfType false
            // Check for: e.rethrowCancellation()
            selectorText == "rethrowCancellation()" ||
                // Check for: ExceptionSafety.rethrowIfCancellation(e)
                selectorText.startsWith("rethrowIfCancellation(")
        }
    }

    private fun isCancellationCheck(expression: KtIsExpression): Boolean {
        val typeText = expression.typeReference?.text ?: return false
        return typeText == "CancellationException" ||
            typeText == "kotlinx.coroutines.CancellationException"
    }
}
