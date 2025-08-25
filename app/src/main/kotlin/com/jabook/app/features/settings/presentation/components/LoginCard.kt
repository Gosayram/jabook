package com.jabook.app.features.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jabook.app.R

@Composable
fun LoginCard(
  state: LoginCardState,
  onUsernameChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onLogin: () -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var passwordVisible by remember { mutableStateOf(false) }

  Card(
    modifier = modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (state.isAuthorized) stringResource(R.string.logged_in) else stringResource(R.string.login),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
      )

      // Общая ошибка (например, "Неверный пароль" или "Нет сети")
      state.generalError?.let { error ->
        Text(
          text = error,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (state.isAuthorized) {
        // Logout section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.logged_in_as, state.username),
            style = MaterialTheme.typography.bodyMedium,
          )

          Button(
            onClick = onLogout,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
          ) {
            if (state.isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
            }
            Text(stringResource(R.string.logout))
          }
        }
      } else {
        // Login form
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.username)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            isError = state.usernameError != null,
            supportingText = {
              state.usernameError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            },
          )

          OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = if (passwordVisible)
                    stringResource(R.string.hide_password)
                  else
                    stringResource(R.string.show_password),
                )
              }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            isError = state.passwordError != null,
            supportingText = {
              state.passwordError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            },
          )

          Button(
            onClick = onLogin,
            enabled = state.username.isNotBlank() && state.password.isNotBlank() && !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
          ) {
            if (state.isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
            }
            Text(stringResource(R.string.login))
          }
        }
      }
    }
  }
}
