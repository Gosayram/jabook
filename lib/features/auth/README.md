# Authentication Feature

This module handles RuTracker authentication and connection management.

## Overview

The authentication feature provides:
- Login/logout functionality for RuTracker
- Connection status monitoring
- Secure credential storage
- Integration with the main navigation

## Architecture

### Domain Layer
- `AuthStatus`: Enum representing authentication states
- `AuthRepository`: Interface for authentication operations

### Data Layer
- `AuthRepositoryImpl`: Implementation using RuTrackerAuth
- Providers for Riverpod state management

### Presentation Layer
- `AuthScreen`: Main authentication UI
- `AuthStatusIndicator`: Navigation badge widget

## Usage

### Checking Authentication Status
```dart
final isLoggedIn = ref.read(isLoggedInProvider);
final authStatus = ref.watch(authStatusProvider);
```

### Logging In
```dart
final repository = ref.read(authRepositoryProvider);
final success = await repository.login(username, password);
```

### Logging Out
```dart
final repository = ref.read(authRepositoryProvider);
await repository.logout();
```

## Integration

The authentication feature is integrated into:
- Main navigation (Connect tab)
- Status indicators throughout the app
- Automatic credential management

## Security

- Credentials are stored securely using Flutter Secure Storage
- Biometric authentication support
- Automatic logout on app termination
- Secure cookie management

## Localization

All UI elements support both English and Russian localization through ARB files.

## Dependencies

- `webview_flutter`: For WebView-based login
- `flutter_secure_storage`: For secure credential storage
- `local_auth`: For biometric authentication
- `dio`: For HTTP requests with cookie management