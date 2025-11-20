# Session Management System

Centralized session and cookie management system for JaBook application.

## Overview

The session management system provides:
- Automatic session saving and restoration
- Session validation before requests
- Cookie synchronization between WebView and Dio
- Authentication error handling
- Session expiration monitoring and notifications

## Core Components

### SessionManager
Central class for session management.

```dart
final sessionManager = SessionManager();

// Check session validity
final isValid = await sessionManager.isSessionValid();

// Save session after authentication
await sessionManager.saveSessionCookies(cookies, endpoint);

// Restore session on app startup
final restored = await sessionManager.restoreSession();

// Clear session on logout
await sessionManager.clearSession();

// Start session monitoring
sessionManager.startSessionMonitoring();
```

### SessionStorage
Secure storage for session data.

```dart
final storage = const SessionStorage();

// Save cookies
await storage.saveCookies(cookies, endpoint);

// Load cookies
final cookies = await storage.loadCookies();

// Get session ID
final sessionId = await storage.getSessionId();
```

### SessionValidator
Validation of session cookies.

```dart
final validator = const SessionValidator();

// Check if cookies exist
final hasCookies = await validator.hasCookies(cookieJar, uri);

// Validate cookies via test request
final isValid = await validator.validateCookies(dio, cookieJar);
```

### CookieSyncService
Cookie synchronization between WebView and Dio.

```dart
final syncService = const CookieSyncService();

// Sync from Dio to WebView
await syncService.syncFromDioToWebView(cookieJar);

// Sync from WebView to Dio
await syncService.syncFromWebViewToDio(cookieJar);

// Bidirectional synchronization
await syncService.syncBothWays(cookieJar);
```

### SessionInterceptor
Dio interceptor for automatic session checking.

```dart
// Automatically added to DioClient
// Checks session validity before each request
// Automatically refreshes session on expiration
```

### AuthErrorHandler
Centralized authentication error handling.

```dart
// Show error dialog
await AuthErrorHandler.handleAuthError(
  context,
  error,
  onRetry: () async {
    // Retry action
  },
);

// Show error SnackBar
AuthErrorHandler.showAuthErrorSnackBar(context, error);

// Determine error type
final errorType = AuthErrorHandler.getErrorType(error);
```

## Usage

### Initialization on App Startup

```dart
// In app.dart
final sessionManager = SessionManager();
final restored = await sessionManager.restoreSession();
if (restored) {
  final isValid = await sessionManager.isSessionValid();
  if (isValid) {
    sessionManager.startSessionMonitoring();
  }
}
```

### Saving Session After Authentication

```dart
// In rutracker_auth.dart after successful login
await sessionManager.saveSessionCookies(cookies, endpoint);
sessionManager.startSessionMonitoring();
```

### Error Handling in UI

```dart
// In screens when handling errors
try {
  // Execute request
} on DioException catch (e) {
  if (e.error is AuthFailure || e.response?.statusCode == 401) {
    AuthErrorHandler.showAuthErrorSnackBar(context, e);
  }
}
```

## Session Monitoring

The system automatically monitors sessions with adaptive intervals:
- New sessions (< 1 hour): check every 10 minutes
- Medium sessions (1-20 hours): check every 5 minutes
- Old sessions (> 20 hours): check every 2 minutes

When session expiration approaches (less than 4 hours), the user receives a system notification.

## Performance Metrics

```dart
final metrics = sessionManager.getPerformanceMetrics();
// Returns metrics for each operation:
// - count: number of executions
// - avgMs: average time in milliseconds
// - minMs: minimum time
// - maxMs: maximum time
// - p50Ms, p95Ms, p99Ms: percentiles
```

## Session Information

```dart
final sessionInfo = await sessionManager.getSessionInfo();
// Returns:
// - session_id: unique session identifier
// - endpoint: active endpoint
// - created_at: creation time
// - cookie_count: number of cookies
```

## Cache Clearing on Session Change

When clearing a session, the cache bound to that session is automatically cleared:

```dart
await sessionManager.clearSession();
// Automatically calls:
// - cacheService.clearCurrentSessionCache()
```

## Testing

All components have unit tests:
- `test/unit/session_manager_test.dart`
- `test/unit/session_storage_test.dart`
- `test/unit/auth_error_handler_test.dart`

## Security

- All session data is stored in `FlutterSecureStorage`
- Cookies are encrypted before saving
- Session ID is generated based on hash of cookies and endpoint
- Automatic cleanup on app exit

## Logging

All operations are logged through `StructuredLogger` with:
- Operation ID for tracking
- Execution duration
- Operation context
- Additional metadata
