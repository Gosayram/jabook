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

package com.jabook.app.jabook.compose.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R

@Composable
public fun ResumeAfterLongPauseSheet(
    chapterName: String,
    chapterPosition: String,
    daysAgo: Int,
    onContinue: () -> Unit,
    onRestartChapter: () -> Unit,
    onSelectChapter: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.resumeLongPauseTitle),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = stringResource(R.string.resumeLongPauseDescription, chapterName, chapterPosition, daysAgo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.resumeLongPauseContinue))
        }

        TextButton(
            onClick = onRestartChapter,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.resumeLongPauseRestartChapter))
        }

        TextButton(
            onClick = onSelectChapter,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.resumeLongPauseSelectChapter))
        }
    }
}
