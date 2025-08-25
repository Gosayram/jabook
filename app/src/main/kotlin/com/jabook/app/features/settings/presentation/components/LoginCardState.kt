package com.jabook.app.features.settings.presentation.components

data class LoginCardState(
  val username: String = "",
  val password: String = "",
  val isAuthorized: Boolean = false,
  val isLoading: Boolean = false,
  val usernameError: String? = null,
  val passwordError: String? = null,
  val generalError: String? = null,
) {
  val canSubmit: Boolean
    get() = username.isNotBlank() && password.isNotBlank() && !isLoading
}
