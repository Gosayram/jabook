package com.jabook.app.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Component for displaying different application states Supports empty states, loading, and errors */
@Composable
fun JaBookEmptyState(
    state: EmptyStateType,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    actionButton: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // State icon
        when (state) {
            EmptyStateType.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
            }
            else -> {
                Icon(
                    imageVector = state.icon,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = title ?: state.defaultTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = subtitle ?: state.defaultSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        // Action button
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(32.dp))
            actionButton()
        }
    }
}

/** Types of empty states with preset icons and texts */
enum class EmptyStateType(val icon: ImageVector, val defaultTitle: String, val defaultSubtitle: String) {
    Loading(
        icon = Icons.Default.LibraryBooks, // Not used for Loading
        defaultTitle = "Загрузка...",
        defaultSubtitle = "Пожалуйста, подождите",
    ),
    EmptyLibrary(
        icon = Icons.Default.LibraryBooks,
        defaultTitle = "Библиотека пуста",
        defaultSubtitle = "Добавьте свою первую аудиокнигу, чтобы начать слушать",
    ),
    EmptySearch(
        icon = Icons.Default.SearchOff,
        defaultTitle = "Ничего не найдено",
        defaultSubtitle = "Попробуйте изменить запрос или воспользуйтесь другими ключевыми словами",
    ),
    NetworkError(
        icon = Icons.Default.CloudOff,
        defaultTitle = "Нет подключения",
        defaultSubtitle = "Проверьте интернет-соединение и попробуйте снова",
    ),
    GeneralError(
        icon = Icons.Default.Error,
        defaultTitle = "Произошла ошибка",
        defaultSubtitle = "Что-то пошло не так. Попробуйте перезагрузить страницу",
    ),
    EmptyDownloads(
        icon = Icons.Default.LibraryBooks,
        defaultTitle = "Нет загрузок",
        defaultSubtitle = "Здесь будут отображаться ваши загруженные аудиокниги",
    ),
    EmptyCategory(
        icon = Icons.Default.LibraryBooks,
        defaultTitle = "Категория пуста",
        defaultSubtitle = "В этой категории пока нет аудиокниг",
    ),
}

/** Component for displaying loading with custom text */
@Composable
fun JaBookLoadingState(message: String = "Загрузка...", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Component for displaying error with retry button */
@Composable
fun JaBookErrorState(
    title: String = "Произошла ошибка",
    subtitle: String = "Что-то пошло не так. Попробуйте еще раз",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    JaBookEmptyState(
        state = EmptyStateType.GeneralError,
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        actionButton =
            if (onRetry != null) {
                { JaBookButton(text = "Попробовать снова", onClick = onRetry, variant = ButtonVariant.Primary) }
            } else {
                null
            },
    )
}
