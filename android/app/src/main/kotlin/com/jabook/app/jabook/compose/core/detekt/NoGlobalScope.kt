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

package com.jabook.app.jabook.compose.core.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * Detekt rule that forbids use of `GlobalScope` anywhere in the project.
 *
 * ## Problem
 * `GlobalScope` launches coroutines that are not bound to any lifecycle.
 * They live until the process dies, cannot be cancelled, and cause:
 * - Memory leaks (captured references stay alive forever)
 * - Resource leaks (network/IO connections are never released)
 * - Unpredictable behaviour after configuration changes or screen rotation
 *
 * ## Correct alternatives
 * | Context          | Correct scope                           |
 * |------------------|-----------------------------------------|
 * | ViewModel        | `viewModelScope`                        |
 * | Activity/Fragment| `lifecycleScope`                        |
 * | Service          | `serviceScope` (a `CoroutineScope` tied to `onDestroy`) |
 * | Repository/UseCase| `coroutineScope { }` or caller's scope |
 *
 * ```kotlin
 * // ❌ GlobalScope — never use
 * GlobalScope.launch { doWork() }
 *
 * // ✅ viewModelScope — auto-cancelled on ViewModel.onCleared()
 * viewModelScope.launch { doWork() }
 *
 * // ✅ Service scope tied to lifecycle
 * private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
 * override fun onDestroy() { serviceScope.cancel() }
 * serviceScope.launch { doWork() }
 * ```
 */
public class NoGlobalScope(
    config: Config,
) : Rule(
        config,
        "GlobalScope is forbidden — use a lifecycle-aware scope (viewModelScope, lifecycleScope, or a custom serviceScope).",
    ) {
    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (expression.getReferencedName() == "GlobalScope") {
            report(
                Finding(
                    entity = Entity.from(expression),
                    message =
                        "GlobalScope is forbidden. Use viewModelScope in ViewModels, " +
                            "lifecycleScope in Activities/Fragments, or a CoroutineScope " +
                            "tied to the component lifecycle in Services.",
                ),
            )
        }
        super.visitSimpleNameExpression(expression)
    }
}
