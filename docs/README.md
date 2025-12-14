# 📚 Архитектурная документация Jabook

Комплексная визуальная документация Android-приложения Jabook с использованием **специализированных типов Mermaid диаграмм**: Entity Relationship Diagrams (ERD), State Diagrams, и Architecture Diagrams.

## 📑 Содержание

### 1. [Архитектура системы](01-app-overview.md) 🏗️

**Architecture Diagram** - общая структура приложения:

- Android App Layer (Application, Activities)
- Presentation Layer (10 feature modules)
- Domain Layer (Use Cases, Models)
- Data Layer (Repositories, Data Sources)
- Services (Audio, Downloads, Sync)
- External Dependencies (Media3, LibTorrent, APIs)
- C4 Container Level diagram

**Технология**: `architecture-beta` + traditional graphs

---

### 2. [Диаграммы состояний](02-navigation-flow.md) 🔄

**10 State Diagrams** для различных аспектов приложения:

1. **UI States**: Loading → Success/Error transitions
2. **Navigation Flow**: Screen transitions и deep links
3. **Download States**: Queued → Downloading → Completed/Failed
4. **Player States**: Idle → Playing → Paused → Buffering
5. **Player Screen States**: NoBook → LoadingBook → PlayerReady
6. **Service Lifecycle**: Created → Running → Stopping
7. **Auth States**: LoggedOut → LoggingIn → LoggedIn
8. **Sync States**: Idle → Syncing → Success/Failed
9. **Search States**: Empty → Searching → Results
10. **WebView States**: Loading → PageLoaded → MagnetDetected

**Технология**: `stateDiagram-v2`

---

### 3. [MVVM Architecture](03-screen-hierarchy.md) 📱

**Паттерны и flow diagrams** для screen architecture:

- MVVM pattern explanation
- UiState sealed interfaces
- ViewModel lifecycle
- Dependency injection in screens
- Common patterns (Loading, Flow collection, Event handling)
- Sequence diagrams для user interactions

**Технология**: `graph`, `stateDiagram-v2`, `sequenceDiagram`

---

### 4. [Схема базы данных](04-data-layer.md) 🗄️

**Entity Relationship Diagram** для Room Database:

- **9 основных таблиц**:
  - BOOKS (главная таблица книг)
  - AUDIO_FILES (аудиофайлы)
  - PLAYLISTS (плейлисты)
  - PLAYLIST_ITEMS (треки в плейлистах)
  - PLAYBACK_POSITIONS (позиции воспроизведения)
  - CHAPTERS (главы книг)
  - CHAPTER_METADATA (метаданные глав)
  - SAVED_PLAYER_STATE (состояние плеера)
  - DOWNLOADS (загрузки торрентов)
  - SEARCH_HISTORY (история поиска)
  - USER_PREFERENCES (настройки)

**Связи**: One-to-Many, One-to-One relationships
**Индексы и миграции** включены

**Технология**: `erDiagram`

---

### 5. [Архитектура аудио-плеера](05-audio-player.md) 🎵

**7 State Diagrams** + component graphs:

1. **Player Lifecycle**: ServiceCreated → Ready → Playing → Destroying
2. **Component Structure**: 29 модульных компонентов
3. **Sleep Timer States**: Inactive → Active → Completed
4. **Position Saving**: Strategy с auto-save каждые 5 секунд
5. **Notification Updates**: Metadata/State/Progress updates
6. **Chapter Navigation**: Loading → Ready → Seeking
7. **Audio Processing Pipeline**: Source → Processors → Output
8. **Media Session Actions**: Button handling flow

**Компоненты**: PlaylistManager, PlaybackController, MediaSession, Notifications, Timers

**Технология**: `stateDiagram-v2` + `graph`

---

### 6. [Система загрузок](06-download-system.md) 📥

**8 State Diagrams** для download engine:

1. **Service Lifecycle**: NotStarted → Running → Stopped
2. **Download State Machine**: Queued → Downloading → Completed/Failed
3. **Queue Management**: Empty → HasItems с concurrent limit (3)
4. **Download Flow**: User action → Service → Torrent → File System
5. **WorkManager Integration**: Constraints and retry logic
6. **Torrent Session**: Initialization → Active → Destroyed
7. **Notification States**: Progress updates
8. **Error Handling Flow**: Network/Storage/Magnet errors

**Компоненты**: DownloadService, TorrentManager, QueueManager, WorkManager

**Технология**: `stateDiagram-v2` + `graph`

---

### 7. [Dependency Injection](07-dependency-injection.md) 💉

**ERD-style dependency graph** + traditional diagrams:

- **6 Hilt Modules**: Network, Database, Data, Download, Auth, Media
- **Dependency relationships**: Application → Singletons → Repositories → ViewModels/Services/Workers
- **Scopes hierarchy**: Application → Activity/Service/Worker → ViewModel
- **Injection types**: Constructor, Field, Assisted injection
- **Module code examples** для каждого модуля

