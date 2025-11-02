# JavaScript Interop Guide

This project uses `dart:js_interop` (available from Dart 3.3+) for any JavaScript interop needs instead of the deprecated `package:js`.

## Current Status

- ✅ SDK version updated to `>=3.3.0` (supports `dart:js_interop`)
- ✅ No direct usage of `package:js` in our codebase
- ⚠️ `package:js` still appears as transitive dependency via `flutter_web_plugins` (Flutter SDK)
- ✅ When adding new JavaScript interop code, use `dart:js_interop`

## Usage Example

```dart
import 'dart:js_interop';

// Declare JavaScript functions/objects
@JS()
external JSObject get window;

// Extension types for type safety
extension type Navigator(JSObject _) implements JSObject {
  external String get userAgent;
}

// Usage
void example() {
  final navigator = window['navigator'.toJS] as Navigator;
  final userAgent = navigator.userAgent;
}
```

## Migration Notes

- The deprecated `package:js` is only present as a transitive dependency
- It will be automatically removed when Flutter SDK updates `flutter_web_plugins`
- No migration needed in our codebase (we don't use `package:js` directly)
- All WebView JavaScript interactions use WebView API (`runJavaScriptReturningResult`), not `package:js`

