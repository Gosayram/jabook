# Feature Structure Documentation

This document describes the standard structure for features in the JaBook application.

## Directory Structure

```
features/{feature_name}/
├── data/
│   └── providers/
│       └── {feature_name}_providers.dart
├── presentation/
│   ├── providers/
│   │   └── {feature_name}_state_provider.dart
│   ├── screens/
│   │   └── {feature_name}_screen.dart
│   └── widgets/
│       └── {feature_name}_widget.dart
└── README.md
```

## Layer Responsibilities

### Domain Layer (`core/domain/{feature_name}/`)

**Purpose**: Contains business logic and domain entities.

**Structure**:
```
core/domain/{feature_name}/
├── entities/
│   └── {entity_name}.dart
├── repositories/
│   └── {feature_name}_repository.dart
└── use_cases/
    └── {use_case_name}_use_case.dart
```

**Responsibilities**:
- Define domain entities (pure Dart classes)
- Define repository interfaces (abstract classes)
- Implement use cases (business logic)

**Rules**:
- No dependencies on Flutter or external packages (except for basic types)
- Entities should be immutable
- Use cases should be simple and focused

### Data Layer (`core/data/{feature_name}/`)

**Purpose**: Implements data access and transformation.

**Structure**:
```
core/data/{feature_name}/
├── datasources/
│   ├── {feature_name}_local_datasource.dart
│   └── {feature_name}_remote_datasource.dart
├── mappers/
│   └── {feature_name}_mapper.dart
└── repositories/
    └── {feature_name}_repository_impl.dart
```

**Responsibilities**:
- Implement data sources (local/remote)
- Transform data models to domain entities
- Implement repository interfaces

**Rules**:
- Data sources should return raw data (maps, JSON, etc.)
- Mappers should handle transformation
- Repository implementations should use data sources and mappers

### Presentation Layer (`features/{feature_name}/presentation/`)

**Purpose**: UI and user interaction.

**Structure**:
```
features/{feature_name}/presentation/
├── providers/
│   └── {feature_name}_state_provider.dart
├── screens/
│   └── {feature_name}_screen.dart
└── widgets/
    └── {feature_name}_widget.dart
```

**Responsibilities**:
- Display UI
- Handle user interactions
- Manage presentation state

**Rules**:
- Should only use use cases, never repositories directly
- State management through Riverpod providers
- Widgets should be reusable and testable

## Dependency Flow

```
Presentation → Use Cases → Repository Interface → Repository Implementation → Data Sources
     ↓              ↓              ↓                        ↓                      ↓
  Widgets      Domain         Domain                  Data                  External
```

**Key Points**:
- Presentation depends on Domain (use cases)
- Domain does not depend on Data or Presentation
- Data depends on Domain (repository interface)
- Data sources are at the bottom of the dependency chain

## Example Flow

1. User interacts with UI (Presentation)
2. UI calls use case (Domain)
3. Use case calls repository (Domain interface)
4. Repository implementation (Data) uses data sources
5. Data sources fetch/transform data
6. Data flows back through mappers to domain entities
7. Domain entities returned to use case
8. Use case returns to presentation
9. UI updates with new data

## Naming Conventions

- **Entities**: `{EntityName}` (e.g., `SearchResult`, `Audiobook`)
- **Repositories**: `{FeatureName}Repository` (interface), `{FeatureName}RepositoryImpl` (implementation)
- **Use Cases**: `{ActionName}UseCase` (e.g., `SearchUseCase`, `GetItemsUseCase`)
- **Data Sources**: `{FeatureName}{Local|Remote}DataSource`
- **Mappers**: `{FeatureName}Mapper`
- **Providers**: `{featureName}Provider`, `{useCaseName}Provider`
- **Screens**: `{FeatureName}Screen`
- **Widgets**: `{FeatureName}Widget`

## File Organization

### Domain Files

- `entities/{entity_name}.dart` - Domain entity
- `repositories/{feature_name}_repository.dart` - Repository interface
- `use_cases/{use_case_name}_use_case.dart` - Use case implementation

### Data Files

- `datasources/{feature_name}_local_datasource.dart` - Local data source
- `datasources/{feature_name}_remote_datasource.dart` - Remote data source
- `mappers/{feature_name}_mapper.dart` - Data mapper
- `repositories/{feature_name}_repository_impl.dart` - Repository implementation

### Presentation Files

- `providers/{feature_name}_state_provider.dart` - State management
- `screens/{feature_name}_screen.dart` - Main screen
- `widgets/{feature_name}_widget.dart` - Reusable widget

## Testing Structure

Each layer should have corresponding tests:

```
test/
├── core/
│   ├── domain/
│   │   └── {feature_name}/
│   │       └── use_cases/
│   │           └── {use_case_name}_use_case_test.dart
│   └── data/
│       └── {feature_name}/
│           └── repositories/
│               └── {feature_name}_repository_impl_test.dart
└── features/
    └── {feature_name}/
        └── presentation/
            └── screens/
                └── {feature_name}_screen_test.dart
```

## Migration Checklist

When migrating an existing feature:

1. [ ] Identify domain entities
2. [ ] Extract repository interface
3. [ ] Create use cases
4. [ ] Implement data layer
5. [ ] Create providers
6. [ ] Update presentation to use use cases
7. [ ] Remove old service dependencies
8. [ ] Update imports
9. [ ] Write tests
10. [ ] Update documentation

