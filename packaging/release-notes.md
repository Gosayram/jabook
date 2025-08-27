# JaBook v1.0.0 Release Notes

## What's New

### Initial Release
- 🎵 **Offline-first audiobook app** for RuTracker torrents
- 🔐 **Works without login** - local library, playback, history, exports
- 🔍 **Optional login** for online search (RuTracker requires auth)
- 🌐 **Mirror management** with automatic failover
- 🎨 **Branded with JaBook theme** - violet, beige, orange color palette
- 📱 **Multi-ABI support** - armeabi-v7a, arm64-v8a, x86, x86_64
- 🚀 **GitHub distribution** - no Google Play dependency

### Features

#### Core Functionality
- **Local library management** - organize and play downloaded audiobooks
- **Background playback** with lock screen controls
- **History tracking** - resume where you left off
- **Export functionality** - share your library
- **Sequential download** for streaming large torrent files

#### RuTracker Integration
- **Mirror manager** - automatic health checks and failover
- **Search functionality** (requires login)
- **Topic browsing** with detailed information
- **Magnet link support** for easy torrent addition

#### User Experience
- **Modern Svelte SPA** interface
- **Adaptive icons** with JaBook branding
- **Splash screen** with fade-in animation
- **Color-coded debug logging** with sharing capabilities
- **Responsive design** for various screen sizes

### Technical Details

#### Architecture
- **Modular design** with separate core modules
- **WebView + Native Core** hybrid approach
- **Local REST API** for SPA communication
- **Unified User-Agent** between WebView and OkHttp
- **NDJSON logging** with rotation and sharing

#### Security
- **Network security configuration** - local server only
- **Cookie management** - session persistence
- **FileProvider** for secure file sharing
- **No external backend** - all processing on-device

### Installation

#### Requirements
- Android 7.0 (API 24) or higher
- Internet connection for initial setup and RuTracker access
- Storage space for audiobook downloads

#### Download APKs
Choose the appropriate APK for your device architecture:
- `app-release-armeabi-v7a.apk` - For older 32-bit ARM devices
- `app-release-arm64-v8a.apk` - For modern 64-bit ARM devices (recommended)
- `app-release-x86.apk` - For 32-bit x86 devices
- `app-release-x86_64.apk` - For 64-bit x86 devices
- `app-release-universal.apk` - Contains all architectures (larger file size)

#### Installation Steps
1. Download the appropriate APK file
2. Enable "Unknown sources" in device settings if needed
3. Tap the APK file to install
4. Open JaBook and enjoy your audiobooks!

### Known Issues

- Initial setup may require network permissions
- Some older devices may experience performance issues with the WebView
- Large audiobook files may take time to start streaming
- Login functionality may require manual intervention for CAPTCHA

### Future Plans

- [ ] Enhanced search filters and sorting
- [ ] Playlist management
- [ ] Sleep timer with custom duration
- [ ] Chromecast support
- [ ] Dark/light theme toggle
- [ ] Download queue management
- [ ] Audiobook metadata editing
- [ ] Cloud backup sync

### Support

For issues, feature requests, or contributions:
- Check the [GitHub Issues](https://github.com/Gosayram/jabook/issues)
- Review the [documentation](https://github.com/Gosayram/jabook/wiki)
- Join the community discussions

---

**JaBook** - Your offline audiobook companion for RuTracker content