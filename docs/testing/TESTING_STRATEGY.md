# Testing Strategy

This document outlines the comprehensive testing strategy for JaBook, following practices from Now In Android.

## Testing Pyramid

```
        /\
       /  \
      / E2E \          (Few, critical user journeys)
     /--------\
    /          \
   / Integration \     (Component interactions)
  /--------------\
 /                \
/   Unit Tests     \   (Many, isolated components)
/------------------\
```

## Test Types

### 1. Unit Tests

**Purpose**: Test individual components in isolation

**Coverage**:
- ✅ Use cases (business logic)
- ✅ Repository implementations
- ✅ Data sources
- ✅ Mappers
- ✅ Utility functions

**Location**: `test/core/domain/`, `test/core/data/`

**Example**:
```dart
test('LoginUseCase should call repository login', () async {
  final mockRepo = MockAuthRepository();
  final useCase = LoginUseCase(mockRepo);
  
  await useCase('user', 'pass');
  
  verify(mockRepo.login('user', 'pass')).called(1);
});
```

### 2. Integration Tests

**Purpose**: Test interactions between components

**Coverage**:
- ✅ Critical user flows (auth, download, playback)
- ✅ Repository + DataSource interactions
- ✅ UseCase + Repository interactions

**Location**: `test/integration/flows/`

**Example**:
```dart
test('complete auth flow', () async {
  final repo = TestAuthRepository();
  final loginUseCase = LoginUseCase(repo);
  final logoutUseCase = LogoutUseCase(repo);
  
  await loginUseCase('user', 'pass');
  expect(await repo.isLoggedIn(), isTrue);
  
  await logoutUseCase();
  expect(await repo.isLoggedIn(), isFalse);
});
```

### 3. Widget Tests

**Purpose**: Test UI components and user interactions

**Coverage**:
- ✅ All screens
- ✅ Reusable widgets
- ✅ User interactions
- ✅ State management integration

**Location**: `test/widget/features/`

**Example**:
```dart
testWidgets('login screen displays form', (tester) async {
  await tester.pumpWidget(
    ProviderScope(child: MaterialApp(home: LoginScreen())),
  );
  
  expect(find.text('Username'), findsOneWidget);
  expect(find.byType(TextField), findsNWidgets(2));
});
```

### 4. Screenshot Tests (Golden Tests)

**Purpose**: Ensure UI consistency across changes

**Coverage**:
- ✅ Critical screens
- ✅ Different screen sizes
- ✅ Different themes

**Location**: `test/widget/golden/`

**Example**:
```dart
testWidgets('login screen matches golden', (tester) async {
  await tester.pumpWidget(LoginScreen());
  
  await expectLater(
    find.byType(LoginScreen),
    matchesGoldenFile('login_screen.png'),
  );
});
```

## Test Doubles

Following Now In Android practices, we use **Test Doubles** instead of mocks:

### Test Implementations

Real implementations with test hooks:

```dart
class TestAuthRepository implements AuthRepository {
  bool _isLoggedIn = false;
  
  // Test hook
  void setLoggedIn(bool value) => _isLoggedIn = value;
  
  @override
  Future<bool> isLoggedIn() async => _isLoggedIn;
}
```

**Location**: `test/core/domain/*/test_doubles/`

### Benefits

- ✅ Test production code paths
- ✅ Less brittle than mocks
- ✅ Easier to maintain
- ✅ Better coverage

## Coverage Goals

- **Unit Tests**: > 80% coverage
- **Integration Tests**: All critical flows
- **Widget Tests**: All screens
- **Screenshot Tests**: Critical screens

## Running Tests

```bash
# Run all tests
flutter test

# Run with coverage
flutter test --coverage

# Run specific test file
flutter test test/core/domain/auth/use_cases/login_use_case_test.dart

# Run golden tests
flutter test --update-goldens
```

## Continuous Integration

Tests should run on:
- ✅ Every pull request
- ✅ Before merging to main
- ✅ Nightly builds

## Best Practices

1. **AAA Pattern**: Arrange-Act-Assert
2. **Test Isolation**: Each test is independent
3. **Descriptive Names**: Test names describe what they test
4. **Test Doubles**: Use test implementations, not mocks
5. **Golden Tests**: For UI consistency
6. **Accessibility**: Test that widgets are accessible

## Resources

- [Flutter Testing Guide](https://docs.flutter.dev/testing)
- [Now In Android Testing](https://github.com/android/nowinandroid/tree/main/core/testing)
- [Test Doubles Pattern](https://martinfowler.com/bliki/TestDouble.html)

