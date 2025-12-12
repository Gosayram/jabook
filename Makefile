# Makefile for Jabook Kotlin/Android Application
# Adapted from Flutter version - removed Flutter commands, kept Android/signing/utility commands

# Variables
PROJECT_NAME = jabook
FLAVORS = dev stage beta prod
SIGNING_SCRIPT = scripts/signing.sh
# Extract version from .release-version (format: version+build -> version)
VERSION = $(shell cat .release-version 2>/dev/null | cut -d+ -f1 || echo "0.0.1")
FULL_VERSION = $(shell cat .release-version 2>/dev/null || echo "0.0.1+1")
APK_DEST_DIR = $(HOME)/Downloads/Jabook

# Default target
.PHONY: help
help:
	@echo "Jabook Kotlin/Android Application - Build Commands"
	@echo "=================================================="
	@echo ""
	@echo "Development Commands:"
	@echo "  make clean                         - Clean build artifacts"
	@echo "  make compile                       - Compile Kotlin code (for syntax checking)"
	@echo "  make fmt-kotlin                    - Format Kotlin code (ktlint format)"
	@echo "  make lint-kotlin                   - Lint Kotlin code (ktlint check)"
	@echo ""
	@echo "Build Commands:"
	@echo "  make build-dev                     - Build Android dev debug APK"
	@echo "  make build-beta                    - Build Android beta release APK"
	@echo "  make build-prod                    - Build Android production release APK"
	@echo "  make build-bundle-prod             - Build Android production App Bundle (AAB)"
	@echo "  make build-signed-apk              - Build signed prod APKs (split + universal)"
	@echo "  make build-signed-apk-beta         - Build signed beta APKs (split + universal)"
	@echo ""
	@echo "Signing Commands:"
	@echo "  make sign-android                  - Generate signing keys and setup Android signing"
	@echo "  make use-existing-android-cert     - Use existing Android certificate"
	@echo "  make patch-gradle-signing          - Patch Gradle signing configuration"
	@echo ""
	@echo "Release Commands:"
	@echo "  make copy-apk                      - Copy prod APK files to ~/Downloads/Jabook"
	@echo "  make copy-apk-beta                 - Copy beta APK files to ~/Downloads/Jabook"
	@echo "  make build-and-copy                - Build prod APK and copy to Downloads"
	@echo "  make build-beta-and-copy           - Build beta APK and copy to Downloads"
	@echo "  make build-android-signed-apk-beta-copy - Alias for build-beta-and-copy"
	@echo ""
	@echo "Version Management:"
	@echo "  make version                       - Show current version from .release-version"
	@echo "  make update-version NEW_VERSION=x.y.z - Update version in .release-version"
	@echo ""
	@echo "Git Commands:"
	@echo "  make tag                           - Create git tag from current version"
	@echo "  make push-tag                      - Create tag and push to remote"
	@echo ""
	@echo "Utility Scripts (hack/):"
	@echo "  make changelog                     - Generate CHANGELOG.md"
	@echo "  make check-copyright               - Check copyright headers"
	@echo "  make update-copyright              - Update copyright headers"
	@echo "  make add-copyright                 - Add missing copyright headers"
	@echo "  make check-l10n                    - Check for l10n duplicates"
	@echo ""
	@echo "Device Commands:"
	@echo "  make run                           - Install and run APK on device"
	@echo "  make devices                       - List connected devices"
	@echo "  make logcat                        - Show logcat with jabook filter"
	@echo "  make clear-logcat                  - Clear logcat buffer"
	@echo "  make install-dev                   - Install dev APK to device"
	@echo "  make install-beta                  - Install beta APK to device"
	@echo "  make install-prod                  - Install prod APK to device"
	@echo "  make uninstall                     - Uninstall app from device"
	@echo ""
	@echo "Quick Workflows:"
	@echo "  make dev                           - Format + compile + install dev"
	@echo "  make beta                          - Format + compile + install beta"
	@echo "  make warnings                      - Count compiler warnings"
	@echo ""

