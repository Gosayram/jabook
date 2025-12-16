// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.jabook.app.jabook.R
import com.jabook.app.jabook.ui.theme.JabookTheme

/**
 * Test screen to verify Compose setup.
 * This is a temporary screen to validate that Jetpack Compose is working correctly.
 *
 * TODO: Replace with actual app screens (Library, Player, etc.)
 */
@Composable
fun TestComposeScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.jetpackComposeWorks),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(
    name = "Light Theme",
    showBackground = true,
)
@Composable
private fun TestComposeScreenPreview() {
    JabookTheme(darkTheme = false) {
        TestComposeScreen()
    }
}

@Preview(
    name = "Dark Theme",
    showBackground = true,
)
@Composable
private fun TestComposeScreenDarkPreview() {
    JabookTheme(darkTheme = true) {
        TestComposeScreen()
    }
}
