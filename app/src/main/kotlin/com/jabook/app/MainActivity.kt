package com.jabook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.jabook.app.shared.ui.AppThemeMode
import com.jabook.app.shared.ui.ThemeViewModel
import com.jabook.app.shared.ui.navigation.JaBookNavigation
import com.jabook.app.shared.ui.theme.JaBookTheme
import dagger.hilt.android.AndroidEntryPoint

/** Main activity for JaBook application. Entry point for the user interface using Jetpack Compose. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode = themeViewModel.themeMode.collectAsState().value
            JaBookTheme(
                darkTheme = when (themeMode) {
                    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                    AppThemeMode.DARK -> true
                    AppThemeMode.LIGHT -> false
                }
            ) {
                JaBookApp(
                    themeViewModel = themeViewModel,
                    themeMode = themeMode
                )
            }
        }
    }
}

@Composable
fun JaBookApp(
    themeViewModel: ThemeViewModel,
    themeMode: AppThemeMode
) {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold { paddingValues ->
            JaBookNavigation(
                navController = navController,
                modifier = Modifier.padding(paddingValues),
                themeViewModel = themeViewModel,
                themeMode = themeMode
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun JaBookAppPreview() {
    val context = LocalContext.current
    val previewThemeViewModel = viewModel<ThemeViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application)
    )
    JaBookTheme {
        JaBookApp(
            themeViewModel = previewThemeViewModel,
            themeMode = AppThemeMode.SYSTEM
        )
    }
}