# Clean build artifacts
.PHONY: clean
clean:
	@echo "Cleaning Android build artifacts..."
	@cd android && ./gradlew clean
	@rm -rf android/build android/app/build
	@rm -rf debug-info/
	@echo "✅ Cleaned build artifacts"

# Compile Kotlin code
.PHONY: compile
compile:
	@echo "Compiling Kotlin code for all flavors..."
	@(cd android && ./gradlew :app:compileDevDebugKotlin :app:compileBetaDebugKotlin :app:compileProdDebugKotlin --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Kotlin compilation successful for all flavors"; \
	else \
		echo "❌ Kotlin compilation failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

# Lint Kotlin code
.PHONY: lint-kotlin
lint-kotlin:
	@echo "Linting Kotlin code with ktlint..."
	@(cd android && ./gradlew :app:ktlintCheck --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Kotlin linting passed"; \
	else \
		echo "❌ Kotlin linting failed with exit code $$EXIT_CODE"; \
		echo "Run 'make fmt-kotlin' to auto-fix issues"; \
	fi; \
	exit $$EXIT_CODE

# Format Kotlin code
.PHONY: fmt-kotlin
fmt-kotlin:
	@echo "Formatting Kotlin code with ktlint..."
	@(cd android && ./gradlew :app:ktlintFormat --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Kotlin code formatted successfully"; \
	else \
		echo "❌ Kotlin formatting failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

# ========================================
# Build Commands
# ========================================

# Build dev debug APK
.PHONY: build-dev
build-dev:
	@echo "Building dev debug APK..."
	@cd android && ./gradlew :app:assembleDevDebug
	@echo "✅ Dev debug APK built at: android/app/build/outputs/apk/dev/debug/"

# Build beta release APK
.PHONY: build-beta
build-beta:
	@echo "Building beta release APK..."
	@cd android && ./gradlew :app:assembleBetaRelease
	@echo "✅ Beta release APK built at: android/app/build/outputs/apk/beta/release/"

# Build prod release APK
.PHONY: build-prod
build-prod:
	@echo "Building prod release APK..."
	@cd android && ./gradlew :app:assembleProdRelease
	@echo "✅ Prod release APK built at: android/app/build/outputs/apk/prod/release/"

# Build prod App Bundle (AAB)
.PHONY: build-bundle-prod
build-bundle-prod:
	@echo "Building prod App Bundle (AAB)..."
	@cd android && ./gradlew :app:bundleProdRelease
	@echo "✅ Prod AAB built at: android/app/build/outputs/bundle/prodRelease/"

# ========================================
# Signing Commands
# ========================================

.PHONY: sign-android
sign-android:
	@echo "Generating Android signing keys..."
	@if [ -f "$(SIGNING_SCRIPT)" ]; then \
		$(SIGNING_SCRIPT); \
		echo "Android signing configured successfully"; \
		echo "Patching Gradle configuration..."; \
		scripts/patch-gradle-signing.sh; \
		echo "Gradle configuration patched successfully"; \
	else \
		echo "Error: Signing script not found at $(SIGNING_SCRIPT)"; \
		echo "Please create .key-generate.conf from .key-generate.example.conf"; \
		exit 1; \
	fi

.PHONY: use-existing-android-cert
use-existing-android-cert:
	@echo "Using existing Android signing certificate..."
	@if [ -f "$(SIGNING_SCRIPT)" ]; then \
		if [ -f ".signing/release.keystore" ]; then \
			echo "Existing certificate found, updating configuration..."; \
			scripts/signing.sh; \
			echo "Android signing configuration updated with existing certificate"; \
		else \
			echo "Error: No existing certificate found in .signing/release.keystore"; \
			echo "Run 'make sign-android' first to generate a certificate"; \
			exit 1; \
		fi \
	else \
		echo "Error: Signing script not found at $(SIGNING_SCRIPT)"; \
		exit 1; \
	fi

.PHONY: patch-gradle-signing
patch-gradle-signing:
	@echo "Patching Gradle configuration..."
	@if [ -f "scripts/patch-gradle-signing.sh" ]; then \
		scripts/patch-gradle-signing.sh; \
		echo "Gradle configuration patched successfully"; \
	else \
		echo "Error: Patch script not found at scripts/patch-gradle-signing.sh"; \
		exit 1; \
	fi

# Build signed prod APKs (split per architecture + universal)
.PHONY: build-signed-apk
build-signed-apk: use-existing-android-cert patch-gradle-signing
	@echo "Building signed prod APKs..."
	@cd android && ./gradlew :app:assembleProdRelease
	@echo "✅ Signed prod APKs built at: android/app/build/outputs/apk/prod/release/"

# Build signed beta APKs (split per architecture + universal)
.PHONY: build-signed-apk-beta
build-signed-apk-beta: use-existing-android-cert patch-gradle-signing
	@echo "Building signed beta APKs..."
	@cd android && ./gradlew :app:assembleBetaRelease
	@echo "✅ Signed beta APKs built at: android/app/build/outputs/apk/beta/release/"

# ========================================
# Copy APK Commands
# ========================================

.PHONY: copy-apk
copy-apk:
	@echo "Copying prod APK files with version $(VERSION)..."
	@mkdir -p $(APK_DEST_DIR)
	@# Find and copy all prod release APKs
	@for apk in build/app/outputs/apk/prod/release/*.apk; do \
		if [ -f "$$apk" ]; then \
			filename=$$(basename "$$apk"); \
			newname="Jabook_$(VERSION)_$${filename#app-prod-release-}"; \
			cp -f "$$apk" "$(APK_DEST_DIR)/$$newname" && \
			echo "✅ Copied: $$newname"; \
		fi \
	done
	@echo "✅ Prod APK files copied to $(APK_DEST_DIR)/"


.PHONY: copy-apk-beta
copy-apk-beta:
	@echo "Copying beta APK files with version $(VERSION)..."
	@mkdir -p $(APK_DEST_DIR)
	@# Check if we have split APKs or universal APK
	@if [ -f "build/app/outputs/apk/beta/release/app-beta-release.apk" ]; then \
		cp -f "build/app/outputs/apk/beta/release/app-beta-release.apk" "$(APK_DEST_DIR)/Jabook_$(VERSION)_beta.apk" && \
		echo "✅ Copied: Jabook_$(VERSION)_beta.apk"; \
	else \
		for apk in build/app/outputs/apk/beta/release/app-beta-*.apk; do \
			if [ -f "$$apk" ]; then \
				filename=$$(basename "$$apk"); \
				arch=$${filename#app-beta-}; \
				arch=$${arch%.apk}; \
				newname="Jabook_$(VERSION)_beta_$$arch.apk"; \
				cp -f "$$apk" "$(APK_DEST_DIR)/$$newname" && \
				echo "✅ Copied: $$newname"; \
			fi \
		done \
	fi
	@echo "✅ Beta APK files copied to $(APK_DEST_DIR)/"


.PHONY: build-and-copy
build-and-copy: build-signed-apk copy-apk
	@echo "✅ Build and copy complete!"

.PHONY: build-beta-and-copy
build-beta-and-copy: build-signed-apk-beta copy-apk-beta
	@echo "✅ Beta build and copy complete!"

# Aliases matching old Makefile naming
.PHONY: build-signed-apk-copy
build-signed-apk-copy: build-and-copy

.PHONY: build-signed-apk-beta-copy
build-signed-apk-beta-copy: build-beta-and-copy

.PHONY: build-android-signed-apk-copy
build-android-signed-apk-copy: build-and-copy

.PHONY: build-android-signed-apk-beta-copy
build-android-signed-apk-beta-copy: build-beta-and-copy


# ========================================
# Version Management
# ========================================

.PHONY: version
version:
	@echo "Current version: $(VERSION)"
	@echo "Full version (with build): $(FULL_VERSION)"

.PHONY: update-version
update-version:
	@if [ -z "$(NEW_VERSION)" ]; then \
		hack/update-version.sh; \
	else \
		hack/update-version.sh "$(NEW_VERSION)"; \
	fi

# ========================================
# Git Commands
# ========================================

.PHONY: tag
tag:
	@if [ ! -f ".release-version" ]; then \
		echo "Error: .release-version file not found"; \
		exit 1; \
	fi
	@TAG_VERSION="$(VERSION)"; \
	if git rev-parse "$$TAG_VERSION" >/dev/null 2>&1; then \
		echo "Error: Tag $$TAG_VERSION already exists"; \
		exit 1; \
	fi; \
	echo "Creating tag $$TAG_VERSION..."; \
	git tag -a "$$TAG_VERSION" -m "Release $$TAG_VERSION"; \
	echo "✅ Tag $$TAG_VERSION created"

.PHONY: push-tag
push-tag: tag
	@TAG_VERSION="$(VERSION)"; \
	CURRENT_BRANCH=$$(git branch --show-current 2>/dev/null || echo ""); \
	REMOTE=$$(git config branch.$$CURRENT_BRANCH.remote 2>/dev/null || echo "origin"); \
	if [ -z "$$REMOTE" ] || [ "$$REMOTE" = "" ]; then \
		REMOTE="origin"; \
	fi; \
	echo "Pushing tag $$TAG_VERSION to $$REMOTE..."; \
	git push $$REMOTE "$$TAG_VERSION"; \
	echo "✅ Tag $$TAG_VERSION pushed to $$REMOTE"

# ========================================
# Device Commands
# ========================================

# Run app (install APK on connected device)
.PHONY: run
run:
	@APK_PATH="android/app/build/outputs/apk/dev/debug/app-dev-debug.apk"; \
	if [ ! -f "$$APK_PATH" ]; then \
		echo "Error: APK not found at $$APK_PATH"; \
		echo "Please build the APK first using: make build-dev"; \
		exit 1; \
	fi; \
	echo "Installing APK to connected device..."; \
	adb install -r "$$APK_PATH"; \
	echo "✅ APK installed successfully"

# List connected devices
.PHONY: devices
devices:
	@adb devices -l

# ========================================
# Utility Scripts (hack/)
# ========================================

# Generate CHANGELOG.md
.PHONY: changelog
changelog:
	@echo "Generating CHANGELOG.md..."
	@bash hack/generate-changelog.sh
	@echo "✅ CHANGELOG.md generated"

# Check copyright headers
.PHONY: check-copyright
check-copyright:
	@echo "Checking copyright headers..."
	@bash hack/check-copyright.sh

# Update copyright headers
.PHONY: update-copyright
update-copyright:
	@echo "Updating copyright headers..."
	@bash hack/update-copyright.sh
	@echo "✅ Copyright headers updated"

# Add missing copyright headers
.PHONY: add-copyright
add-copyright:
	@echo "Adding missing copyright headers..."
	@bash hack/add-copyright.sh
	@echo "✅ Copyright headers added"

# Check for l10n duplicates
.PHONY: check-l10n
check-l10n:
	@echo "Checking for l10n duplicates..."
	@bash hack/check-l10n-duplicates.sh

# ========================================
# Additional Android Development Commands
# ========================================

# Show logcat with filtering for jabook
.PHONY: logcat
logcat:
	@echo "Showing logcat (Ctrl+C to stop)..."
	@adb logcat | grep -i --color=auto -E "jabook|AndroidRuntime|FATAL"

# Clear logcat buffer
.PHONY: clear-logcat
clear-logcat:
	@echo "Clearing logcat buffer..."
	@adb logcat -c
	@echo "✅ Logcat cleared"

# Install dev APK to connected device
.PHONY: install-dev
install-dev:
	@echo "Installing dev debug APK to device..."
	@cd android && ./gradlew :app:installDevDebug --no-daemon
	@echo "✅ Dev APK installed"

# Install beta APK to connected device
.PHONY: install-beta
install-beta:
	@echo "Installing beta release APK on device..."
	@adb install -r build/app/outputs/apk/beta/release/app-beta-release.apk
	@echo "✅ Beta APK installed"

# Run beta on device - builds, installs, launches and shows logs
.PHONY: run-beta
run-beta: build-signed-apk-beta
	@echo "Installing and launching beta on device..."
	@adb install -r build/app/outputs/apk/beta/release/app-beta-arm64-v8a-release.apk
	@echo "Clearing logcat..."
	@adb logcat -c
	@echo "Launching Jabook Beta..."
	@adb shell am start -n com.jabook.app.jabook.beta/com.jabook.app.jabook.compose.ComposeMainActivity
	@echo ""
	@echo "📱 App launched! Watching logs (Ctrl+C to stop)..."
	@echo ""
	@adb logcat | grep -E "(Jabook|jabook|FATAL|AndroidRuntime|DEBUG|ComposeMainActivity)"

# Run beta debug - with verbose logging and startup profiling
.PHONY: run-beta-debug
run-beta-debug: build-signed-apk-beta
	@echo "Installing and launching beta with debug logging..."
	@adb install -r build/app/outputs/apk/beta/release/app-beta-arm64-v8a-release.apk
	@echo "Clearing logcat..."
	@adb logcat -c
	@echo "Launching Jabook Beta with debug flags..."
	@adb shell am start -n com.jabook.app.jabook.beta/com.jabook.app.jabook.compose.ComposeMainActivity
	@echo ""
	@echo "📱 App launched! Saving verbose logs to startup_profile.log..."
	@echo "Press Ctrl+C to stop logging"
	@echo ""
	@adb logcat > startup_profile.log

# Install prod APK to connected device
.PHONY: install-prod
install-prod:
	@echo "Installing prod release APK to device..."
	@cd android && ./gradlew :app:installProdRelease --no-daemon
	@echo "✅ Prod APK installed"

# Uninstall app from device
.PHONY: uninstall
uninstall:
	@echo "Uninstalling app from device..."
	@adb uninstall com.jabook.app.jabook || echo "App not installed"
	@echo "✅ App uninstalled"

# Compile specific flavor only
.PHONY: compile-dev
compile-dev:
	@echo "Compiling dev flavor..."
	@cd android && ./gradlew :app:compileDevDebugKotlin --no-daemon
	@echo "✅ Dev flavor compiled"

.PHONY: compile-beta
compile-beta:
	@echo "Compiling beta flavor..."
	@cd android && ./gradlew :app:compileBetaDebugKotlin --no-daemon
	@echo "✅ Beta flavor compiled"

.PHONY: compile-prod
compile-prod:
	@echo "Compiling prod flavor..."
	@cd android && ./gradlew :app:compileProdDebugKotlin --no-daemon
	@echo "✅ Prod flavor compiled"

# Quick development workflow: format, compile, install dev
.PHONY: dev
dev: fmt-kotlin compile-dev install-dev
	@echo "✅ Dev workflow complete - app installed and ready to run"

# Quick beta testing workflow
.PHONY: beta
beta: fmt-kotlin compile-beta install-beta
	@echo "✅ Beta workflow complete - app installed and ready to test"

# Show warnings count
.PHONY: warnings
warnings:
	@echo "Checking for compiler warnings..."
	@cd android && ./gradlew :app:compileDevDebugKotlin --no-daemon 2>&1 | grep "^w:" | wc -l | xargs -I{} echo "Found {} warnings"