# JaBook - Android Audiobook Player

## Project Overview

JaBook is a modern Android audiobook player designed as a successor to discontinued audiobook applications. The app provides seamless integration with RuTracker.net for discovering and downloading audiobooks via torrent protocol.

## Core Features

### 1. Audio Playback
- **ExoPlayer Integration**: High-quality audio playback with Media3
- **Chapter Navigation**: Seamless chapter-to-chapter playback
- **Playback Controls**: Play, pause, seek, speed control (0.5x - 3.0x)
- **Sleep Timer**: Automatic playback stop after specified time
- **Background Playback**: MediaSession integration for system controls
- **Audio Focus Management**: Proper handling of audio interruptions

### 2. Library Management
- **Local Storage**: Organized audiobook collection with metadata
- **Smart Filtering**: By author, category, download status, completion
- **Search Functionality**: Full-text search across titles and authors
- **Favorites System**: Mark and filter favorite audiobooks
- **Progress Tracking**: Resume playback from last position

### 3. RuTracker Integration
- **Dual Mode Support**: Guest browsing + authenticated downloads
- **Guest Mode**: Browse and view magnet links without registration
- **Authenticated Mode**: Full access with user credentials
- **Search & Discovery**: Advanced search with filters and sorting
- **Category Browsing**: Navigate through audiobook categories
- **Real-time Updates**: Live seeder/leecher information

### 4. Download Management
- **Torrent Engine**: LibTorrent4j integration for downloads
- **Progress Tracking**: Real-time download progress and speed
- **Queue Management**: Pause, resume, prioritize downloads
- **File Extraction**: Automatic archive extraction and organization
- **Storage Management**: Smart space allocation and cleanup

### 5. User Interface
- **Material Design 3**: Modern, adaptive UI with dynamic theming
- **Dark/Light/Auto Themes**: System theme following with manual override
- **Responsive Design**: Optimized for phones and tablets
- **Accessibility**: Screen reader support and high contrast modes
- **Animations**: Smooth transitions and micro-interactions

## Technical Architecture

### Technology Stack
- **Language**: Kotlin 2.2.x targeting JVM 17
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Clean Architecture principles
- **Dependency Injection**: Hilt/Dagger
- **Database**: Room with SQLite for local storage
- **Audio**: ExoPlayer (Media3) for playback
- **Networking**: Retrofit2 + OkHttp3 for API calls
- **Image Loading**: Coil for cover art
- **Torrent**: LibTorrent4j for download management

### Module Structure
```
app/
├── core/
│   ├── network/          # RuTracker API client
│   ├── database/         # Room database entities
│   ├── torrent/          # Torrent download engine
│   ├── storage/          # File management
│   └── compat/           # Android version compatibility
├── features/
│   ├── library/          # Book library & organization
│   ├── player/           # Audio player functionality
│   ├── discovery/        # RuTracker browsing
│   └── downloads/        # Download management
└── shared/
    ├── ui/               # Common UI components
    ├── utils/            # Utilities & extensions
    └── debug/            # Debug tools & logging
```

### Data Flow
1. **Discovery**: RuTracker API → Domain Models → UI
2. **Download**: Torrent Engine → File Manager → Database
3. **Playback**: Database → ExoPlayer → Audio Focus
4. **Library**: Database → Repository → UI

## RuTracker API Integration Plan

### Current Issues
- Не работает получение данных с rutracker.net
- Отсутствует поддержка аутентификации пользователей
- Нет возможности просматривать magnet ссылки в гостевом режиме
- Ограниченная функциональность поиска и фильтрации

### Proposed Solution

#### 1. Dual Mode Architecture
- **Guest Mode**: Без регистрации, только просмотр и magnet ссылки
- **Authenticated Mode**: Полный доступ с учетными данными пользователя

#### 2. API Client Redesign
```kotlin
interface RuTrackerApiClient {
    // Guest mode operations
    suspend fun searchGuest(query: String, category: String? = null): List<RuTrackerAudiobook>
    suspend fun getCategoriesGuest(): List<RuTrackerCategory>
    suspend fun getAudiobookDetailsGuest(topicId: String): RuTrackerAudiobook?
    suspend fun getMagnetLinkGuest(topicId: String): String?
    
    // Authenticated mode operations
    suspend fun login(username: String, password: String): Boolean
    suspend fun searchAuthenticated(query: String, sort: String = "seeds", order: String = "desc"): List<RuTrackerAudiobook>
    suspend fun downloadTorrent(topicId: String): InputStream?
    suspend fun getMagnetLinkAuthenticated(topicId: String): String?
    suspend fun logout()
    
    // Common operations
    fun isAuthenticated(): Boolean
    fun getCurrentUser(): String?
}
```

#### 3. User Preferences Management
```kotlin
interface RuTrackerPreferences {
    suspend fun setCredentials(username: String, password: String)
    suspend fun getCredentials(): Pair<String, String>?
    suspend fun clearCredentials()
    suspend fun setGuestMode(enabled: Boolean)
    suspend fun isGuestMode(): Boolean
}
```

