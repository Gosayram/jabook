# APK Build Guide

## Available Tasks

### Build Debug APK
```bash
./gradlew buildApk
```
Builds debug APK and saves to `bin/` folder with timestamp in filename.

### Build Release APK
```bash
./gradlew buildReleaseApk
```
Builds release APK (requires signing configuration).

### Clean bin/ Directory
```bash
./gradlew cleanBin
```
Removes all files from `bin/` directory.

## File Structure

APK files are saved to `bin/` directory with names:
- Debug: `JaBook_YYYYMMDD_HHMMSS.apk`
- Release: `JaBook_Release_YYYYMMDD_HHMMSS.apk`

## Installation on Device

1. Enable "Unknown sources" in Android settings
2. Copy APK file to device
3. Open file and follow installation instructions

## Notes

- `bin/` directory is added to `.gitignore` and won't be tracked by Git
- Debug APK can be installed without signing
- Release APK requires keystore configuration for signing
- APK size is approximately 28MB for debug version 