**Технология**: `erDiagram` + `graph`

---

### 8. [Feature Modules](08-feature-modules.md) 🧩

**Dependency graphs** и communication patterns:

- Feature dependencies (Design System, L10n, Navigation, DI)
- **Navigation Communication**: State diagram переходов
- **Shared Repository Pattern**: Multiple features observing same data
- **Module Dependency Rules**: Allowed vs Forbidden
- **Data Flow** per feature (Library, Player, Downloads)
- **Anti-patterns** to avoid

**Технология**: `graph` + `stateDiagram-v2`

---

## 🛠 Технологический стек

### Диаграммы

- **Mermaid ERD**: Схема базы данных
- **Mermaid State Diagrams**: Машины состояний
- **Mermaid Architecture Diagram**: Структура системы
- **Mermaid Graphs**: Потоки данных и зависимости
- **Mermaid Sequence Diagrams**: Взаимодействия компонентов

### Приложение

- **UI**: Jetpack Compose + Material 3
- **Architecture**: Clean Architecture + MVVM
- **DI**: Dagger Hilt
- **Database**: Room + DataStore
- **Network**: Retrofit + OkHttp
- **Audio**: Media3 ExoPlayer
- **Downloads**: LibTorrent4j + WorkManager
- **Async**: Kotlin Coroutines + Flow

---

## 🎨 Просмотр диаграмм

### В редакторе с плагином

1. **VS Code**: Установите "Markdown Preview Mermaid Support"
2. **WebStorm/IntelliJ**: Встроенная поддержка Mermaid
3. **Obsidian**: Встроенная поддержка
4. Откройте `.md` файл и активируйте Preview

### Online

- [Mermaid Live Editor](https://mermaid.live) - копируйте код диаграммы
- [Mermaid Chart](https://www.mermaidchart.com/) - создавайте и редактируйте
- GitHub автоматически рендерит Mermaid в preview

---

## 📊 Статистика диаграмм

- **ERD**: 1 диаграмма (9 entities, 20+ relationships)
- **State Diagrams**: 28 диаграмм (UI, navigation, downloads, player, services)
- **Architecture Diagram**: 2 диаграммы (system components, C4 container)
- **Flow Diagrams**: 15+ диаграмм (data flows, dependencies, patterns)
- **Sequence Diagrams**: 1 диаграмма (user interactions)

**Всего**: 45+ диаграмм covering complete architecture

---

## 🔍 Какую диаграмму использовать?

| Что нужно показать | Тип диаграммы | Пример |
|-------------------|---------------|--------|
| База данных, entities, relationships | **ERD** | [04-data-layer.md](04-data-layer.md) |
| Состояния и переходы | **State Diagram** | [02-navigation-flow.md](02-navigation-flow.md) |
| Системная архитектура | **Architecture Diagram** | [01-app-overview.md](01-app-overview.md) |
| Потоки данных | **Graph** | [03-screen-hierarchy.md](03-screen-hierarchy.md) |
| Зависимости компонентов | **Graph** + **ERD** | [07-dependency-injection.md](07-dependency-injection.md) |
| Взаимодействия во времени | **Sequence Diagram** | [03-screen-hierarchy.md](03-screen-hierarchy.md) |

---

## 📝 Конвенции

### State Diagrams

- `[*]` - начальное состояние
- `state` blocks - вложенные состояния
- `-->` - переход
- `note right/left` - комментарии

### ERD

- `||--o{` - One-to-Many
- `||--||` - One-to-One
- `}o--o{` - Many-to-Many
- `PK` - Primary Key
- `FK` - Foreign Key

### Architecture Diagram

- `service` - компонент/сервис
- `database` - база данных
- `internet` - внешний API
- `R → L` / `L → R` / `T → B` / `B → T` - направления связей

---

## 🤝 Обновление документации

При изменении архитектуры:

1. Определите тип изменения (data/state/architecture)
2. Обновите соответствующую диаграмму
3. Проверьте syntax на [Mermaid Live](https://mermaid.live)
4. Обновите связанные диаграммы
5. Обновите README при необходимости

---

## 📚 Ресурсы

- [Mermaid Documentation](https://mermaid.js.org/)
- [ERD Syntax](https://mermaid.js.org/syntax/entityRelationshipDiagram.html)
- [State Diagram Syntax](https://mermaid.js.org/syntax/stateDiagram.html)
- [Architecture Diagram](https://mermaid.js.org/syntax/architecture.html)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)

---

**Дата обновления**: 2025-12-15  
**Версия**: 2.0.0 (Specialized Diagrams)  
**Автор**: Google Gemini Antigravity
