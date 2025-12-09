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
import androidx.compose.ui.tooling.preview.Preview
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
                text = "Jetpack Compose работает! 🎉",
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
