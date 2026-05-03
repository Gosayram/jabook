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

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.internal.DefaultRuleSetProvider

/**
 * RuleSet provider for JaBook custom detekt rules.
 *
 * This provider registers project-specific rules that enforce
 * best practices for audio book player development.
 */
public class JabookRulesProvider : DefaultRuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("jabook")

    override fun instance(): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                ::NoSilentCancellationException,
            ),
        )
}
