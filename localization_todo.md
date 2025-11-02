# Localization Todo List

## Hardcoded Strings Found in Codebase

### 1. Debug Screen

- "Debug Tools" - appBar title
- "Logs" - tab text
- "Mirrors" - tab text
- "Downloads" - tab text
- "Cache" - tab text
- "Test all mirrors" - button text
- "Status: $statusText" - mirror status text
- "Last OK: $lastOk" - mirror status text
- "RTT: $rtt ms" - mirror status text
- "Download ${download['id']}" - download title
- "Status: $status" - download status
- "Progress: ${progress.toStringAsFixed(1)}%" - download progress
- "Delete download" - delete button
- "Cache Statistics" - section title
- "Total entries: " - cache stat label
- "Search cache: " - cache stat label
- "Topic cache: " - cache stat label
- "Memory usage: " - cache stat label
- "Clear all cache" - button text
- "Cache is empty" - empty cache message
- "Refresh debug data" - refresh button
- "Export logs" - export button

### 2. WebView Screen

- "RuTracker" - appBar title
- "Этот сайт использует проверки безопасности Cloudflare. Пожалуйста, дождитесь завершения проверки и взаимодействуйте с открывшейся страницей при необходимости." - cloudflare message
- "Повторить попытку" - retry button
- "Перейти на главную" - home button
- "Сайт проверяет ваш браузер — пожалуйста, дождитесь завершения проверки в этой странице." - browser check message
- "Скачать торрент" - dialog title
- "Выберите действие:" - dialog content
- "Открыть" - open button
- "Скачать" - download button
- "Для скачивания файла, пожалуйста, откройте ссылку в браузере" - download message

### 3. Library Screen

- "Import Audiobooks" - dialog title
- "Select audiobook files from your device to add to your library" - dialog content
- "Cancel" - cancel button
- "Import" - import button
- "Imported $importedCount audiobook(s)" - success message
- "No files selected" - no files message
- "Failed to import: $e" - error message
- "Scan Folder" - dialog title
- "Scan a folder on your device for audiobook files" - dialog content
- "Scan" - scan button
- "Found and imported $importedCount audiobook(s)" - success message
- "No audiobook files found in selected folder" - no files message
- "No folder selected" - no folder message
- "Failed to scan folder: $e" - error message

### 4. Auth Screen

- "RuTracker Connection" - appBar title
- "Username" - label
- "Password" - label
- "Remember me" - checkbox text
- "Login" - login button
- "Test Connection" - test button
- "Logout" - logout button
- "Login to RuTracker to access audiobook search and downloads. Your credentials are stored securely." - help text
- "Please enter username and password" - status message
- "Logging in..." - status message
- "Login successful!" - success message
- "Login failed. Please check credentials" - failure message
- "Login error: ${e.toString()}" - error message
- "Testing connection..." - status message
- "Connection successful! Using: $activeEndpoint" - success message
- "Connection test failed: ${e.toString()}" - error message
- "Logging out..." - status message
- "Logged out successfully" - success message
- "Logout error: ${e.toString()}" - error message

### 5. Settings Screen

- "Manage Mirrors" - subtitle
- "Configure and test RuTracker mirrors" - subtitle
- "Playback Speed" - title
- "1.0x" - subtitle
- "Skip Duration" - title
- "15 seconds" - subtitle
- "Download Location" - title
- "/storage/emulated/0/Download" - subtitle
- "Dark Mode" - title
- "High Contrast" - title
- "Wi-Fi Only Downloads" - title

### 6. Mirror Settings Screen

- "Failed to load mirrors: $e" - error message
- "Mirror $url tested successfully" - success message
- "Failed to test mirror $url: $e" - error message
- "Mirror ${enabled ? 'enabled' : 'disabled'}" - status message
- "Failed to update mirror: $e" - error message
- "Add Custom Mirror" - dialog title
- "Mirror URL" - label
- "https://rutracker.example.com" - hint text
- "Priority (1-10)" - label
- "5" - hint text
- "Add" - add button
- "Mirror ${urlController.text} added" - success message
- "Failed to add mirror: $e" - error message
- "Mirror Settings" - appBar title
- "Configure RuTracker mirrors for optimal search performance. Enabled mirrors will be used automatically." - description
- "Add Custom Mirror" - button text
- "Priority: $priority" - priority text
- "Response time: $rtt ms" - response time text
- "Last checked: ${\_formatDate(lastOk)}" - last check text
- "Test this mirror" - button text
- "Active" - active status
- "Disabled" - disabled status
- "Never" - never date
- "Invalid date" - invalid date message

### 7. Player Screen

- "Player: ${widget.bookId}" - appBar title
- "Failed to load audiobook" - error message
- "Retry" - retry button
- "by ${\_audiobook!.author}" - author text
- "Chapters" - chapters title
- "Download functionality coming soon!" - download message
- "Sample Audiobook" - sample title
- "Sample Author" - sample author
- "Fiction" - sample category
- "150 MB" - sample size
- "Chapter 1" - sample chapter
- "Chapter 2" - sample chapter

### 8. Topic Screen

- "Topic: ${widget.topicId}" - appBar title
- "Request timed out. Please check your connection." - timeout message
- "Network error: ${e.message}" - network error message
- "Error loading topic: $e" - error message
- "Failed to load topic" - error message
- "Retry" - retry button
- "Data loaded from cache" - cache message
- "$seeders seeders" - seeders text
- "$leechers leechers" - leechers text
- "Magnet Link" - magnet link title
- "Magnet link copied to clipboard" - copy success message
- "$label copied to clipboard" - copy success message
- "Unknown chapter" - unknown chapter text

### 9. Search Screen

