package com.jabook.app.features.settings.presentation.components

data class LoginCardState(
  val username: String = "",
  val password: String = "",
  val isAuthorized: Boolean = false,
  val isLoading: Boolean = false,
)
