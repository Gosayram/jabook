# Widget Testing Guide

This document describes the widget testing strategy for JaBook, following best practices from Flutter and Now In Android.

## Principles

1. **Test User Interactions**: Test what users see and do, not implementation details
2. **Golden Tests**: Use screenshot/golden tests for UI consistency
3. **Accessibility**: Test that widgets are accessible
4. **State Management**: Test widgets with Riverpod providers

## Structure

```
test/
├── widget/
│   ├── features/
│   │   ├── auth/
│   │   ├── search/
│   │   └── downloads/
│   └── shared/
│       └── widgets/
```

## Example Widget Test

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/features/auth/presentation/screens/login_screen.dart';

void main() {
  group('LoginScreen', () {
    testWidgets('should display login form', (WidgetTester tester) async {
      // Arrange
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: LoginScreen(),
          ),
        ),
      );

      // Assert
      expect(find.text('Username'), findsOneWidget);
      expect(find.text('Password'), findsOneWidget);
      expect(find.byType(TextField), findsNWidgets(2));
    });

    testWidgets('should show error on invalid login', (WidgetTester tester) async {
      // Arrange
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: LoginScreen(),
          ),
        ),
      );

      // Act
      await tester.enterText(find.byType(TextField).first, 'invalid');
      await tester.tap(find.text('Login'));
      await tester.pumpAndSettle();

      // Assert
      expect(find.text('Invalid credentials'), findsOneWidget);
    });
  });
}
```

## Testing with Riverpod

When testing widgets that use Riverpod providers, override providers with test values:

```dart
testWidgets('should display user data', (WidgetTester tester) async {
  // Arrange
  final container = ProviderContainer(
    overrides: [
      authRepositoryProvider.overrideWithValue(
        TestAuthRepository()..setLoggedIn(true),
      ),
    ),
  );

  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: const MaterialApp(home: UserProfileScreen()),
    ),
  );

  // Assert
  expect(find.text('Logged in'), findsOneWidget);
});
```

## Golden Tests (Screenshot Tests)

For UI consistency, use golden tests:

```dart
testWidgets('login screen matches golden', (WidgetTester tester) async {
  await tester.pumpWidget(
    const ProviderScope(
      child: MaterialApp(home: LoginScreen()),
    ),
  );

  await expectLater(
    find.byType(LoginScreen),
    matchesGoldenFile('login_screen.png'),
  );
});
```

## Best Practices

1. **Use `pumpAndSettle()`**: Wait for all animations to complete
2. **Test User Flows**: Test complete user interactions, not just individual widgets
3. **Override Providers**: Use test doubles for providers in tests
4. **Accessibility**: Test that widgets are accessible with `tester.getSemantics()`
5. **Responsive**: Test on different screen sizes

## Checklist

- [ ] All screens have widget tests
- [ ] Critical user flows are tested
- [ ] Golden tests for UI consistency
- [ ] Accessibility tests
- [ ] Responsive design tests

