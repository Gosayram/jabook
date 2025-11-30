# Downloads Feature

This feature provides a user interface for managing and monitoring torrent downloads.

## Structure

This feature follows Clean Architecture principles:

```
features/downloads/
├── data/
│   └── providers/
│       └── downloads_providers.dart    # Feature-specific providers for use cases
├── presentation/
│   ├── providers/                       # Presentation state providers (to be added)
│   ├── screens/
│   │   └── downloads_screen.dart       # Main downloads screen
│   └── widgets/
│       └── download_status_bar.dart    # Download status widget
└── README.md
```

## Architecture

### Domain Layer
The domain layer is located in `core/domain/torrent/` and includes:
- **Entities**: `TorrentTask`, `TorrentProgress`
- **Repository Interface**: `TorrentRepository`
- **Use Cases**: 
  - `GetActiveTasksUseCase` - Get all active download tasks
  - `PauseTorrentUseCase` - Pause a download
  - `ResumeTorrentUseCase` - Resume a paused download
  - `CancelTorrentUseCase` - Cancel a download
  - `DownloadTorrentUseCase` - Start a new download

### Data Layer
The data layer is located in `core/data/torrent/` and includes:
- **Data Sources**: `TorrentLocalDataSource`
- **Repository Implementation**: `TorrentRepositoryImpl`
- **Mappers**: `TorrentMapper`

### Presentation Layer
The presentation layer is in `features/downloads/presentation/` and includes:
- **Screens**: Downloads UI screen
- **Widgets**: Reusable UI components
- **Providers**: Feature-specific state management (to be migrated)

## Usage

### Using Get Active Tasks Use Case

```dart
final useCase = ref.watch(getActiveTasksUseCaseProvider);
final tasks = await useCase();
// Handle tasks
```

### Using Pause Torrent Use Case

```dart
final useCase = ref.watch(pauseTorrentUseCaseProvider);
await useCase(taskId);
```

### Using Resume Torrent Use Case

```dart
final useCase = ref.watch(resumeTorrentUseCaseProvider);
await useCase(taskId);
```

### Using Cancel Torrent Use Case

```dart
final useCase = ref.watch(cancelTorrentUseCaseProvider);
await useCase(taskId, deleteFiles: false);
```

## Migration Status

- ✅ Domain layer exists in `core/domain/torrent/`
- ✅ Data layer exists in `core/data/torrent/`
- ✅ Use cases created for downloads operations
- ✅ Feature-specific providers created in `features/downloads/data/providers/`
- ⏳ Presentation layer migration in progress
  - Downloads screen still uses direct `AudiobookTorrentManager` calls
  - Needs migration to use cases and providers

## Next Steps

1. Create presentation state providers for downloads screen
2. Migrate `downloads_screen.dart` to use use cases instead of direct manager calls
3. Add proper error handling using domain entities
4. Implement proper state management with Riverpod
5. Update to use `TorrentTask` entities instead of `Map<String, dynamic>`

