# Search Feature

This feature provides search functionality for audiobooks on RuTracker.

## Structure

This feature follows Clean Architecture principles:

```
features/search/
├── data/
│   └── providers/
│       └── search_providers.dart    # Feature-specific providers for use cases
├── presentation/
│   ├── providers/                    # Presentation state providers (to be added)
│   ├── screens/
│   │   └── search_screen.dart       # Main search screen
│   └── widgets/
│       ├── audiobook_card.dart
│       ├── audiobook_card_skeleton.dart
│       ├── grouped_audiobook_list.dart
│       └── recommended_audiobooks_widget.dart
└── README.md
```

## Architecture

### Domain Layer
The domain layer is located in `core/domain/search/` and includes:
- **Entities**: `SearchQuery`, `SearchResult`
- **Repository Interface**: `SearchRepository`
- **Use Cases**: `SearchUseCase`, `GetSearchHistoryUseCase`

### Data Layer
The data layer is located in `core/data/search/` and includes:
- **Data Sources**: `SearchLocalDataSource`, `SearchRemoteDataSource`
- **Repository Implementation**: `SearchRepositoryImpl`
- **Mappers**: `SearchMapper`

### Presentation Layer
The presentation layer is in `features/search/presentation/` and includes:
- **Screens**: Search UI screens
- **Widgets**: Reusable UI components
- **Providers**: Feature-specific state management (to be migrated)

## Usage

### Using Search Use Case

```dart
final searchUseCase = ref.watch(searchUseCaseProvider);
if (searchUseCase != null) {
  final results = await searchUseCase(query, offset: 0);
  // Handle results
}
```

### Using Search History Use Case

```dart
final historyUseCase = ref.watch(getSearchHistoryUseCaseProvider);
if (historyUseCase != null) {
  final history = await historyUseCase(limit: 10);
  // Handle history
}
```

## Migration Status

- ✅ Domain layer created in `core/domain/search/`
- ✅ Data layer created in `core/data/search/`
- ✅ Feature-specific providers created in `features/search/data/providers/`
- ⏳ Presentation layer migration in progress
  - Search screen still uses direct service calls
  - Needs migration to use cases and providers

## Next Steps

1. Create presentation state providers for search screen
2. Migrate `search_screen.dart` to use use cases instead of direct service calls
3. Add proper error handling using domain entities
4. Implement proper state management with Riverpod