#### 4. Enhanced Domain Models
```kotlin
data class RuTrackerAudiobook(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String? = null,
    val description: String,
    val category: String,
    val categoryId: String,
    val year: Int? = null,
    val quality: String? = null,
    val duration: String? = null,
    val size: String,
    val sizeBytes: Long,
    val magnetUri: String? = null, // Available in guest mode
    val torrentUrl: String? = null, // Available in authenticated mode
    val seeders: Int,
    val leechers: Int,
    val completed: Int,
    val addedDate: String,
    val lastUpdate: String? = null,
    val coverUrl: String? = null,
    val rating: Float? = null,
    val genreList: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val isVerified: Boolean = false,
    val state: TorrentState = TorrentState.APPROVED,
    val downloads: Int = 0,
    val registered: Date? = null
)

enum class TorrentState {
    APPROVED,        // проверено
    NOT_APPROVED,    // не проверено
    NEED_EDIT,       // недооформлено
    DUBIOUSLY,       // сомнительно
    CONSUMED,        // поглощена
    TEMPORARY        // временная
}
```

#### 5. UI Components for Authentication
- **Settings Screen**: Переключатель режимов и поля для учетных данных
- **Login Dialog**: Модальное окно для ввода логина/пароля
- **Mode Indicator**: Индикатор текущего режима в UI
- **Authentication Status**: Отображение статуса авторизации

#### 6. Implementation Phases

**Phase 1: Guest Mode Implementation**
- Реализовать базовый API клиент для гостевого режима
- Добавить парсинг HTML страниц rutracker.net
- Реализовать поиск и получение magnet ссылок
- Добавить UI для переключения режимов

**Phase 2: Authentication System**
- Реализовать систему аутентификации с сохранением сессии
- Добавить безопасное хранение учетных данных
- Реализовать полный API для аутентифицированного режима
- Добавить обработку ошибок авторизации

**Phase 3: Enhanced Features**
- Добавить расширенный поиск с фильтрами
- Реализовать категории и подкатегории
- Добавить статистику и рейтинги
- Улучшить UI/UX для обоих режимов

#### 7. Security Considerations
- Шифрование сохраненных учетных данных
- Безопасная передача данных через HTTPS
- Обработка ошибок авторизации
- Автоматический logout при ошибках

#### 8. Error Handling
- Network connectivity issues
- Authentication failures
- Rate limiting protection
- Malformed response handling
- User-friendly error messages

### Implementation Details

#### HTML Parsing Strategy
```kotlin
interface RuTrackerParser {
    suspend fun parseSearchResults(html: String): List<RuTrackerAudiobook>
    suspend fun parseAudiobookDetails(html: String): RuTrackerAudiobook?
    suspend fun parseCategories(html: String): List<RuTrackerCategory>
    suspend fun extractMagnetLink(html: String): String?
    suspend fun extractTorrentLink(html: String): String?
    suspend fun parseTorrentState(html: String): TorrentState
}
```

#### Network Configuration
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object RuTrackerNetworkModule {
    @Provides
    @Singleton
    fun provideRuTrackerOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor("JaBook/1.0"))
            .addInterceptor(RateLimitInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
```

This comprehensive plan addresses all current limitations and provides a robust foundation for RuTracker integration with both guest and authenticated modes.

## Development Phases

### Phase 1: Foundation (Completed)
- ✅ Project setup with Kotlin 2.2.x
- ✅ Clean Architecture implementation
- ✅ Basic UI with Jetpack Compose
- ✅ Database setup with Room
- ✅ Dependency injection with Hilt

### Phase 2: Core Features (Completed)
- ✅ Audio playback with ExoPlayer
- ✅ Library management
- ✅ Basic RuTracker integration
- ✅ Download system foundation
- ✅ UI/UX improvements

### Phase 3: Advanced Features (In Progress)
- 🔄 Enhanced RuTracker API integration
- 🔄 Dual mode authentication system
- 🔄 Advanced download management
- 🔄 Performance optimizations
- 🔄 Comprehensive testing

### Phase 4: Polish & Release
- ⏳ Final UI/UX refinements
- ⏳ Performance optimization
- ⏳ Comprehensive testing
- ⏳ Documentation completion
- ⏳ Release preparation

## Testing Strategy

### Unit Tests
- Repository layer testing
- Use case testing
- Domain model validation
- API client testing

### Integration Tests
- Database operations
- Network API calls
- File system operations
- Audio playback integration

### UI Tests
- Navigation testing
- User interaction testing
- Theme switching
- Accessibility testing

## Performance Considerations

### Memory Management
- Efficient image loading with Coil
- Proper lifecycle management
- Background task optimization
- Memory leak prevention

### Network Optimization
- Request caching
- Rate limiting
- Connection pooling
- Error retry logic

### Storage Optimization
- Efficient database queries
- File compression
- Cache management
- Storage cleanup

## Security Considerations

### Data Protection
- Encrypted storage for credentials
- Secure network communication
- Input validation
- Error message sanitization

### Privacy
- No analytics or tracking
- Local data storage only
- User consent for features
- Data deletion options

## Future Enhancements

### Planned Features
- Cloud sync support
- Multiple audio format support
- Advanced audio effects
- Social features (ratings, reviews)
- Offline mode improvements

### Technical Improvements
- Kotlin Multiplatform support
- Performance monitoring
- Advanced caching strategies
- Enhanced error handling

## Conclusion

JaBook represents a modern approach to audiobook management on Android, combining powerful playback capabilities with seamless content discovery. The dual-mode RuTracker integration ensures accessibility while providing enhanced features for registered users.

The project follows Android best practices and modern development patterns, ensuring maintainability, scalability, and user satisfaction. 