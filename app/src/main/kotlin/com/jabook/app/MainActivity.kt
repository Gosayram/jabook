package com.jabook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.jabook.app.shared.ui.navigation.JaBookNavigation
import com.jabook.app.shared.ui.theme.JaBookTheme
import dagger.hilt.android.AndroidEntryPoint

/** Main activity for JaBook application. Entry point for the user interface using Jetpack Compose. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent { JaBookTheme { JaBookApp() } }
    }
}

@Composable
fun JaBookApp() {
    val navController = rememberNavController()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold { paddingValues -> JaBookNavigation(navController = navController, modifier = Modifier.padding(paddingValues)) }
    }
}

@Preview(showBackground = true)
@Composable
fun JaBookAppPreview() {
    JaBookTheme { JaBookApp() }
}
