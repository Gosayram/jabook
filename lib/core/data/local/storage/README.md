# Storage Layer

This directory contains storage-related services for local file and secure storage operations.

## Files

- **secure_storage_service.dart** - Service for secure storage operations using FlutterSecureStorage
- **path_storage_service.dart** - Service for managing storage paths for audiobooks and app data

## Usage

These services provide unified interfaces for storage operations across the application.

### SecureStorageService

For storing sensitive data like credentials and tokens:

```dart
final storage = SecureStorageService();
await storage.write('key', 'value');
final value = await storage.read('key');
```

### PathStorageService

For managing storage paths:

```dart
final pathService = PathStorageService();
final path = await pathService.getDefaultAudiobookPath();
await pathService.setDownloadFolderPath('/path/to/folder');
```

## Future Enhancements

Additional storage services can be added here:
- FileStorageService - for file operations
- SharedPreferencesService - for preferences storage
- etc.

