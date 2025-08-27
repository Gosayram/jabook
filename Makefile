# JaBook Build Makefile
# Use Gradle Wrapper for all operations

APP_MODULE := app

.PHONY: clean debug apk aab test lint logs splits help

help:  ## Show this help message
	@echo "JaBook Android App Build Commands:"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"; printf "%-20s %s\n", "Target", "Description"} /^[a-zA-Z_-]+:.*?##/ { printf "%-20s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

clean:  ## Clean all build artifacts
	./gradlew clean

debug:  ## Build debug APK
	./gradlew :$(APP_MODULE):assembleDebug

apk:    ## Build universal signed release APK
	./gradlew :$(APP_MODULE):assembleRelease

splits: ## Build per-ABI signed APKs (armeabi-v7a, arm64-v8a, x86, x86_64)
	./gradlew :$(APP_MODULE):assembleRelease

aab:    ## Build App Bundle for Google Play (optional)
	./gradlew :$(APP_MODULE):bundleRelease

test:   ## Run unit and instrumented tests
	./gradlew testDebugUnitTest connectedDebugAndroidTest

lint:   ## Run lint checks
	./gradlew :$(APP_MODULE):lint

logs:   ## Export logs (requires implementation in build.gradle)
	./gradlew :$(APP_MODULE):copyLogs

install-debug:  ## Install debug APK on connected device
	./gradlew :$(APP_MODULE):installDebug

install-release:  ## Install release APK on connected device
	./gradlew :$(APP_MODULE):installRelease

uninstall:  ## Uninstall app from connected device
	./gradlew :$(APP_MODULE):uninstallDebug

# Development helpers
run: debug install-debug  ## Build and install debug APK, then run on device

# GitHub Release helpers
release-prep: splits apk  ## Prepare artifacts for GitHub release
	@echo "GitHub Release Artifacts:"
	@echo "  - app/build/outputs/apk/release/app-release-armeabi-v7a.apk"
	@echo "  - app/build/outputs/apk/release/app-release-arm64-v8a.apk"
	@echo "  - app/build/outputs/apk/release/app-release-x86.apk"
	@echo "  - app/build/outputs/apk/release/app-release-x86_64.apk"
	@echo "  - app/build/outputs/apk/release/app-release-universal.apk"
	@echo "  - packaging/release-notes.md"