# Testing Guide

This document describes the testing structure and guidelines for the JaBook project.

## Test Structure

Tests are organized following the Clean Architecture structure:

```
test/
├── core/
│   ├── domain/
│   │   └── {domain}/
│   │       └── use_cases/
│   │           └── {use_case}_test.dart
│   └── data/
│       └── {domain}/
│           └── repositories/
│               └── {repository}_impl_test.dart
├── features/
│   └── {feature}/
│       └── presentation/
│           └── screens/
│               └── {screen}_test.dart
├── integration/
│   └── {flow}_test.dart
└── unit/
    └── {service}_test.dart
```

## Test Types

### Unit Tests

Unit tests verify individual components in isolation:
- **Use Cases**: Test business logic with mocked repositories
- **Repositories**: Test data layer with mocked data sources
- **Data Sources**: Test data access with mocked external dependencies
- **Services**: Test utility services and helpers

### Integration Tests

Integration tests verify interactions between components:
- **Critical Flows**: Test complete user flows (auth, download, playback)
- **Component Interactions**: Test how different layers work together
- **Database Operations**: Test real database operations with test database

### Widget Tests

Widget tests verify UI components:
- **Screens**: Test screen rendering and user interactions
- **Widgets**: Test reusable widget components
- **State Management**: Test Riverpod providers and state changes

## Running Tests

```bash
# Run all tests
make test
# or
flutter test

# Run unit tests only
make test-unit

# Run widget tests
make test-widget

# Run integration tests
make test-integration

# Run with coverage
make test-coverage
```

## Test Coverage Goals

- **Target Coverage**: > 70%
- **Critical Components**: 100% coverage
  - Use cases
  - Repositories
  - Critical business logic
- **UI Components**: > 60% coverage
  - Screens
  - Widgets
  - State management

## Writing Tests

### Use Case Tests

```dart
test('should call repository with correct parameters', () async {
  // Arrange
  final mockRepository = MockRepository();
  final useCase = UseCase(mockRepository);
  
  // Act
  await useCase(param);
  
  // Assert
  verify(mockRepository.method(param)).called(1);
});
```

### Repository Tests

```dart
test('should return domain entities from data source', () async {
  // Arrange
  final mockDataSource = MockDataSource();
  final repository = RepositoryImpl(mockDataSource);
  when(mockDataSource.getData()).thenAnswer((_) async => dataModel);
  
  // Act
  final result = await repository.getData();
  
  // Assert
  expect(result, isA<DomainEntity>());
});
```

### Widget Tests

```dart
testWidgets('should display search results', (tester) async {
  // Arrange
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        searchRepositoryProvider.overrideWithValue(mockRepository),
      ],
      child: SearchScreen(),
    ),
  );
  
  // Act
  await tester.enterText(find.byType(TextField), 'query');
  await tester.tap(find.byType(IconButton));
  await tester.pumpAndSettle();
  
  // Assert
  expect(find.text('Result 1'), findsOneWidget);
});
```

## Test Doubles

Following Now In Android practices, we use test implementations instead of mocks:

### Test Repository Implementation

```dart
class TestAuthRepository implements AuthRepository {
  final List<AuthSession> _sessions = [];
  
  // Test hooks
  void addTestSession(AuthSession session) {
    _sessions.add(session);
  }
  
  @override
  Future<AuthSession?> getSession() async {
    return _sessions.lastOrNull;
  }
}
```

## Best Practices

1. **Isolation**: Each test should be independent
2. **Arrange-Act-Assert**: Follow AAA pattern
3. **Descriptive Names**: Test names should describe what they test
4. **One Assertion**: Each test should verify one thing
5. **Test Doubles**: Use test implementations instead of mocks when possible
6. **Coverage**: Aim for high coverage but focus on critical paths
7. **Maintainability**: Keep tests simple and readable

## Current Test Status

### Completed
- ✅ Unit tests for core services
- ✅ Integration tests for file operations
- ✅ Widget tests for library screen

### In Progress
- ⏳ Unit tests for use cases
- ⏳ Repository implementation tests
- ⏳ Data source tests

### Planned
- 📋 Integration tests for critical flows
- 📋 Widget tests for all screens
- 📋 Screenshot tests for UI

## Coverage Report

Generate coverage report:
```bash
make test-coverage
```

View coverage report:
```bash
open coverage/html/index.html
```

