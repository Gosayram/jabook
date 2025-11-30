# Offline-First Architecture

This document describes the offline-first architecture approach for JaBook, following practices from Now In Android.

## Principles

1. **Local Storage as Source of Truth**: Data is always read from local storage first
2. **Streams Instead of Snapshots**: Repositories provide streams of data, not snapshots
3. **Background Synchronization**: Remote data is synced in the background
4. **No Errors on Read**: Reading from local storage should never fail
5. **Exponential Backoff**: Failed syncs use exponential backoff

## Current State

Most repositories currently return `Future<List<T>>` (snapshots). For offline-first architecture, they should return `Stream<List<T>>` (streams).

### Repositories with Streams (Already Implemented)

- ✅ `PlayerRepository.stateStream` - Returns `Stream<PlayerState>`
- ✅ `TorrentRepository.getProgressStream()` - Returns `Stream<TorrentProgress>`
- ✅ `AuthRepository.authStatus` - Returns `Stream<AuthStatus>`

### Repositories Needing Migration

- ⏳ `LibraryRepository` - Should return `Stream<List<LocalAudiobookGroup>>`
- ⏳ `SearchRepository` - Should return `Stream<List<SearchResult>>` for cached results
- ⏳ `TorrentRepository.getActiveTasks()` - Could return `Stream<List<TorrentTask>>`

## Migration Strategy

### Step 1: Add Stream Methods to Repository Interfaces

```dart
abstract class LibraryRepository {
  // Existing snapshot methods (keep for backward compatibility)
  Future<List<LocalAudiobookGroup>> scanAllLibraryFolders();
  
  // New stream methods (offline-first)
  Stream<List<LocalAudiobookGroup>> watchLibraryGroups();
}
```

### Step 2: Implement Stream Methods in Repository Implementations

```dart
class LibraryRepositoryImpl implements LibraryRepository {
  @override
  Stream<List<LocalAudiobookGroup>> watchLibraryGroups() {
    // Read from local database/cache
    return _localDataSource.watchLibraryGroups().map((entities) =>
      entities.map((e) => e.toDomain()).toList()
    );
  }
}
```

### Step 3: Update Use Cases to Use Streams

```dart
class GetLibraryGroupsUseCase {
  Stream<List<LocalAudiobookGroup>> call() {
    return _repository.watchLibraryGroups();
  }
}
```

### Step 4: Update Presentation Layer

```dart
class LibraryScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final groupsStream = ref.watch(getLibraryGroupsUseCaseProvider);
    
    return StreamBuilder<List<LocalAudiobookGroup>>(
      stream: groupsStream,
      builder: (context, snapshot) {
        if (snapshot.hasData) {
          return LibraryList(groups: snapshot.data!);
        }
        return const CircularProgressIndicator();
      },
    );
  }
}
```

## Background Synchronization

Remote data should be synced in the background:

```dart
class LibrarySyncService {
  Future<void> syncLibrary() async {
    try {
      // Fetch from remote
      final remoteGroups = await _remoteDataSource.getLibraryGroups();
      
      // Update local storage
      await _localDataSource.saveLibraryGroups(remoteGroups);
    } catch (e) {
      // Use exponential backoff
      await _scheduleRetry();
    }
  }
}
```

## Benefits

1. **Always Available**: Data is always available from local storage
2. **Reactive Updates**: UI automatically updates when data changes
3. **Better UX**: No loading states for cached data
4. **Offline Support**: App works without internet connection
5. **Performance**: Faster access to cached data

## Implementation Checklist

- [ ] Add stream methods to repository interfaces
- [ ] Implement stream methods in repository implementations
- [ ] Update use cases to use streams
- [ ] Update presentation layer to use StreamBuilder
- [ ] Implement background synchronization
- [ ] Add exponential backoff for failed syncs
- [ ] Update tests to use test doubles
- [ ] Document migration process

## Examples

See `test/core/domain/*/test_doubles/` for test implementations that demonstrate the pattern.

