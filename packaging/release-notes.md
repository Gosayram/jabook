# JaBook Release Notes

## Version 1.0.0

### Changes
- Initial release of JaBook audiobook streaming app
- Offline-first architecture with no external backend required
- Optional RuTracker login for online search functionality
- Mirror/endpoint manager for handling RuTracker domain changes
- Unified User-Agent across WebView and OkHttp
- Robust debug logging with in-app viewer and file sharing
- Background playback with Media3 ExoPlayer
- Cross-platform compatibility (Android 7.0 through 15)
- GitHub distribution with per-ABI APKs

### Features
- **Local Library**: Browse and play downloaded audiobooks
- **Online Search**: Search RuTracker when logged in
- **Topic Details**: View torrent information and download files
- **Background Playback**: Continue listening with lock screen controls
- **Mirror Management**: Add, validate, and switch between RuTracker mirrors
- **Debug Tools**: Live log viewing, filtering, and sharing
- **Data Export**: Export logs and app data for troubleshooting

### Technical Highlights
- Svelte + Vite frontend with WebView container
- libtorrent4j for torrent handling
- ExoPlayer for audio playback
- NanoHTTPD for local streaming server
- NDJSON logging with rotation
- Modular architecture with core modules

### Downloads
- [APK (Universal)](https://github.com/yourusername/jabook/releases/download/v1.0.0/jabook-1.0.0-universal.apk)
- [APK (arm64-v8a)](https://github.com/yourusername/jabook/releases/download/v1.0.0/jabook-1.0.0-arm64-v8a.apk)
- [APK (armeabi-v7a)](https://github.com/yourusername/jabook/releases/download/v1.0.0/jabook-1.0.0-armeabi-v7a.apk)
- [APK (x86)](https://github.com/yourusername/jabook/releases/download/v1.0.0/jabook-1.0.0-x86.apk)
- [APK (x86_64)](https://github.com/yourusername/jabook/releases/download/v1.0.0/jabook-1.0.0-x86_64.apk)

### SHA256 Checksums
```
jabook-1.0.0-universal.apk: [checksum]
jabook-1.0.0-arm64-v8a.apk: [checksum]
jabook-1.0.0-armeabi-v7a.apk: [checksum]
jabook-1.0.0-x86.apk: [checksum]
jabook-1.0.0-x86_64.apk: [checksum]
```

### Installation Instructions
1. Download the appropriate APK for your device architecture
2. Enable "Unknown sources" in device settings if needed
3. Install the APK file
4. Open JaBook and start enjoying audiobooks!

### Requirements
- Android 7.0 (API 24) or higher
- Internet connection for initial setup and RuTracker access
- Storage space for downloaded torrents

### Known Issues
- Initial setup may require RuTracker login for full functionality
- Some older devices may have compatibility issues with ExoPlayer
- Large torrent files may take time to download

### Support
For issues and feature requests, please visit:
- [GitHub Issues](https://github.com/yourusername/jabook/issues)
- [Wiki](https://github.com/yourusername/jabook/wiki)

### License
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

*JaBook - Offline-first audiobook streaming for RuTracker torrents*