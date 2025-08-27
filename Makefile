# JaBook Build Makefile
# Based on IDEA.md specifications

.PHONY: debug release apk splits aab clean install

# Variables
APP_NAME := JaBook
VERSION := $(shell grep versionName app/build.gradle.kts | cut -d'"' -f2)
BUILD_TYPE ?= debug
GRADLE := ./gradlew
GRADLE_OPTS := --parallel

# Default target
all: debug

# Build debug APK
debug:
	@echo "Building debug APK..."
	$(GRADLE) $(GRADLE_OPTS) assembleDebug
	@echo "Debug APK built successfully!"

# Build release APK
release:
	@echo "Building release APK..."
	$(GRADLE) $(GRADLE_OPTS) assembleRelease
	@echo "Release APK built successfully!"

# Build APK with per-ABI splits
apk:
	@echo "Building per-ABI APKs..."
	$(GRADLE) $(GRADLE_OPTS) assembleRelease
	@echo "Per-ABI APKs built successfully!"
	@echo "APKs available in app/build/outputs/apk/release/"

# Build universal APK
splits:
	@echo "Building universal APK..."
	$(GRADLE) $(GRADLE_OPTS) assembleRelease
	@echo "Universal APK built successfully!"
	@echo "Universal APK available in app/build/outputs/apk/release/"

# Build Android App Bundle (AAB)
aab:
	@echo "Building Android App Bundle..."
	$(GRADLE) $(GRADLE_OPTS) bundleRelease
	@echo "AAB built successfully!"
	@echo "AAB available in app/build/outputs/bundle/release/"

# Install debug APK to device
install:
	@echo "Installing debug APK to device..."
	$(GRADLE) $(GRADLE_OPTS) installDebug
	@echo "Debug APK installed successfully!"

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	$(GRADLE) --stop && $(GRADLE) $(GRADLE_OPTS) clean
	@echo "Build artifacts cleaned!"

# Generate release notes
release-notes:
	@echo "Generating release notes..."
	@echo "# JaBook Release Notes" > packaging/release-notes.md
	@echo "" >> packaging/release-notes.md
	@echo "## Version $(VERSION)" >> packaging/release-notes.md
	@echo "" >> packaging/release-notes.md
	@echo "### Changes" >> packaging/release-notes.md
	@echo "- Updated to latest dependencies" >> packaging/release-notes.md
	@echo "- Fixed various bugs" >> packaging/release-notes.md
	@echo "- Improved performance" >> packaging/release-notes.md
	@echo "" >> packaging/release-notes.md
	@echo "### Downloads" >> packaging/release-notes.md
	@echo "- [APK (Universal)](https://github.com/Gosayram/jabook/releases/download/v$(VERSION)/jabook-$(VERSION)-universal.apk)" >> packaging/release-notes.md
	@echo "- [APK (arm64-v8a)](https://github.com/Gosayram/jabook/releases/download/v$(VERSION)/jabook-$(VERSION)-arm64-v8a.apk)" >> packaging/release-notes.md
	@echo "- [APK (armeabi-v7a)](https://github.com/Gosayram/jabook/releases/download/v$(VERSION)/jabook-$(VERSION)-armeabi-v7a.apk)" >> packaging/release-notes.md
	@echo "- [APK (x86)](https://github.com/Gosayram/jabook/releases/download/v$(VERSION)/jabook-$(VERSION)-x86.apk)" >> packaging/release-notes.md
	@echo "- [APK (x86_64)](https://github.com/Gosayram/jabook/releases/download/v$(VERSION)/jabook-$(VERSION)-x86_64.apk)" >> packaging/release-notes.md
	@echo "" >> packaging/release-notes.md
	@echo "### SHA256 Checksums" >> packaging/release-notes.md
	@cd app/build/outputs/apk/release && sha256sum *.apk >> ../../../../packaging/release-notes.md
	@echo "Release notes generated in packaging/release-notes.md"

# Prepare GitHub release
github-release: release-notes splits
	@echo "Preparing GitHub release..."
	@echo "1. Commit and push changes"
	@echo "2. Tag the release: git tag v$(VERSION)"
	@echo "3. Push the tag: git push origin v$(VERSION)"
	@echo "4. Attach APKs from app/build/outputs/apk/release/ to the GitHub release"
	@echo "5. Attach packaging/release-notes.md as the release description"

# Build and sign APK
sign: release
	@echo "Signing APK..."
	@echo "Note: Make sure keystore.properties is configured"
	$(GRADLE) $(GRADLE_OPTS) assembleRelease
	@echo "APK signed and ready for distribution!"

# Run lint checks
lint:
	@echo "Running lint checks..."
	$(GRADLE) $(GRADLE_OPTS) lintDebug
	@echo "Lint checks completed!"

# Run tests
test:
	@echo "Running tests..."
	$(GRADLE) $(GRADLE_OPTS) test
	@echo "Tests completed!"

# Run all checks
check: lint test
	@echo "All checks completed!"

# Generate documentation
docs:
	@echo "Generating documentation..."
	$(GRADLE) $(GRADLE_OPTS) javadoc
	@echo "Documentation generated in app/build/docs/javadoc/"

# Build and run on device
run: install
	@echo "Starting app..."
	adb shell am start -n com.jabook.app/.MainActivity

# Stop the app
stop:
	@echo "Stopping app..."
	adb shell am force-stop com.jabook.app

# Logcat
logcat:
	@echo "Showing logcat..."
	adb logcat | grep -i jabook

# Show connected devices
devices:
	@echo "Connected devices:"
	adb devices

# Build for CI
ci: clean check release
	@echo "CI build completed successfully!"

# Help
help:
	@echo "JaBook Build Commands:"
	@echo ""
	@echo "  debug          - Build debug APK"
	@echo "  release        - Build release APK"
	@echo "  apk            - Build per-ABI APKs"
	@echo "  splits         - Build universal APK"
	@echo "  aab            - Build Android App Bundle"
	@echo "  install        - Install debug APK to device"
	@echo "  clean          - Clean build artifacts"
	@echo "  release-notes  - Generate release notes"
	@echo "  github-release - Prepare GitHub release"
	@echo "  sign           - Build and sign APK"
	@echo "  lint           - Run lint checks"
	@echo "  test           - Run tests"
	@echo "  check          - Run all checks"
	@echo "  docs           - Generate documentation"
	@echo "  run            - Build, install and run app"
	@echo "  stop           - Stop the app"
	@echo "  logcat         - Show app logs"
	@echo "  devices        - Show connected devices"
	@echo "  ci             - Build for CI"
	@echo "  help           - Show this help message"
	@echo ""
	@echo "Examples:"
	@echo "  make debug          # Build debug version"
	@echo "  make release        # Build release version"
	@echo "  make splits         # Build universal APK"
	@echo "  make install        # Install and run"
	@echo "  make github-release # Prepare GitHub release"