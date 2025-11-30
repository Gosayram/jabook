# Feature Template

This template provides a standard structure for creating new features following Clean Architecture principles.

## Structure

```
features/{feature_name}/
├── data/
│   └── providers/
│       └── {feature_name}_providers.dart    # Feature-specific providers for use cases
├── presentation/
│   ├── providers/                            # Presentation state providers
│   │   └── {feature_name}_state_provider.dart
│   ├── screens/
│   │   └── {feature_name}_screen.dart        # Main feature screen
│   └── widgets/
│       └── {feature_name}_widget.dart        # Reusable widgets
└── README.md                                  # Feature documentation
```

## Architecture Guidelines

### Domain Layer
The domain layer should be located in `core/domain/{feature_name}/` and include:
- **Entities**: Domain entities specific to this feature
- **Repository Interface**: Abstract repository interface
- **Use Cases**: Business logic use cases

### Data Layer
The data layer should be located in `core/data/{feature_name}/` and include:
- **Data Sources**: Local and remote data sources
- **Repository Implementation**: Concrete repository implementation
- **Mappers**: Data to domain entity mappers

### Presentation Layer
The presentation layer should be in `features/{feature_name}/presentation/` and include:
- **Screens**: UI screens
- **Widgets**: Reusable UI components
- **Providers**: Feature-specific state management

## Implementation Steps

1. **Create Domain Layer** (in `core/domain/{feature_name}/`)
   - Define domain entities
   - Create repository interface
   - Implement use cases

2. **Create Data Layer** (in `core/data/{feature_name}/`)
   - Implement data sources (local/remote)
   - Create repository implementation
   - Add mappers for data transformation

3. **Create Core Providers** (in `core/di/providers/{feature_name}_providers.dart`)
   - Create providers for repository
   - Create providers for use cases

4. **Create Feature Structure** (in `features/{feature_name}/`)
   - Create data/providers for feature-specific use case providers
   - Create presentation/screens
   - Create presentation/widgets
   - Create presentation/providers for state management

5. **Documentation**
   - Create README.md explaining the feature
   - Document usage examples
   - Document migration status if applicable

## Example: Creating a New Feature

### Step 1: Domain Layer

```dart
// core/domain/{feature_name}/entities/{feature_name}_entity.dart
class FeatureEntity {
  final String id;
  final String name;
  // ...
}

// core/domain/{feature_name}/repositories/{feature_name}_repository.dart
abstract class FeatureRepository {
  Future<List<FeatureEntity>> getItems();
  Future<FeatureEntity> getItem(String id);
}

// core/domain/{feature_name}/use_cases/get_items_use_case.dart
class GetItemsUseCase {
  GetItemsUseCase(this._repository);
  final FeatureRepository _repository;
  
  Future<List<FeatureEntity>> call() => _repository.getItems();
}
```

### Step 2: Data Layer

```dart
// core/data/{feature_name}/datasources/{feature_name}_local_datasource.dart
abstract class FeatureLocalDataSource {
  Future<List<Map<String, dynamic>>> getItems();
}

// core/data/{feature_name}/datasources/{feature_name}_remote_datasource.dart
abstract class FeatureRemoteDataSource {
  Future<List<Map<String, dynamic>>> getItems();
}

// core/data/{feature_name}/repositories/{feature_name}_repository_impl.dart
class FeatureRepositoryImpl implements FeatureRepository {
  FeatureRepositoryImpl(
    this._remoteDataSource,
    this._localDataSource,
  );
  
  final FeatureRemoteDataSource _remoteDataSource;
  final FeatureLocalDataSource _localDataSource;
  
  @override
  Future<List<FeatureEntity>> getItems() async {
    // Implementation
  }
}
```

### Step 3: Core Providers

```dart
// core/di/providers/{feature_name}_providers.dart
final featureRepositoryProvider = Provider<FeatureRepository>((ref) {
  final remoteDataSource = ref.watch(featureRemoteDataSourceProvider);
  final localDataSource = ref.watch(featureLocalDataSourceProvider);
  return FeatureRepositoryImpl(remoteDataSource, localDataSource);
});

final getItemsUseCaseProvider = Provider<GetItemsUseCase>((ref) {
  final repository = ref.watch(featureRepositoryProvider);
  return GetItemsUseCase(repository);
});
```

### Step 4: Feature Providers

```dart
// features/{feature_name}/data/providers/{feature_name}_providers.dart
final getItemsUseCaseProvider = Provider<GetItemsUseCase>((ref) {
  final repository = ref.watch(core.featureRepositoryProvider);
  return GetItemsUseCase(repository);
});
```

### Step 5: Presentation

```dart
// features/{feature_name}/presentation/screens/{feature_name}_screen.dart
class FeatureScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final useCase = ref.watch(getItemsUseCaseProvider);
    // Use use case to get data
    return Scaffold(/* ... */);
  }
}
```

## Best Practices

1. **Always use use cases** - Never call repositories directly from presentation layer
2. **Use providers for dependency injection** - All dependencies should be provided via Riverpod
3. **Separate concerns** - Domain, data, and presentation should be clearly separated
4. **Document everything** - Create README.md for each feature
5. **Follow naming conventions** - Use consistent naming across the codebase
6. **Handle errors properly** - Use domain entities for error handling (Failures)
7. **Test each layer** - Write tests for domain, data, and presentation layers

## Migration from Old Structure

If migrating an existing feature:

1. Identify domain entities and extract them
2. Create repository interface based on current service methods
3. Create use cases for business logic
4. Implement data layer with mappers
5. Create providers for dependency injection
6. Migrate presentation layer to use use cases
7. Remove old service dependencies

## Checklist

- [ ] Domain layer created in `core/domain/{feature_name}/`
- [ ] Data layer created in `core/data/{feature_name}/`
- [ ] Core providers created in `core/di/providers/`
- [ ] Feature structure created in `features/{feature_name}/`
- [ ] Feature-specific providers created
- [ ] Presentation layer uses use cases
- [ ] README.md created with documentation
- [ ] All imports updated to use new structure
- [ ] Old service dependencies removed

