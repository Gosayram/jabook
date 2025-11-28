# Dependency Injection Providers

This directory contains all Riverpod providers for dependency injection.

## Structure

Providers are organized by domain:
- `auth_providers.dart` - Authentication-related providers
- `database_providers.dart` - Database providers
- `network_providers.dart` - Network-related providers
- `player_providers.dart` - Audio player providers
- `torrent_providers.dart` - Torrent-related providers

## Principles

1. **All dependencies through providers**: No singleton classes (except where necessary)
2. **Explicit dependencies**: All dependencies are declared in provider constructors
3. **Testability**: Easy to override providers for testing
4. **Lifecycle management**: Automatic cleanup when providers are no longer used

## Usage

```dart
// In your widget or service
final repository = ref.watch(authRepositoryProvider);
final isLoggedIn = await repository.isLoggedIn();
```

## Migration from Singletons

When migrating singleton classes to providers:

1. Create a provider that instantiates the class
2. Update all usages to use the provider instead of the singleton
3. Remove the singleton pattern from the class
4. Add the provider to the appropriate file in this directory