- "Request timed out. Please check your connection." - timeout message
- "Network error: ${e.message}" - network error message
- "Error: $e" - error message
- "Navigate to login screen" - navigation text

### 10. Core/Permissions

- "Отмена" - cancel button (Russian)
- "Разрешить" - allow button (Russian)

### 11. Core/Auth

- "Login to RuTracker" - dialog title
- "Cancel" - cancel button
- "Logout failed" - error message
- "Cookie sync failed: ${e.toString()}" - error message

### 12. Core/Player

- "Failed to start audio service" - error message
- "Failed to play media" - error message
- "Failed to pause media" - error message
- "Failed to stop media" - error message
- "Failed to seek" - error message
- "Failed to set speed" - error message

### 13. Core/Logging

- "Failed to initialize logger" - error message
- "Failed to write log" - error message
- "Log rotation failed" - error message
- "Failed to rotate logs" - error message
- "Failed to clean old logs" - error message
- "Failed to export logs" - error message
- "Error sharing logs: $e" - error message
- "Failed to share logs" - error message
- "Failed to read logs" - error message

### 14. Core/Stream

- "Local stream server started on http://$_host:$\_port" - success message
- "Failed to start stream server: ${e.toString()}" - error message
- "Local stream server stopped" - success message
- "Failed to stop stream server: ${e.toString()}" - error message
- "Missing book ID parameter" - error message
- "Invalid file index parameter" - error message
- "File not found" - error message
- "Streaming error" - error message
- "Invalid range header" - error message
- "Requested range not satisfiable" - error message
- "Range request error" - error message
- "Static file error" - error message

### 15. Core/Cache

- "CacheManager not initialized" - error message

### 16. Core/Parse

- "Failed to parse search results" - error message
- "Failed to parse topic details" - error message
- "Failed to parse categories" - error message
- "Failed to parse category topics" - error message

### 17. Core/Torrent

- "Invalid magnet URL: missing info hash" - error message
- "Invalid info hash length" - error message
- "Failed to start download: ${e.toString()}" - error message
- "Download not found" - error message
- "Failed to pause download: ${e.toString()}" - error message
- "Failed to resume download: ${e.toString()}" - error message
- "Failed to remove download: ${e.toString()}" - error message
- "Failed to get active downloads: ${e.toString()}" - error message
- "Failed to shutdown torrent manager: ${e.toString()}" - error message

### 18. Core/Endpoints

- "No healthy endpoints available" - error message

### 19. Core/Config

- "AuthRepositoryProvider must be overridden with proper context" - error message
- "Use EndpointManager.getActiveEndpoint() for dynamic mirror selection" - deprecated message
- "CacheManager not initialized" - error message

### 20. Core/Net

- "Search failed: ${e.message}" - error message
- "Failed to search audiobooks" - error message
- "Failed to fetch categories: ${e.message}" - error message
- "Failed to get categories" - error message
- "Failed to get category audiobooks: ${e.message}" - error message
- "Failed to get category audiobooks" - error message
- "Failed to fetch audiobook details: ${e.message}" - error message
- "Failed to get audiobook details" - error message
- "Failed to fetch new releases: ${e.message}" - error message

### 21. Core/Auth/Credential Manager

- "Failed to save credentials: ${e.toString()}" - error message
- "Failed to retrieve credentials: ${e.toString()}" - error message
- "Failed to clear credentials: ${e.toString()}" - error message
- "No credentials to export" - error message
- "Unsupported export format: $format" - error message
- "Invalid CSV format" - error message
- "Invalid CSV data" - error message
- "Invalid JSON format" - error message
- "Unsupported import format: $format" - error message
- "Failed to import credentials: ${e.toString()}" - error message

### 22. Core/Net/Cloudflare Utils

- "checking your browser" - cloudflare check
- "please enable javascript" - cloudflare check
- "attention required" - cloudflare check
- "cf-chl-bypass" - cloudflare check
- "challenges.cloudflare.com" - cloudflare check
- "cf-turnstile" - cloudflare check

### 23. Core/Parse/Category Parser

- "Failed to parse categories" - error message
- "Failed to parse category topics" - error message

### 24. Core/Net/User Agent Manager

- "Failed to store user agent: $e" - error message
- "Failed to clear user agent: $e" - error message

### 25. Core/Parse/Rutracker Parser

- "Failed to parse search results" - error message
- "Failed to parse topic details" - error message
- "Радиоспектакль" - category
- "Аудиокнига" - category
- "Биография" - category
- "Мемуары" - category
- "История" - category
- "Другое" - category
- "Добавлено" - date label
- "Сиды" - seeders label
- "Личи" - leechers label

### 26. App/Router

- "Error" - error title
- "Library" - navigation title
- "Connect" - navigation title
- "Search" - navigation title
- "Settings" - navigation title
- "Debug" - navigation title

### 27. App/App

- "App initialization complete" - log message
- "Initializing JaBook app..." - log message
- "Build flavor: ${config.flavor}" - log message
- "App version: ${config.appVersion}" - log message
- "API base URL: ${config.apiBaseUrl}" - log message
- "Log level: ${config.logLevel}" - log message
- "Unknown flavor: ${config.flavor}, falling back to dev" - log message
- "Initializing database..." - log message
- "Database, cache, and endpoints initialized successfully" - log message
- "Initializing development environment" - log message
- "Debug features enabled" - log message
- "Initializing stage environment" - log message
- "Analytics enabled for stage environment" - log message
- "Crash reporting enabled for stage environment" - log message
- "Initializing production environment" - log message
- "Analytics enabled for production environment" - log message
- "Crash reporting enabled for production environment" - log message
- "Debug features disabled for production environment" - log message
