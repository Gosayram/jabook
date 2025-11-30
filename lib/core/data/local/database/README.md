# Database Layer

This directory contains database-related services and classes for local data storage.

## Files

- **app_database.dart** - Main database class providing access to all database stores (Sembast)
- **cookie_database_service.dart** - Service for managing authentication cookies in the database

## Usage

Use `appDatabaseProvider` from `core/di/providers/database_providers.dart` to get an AppDatabase instance.

```dart
final appDatabase = ref.watch(appDatabaseProvider);
```

## Migration

This directory was created as part of the data layer unification. Previously:
- `AppDatabase` was in `lib/data/db/app_database.dart`
- `CookieDatabaseService` was in `lib/core/auth/cookie_database_service.dart`

