# Makefile for JaBook Flutter Application
# This file provides convenient commands for building and testing the app

# Variables
PROJECT_NAME = jabook
FLAVORS = dev stage beta prod
ANDROID_BUILD_VARIANTS = $(addsuffix $(FLAVOR), $(FLAVORS))
IOS_BUILD_VARIANTS = $(addprefix $(PROJECT_NAME)-, $(FLAVORS))
SIGNING_SCRIPT = scripts/signing.sh
PUBSPEC_FILE = pubspec.yaml
VERSION = $(shell grep "^version:" $(PUBSPEC_FILE) | sed 's/version:[[:space:]]*//' | cut -d+ -f1)
FULL_VERSION = $(shell grep "^version:" $(PUBSPEC_FILE) | sed 's/version:[[:space:]]*//')
APK_DEST_DIR = ~/Downloads/Jabook

# Default target
.PHONY: help
help:
	@echo "JaBook Flutter Application - Build Commands"
	@echo "=========================================="
	@echo ""
	@echo "Development Commands:"
	@echo "  make install                       - Install dependencies"
	@echo "  make clean                         - Clean build artifacts"
	@echo "  make run                           - Run built APK (build/app/outputs/apk/release/app-arm64-v8a-release.apk)"
	@echo "  make run-profile                   - Run app with profiling (output to startup_profile.log)"
	@echo "  make setup-android                 - Setup Android project configuration"
	@echo "  make setup-ios                     - Setup iOS project configuration"
	@echo "  make setup                         - Setup both Android and iOS projects"
	@echo "  make sign-android                  - Generate signing keys and setup Android signing"
	@echo "  make use-existing-android-cert     - Use existing Android certificate without regeneration"
	@echo ""
	@echo "Build Commands:"
	@echo "  make build-android-dev             - Build Android dev variant"
	@echo "  make build-android-stage           - Build Android stage variant"
	@echo "  make build-android-beta            - Build Android beta variant"
	@echo "  make build-android-prod            - Build Android production variant"
	@echo "  make build-ios-dev                 - Build iOS dev variant"
	@echo "  make build-ios-stage               - Build iOS stage variant"
	@echo "  make build-ios-prod                - Build iOS production variant"
	@echo "  make build-android-signed-apk      - Build signed prod APKs (split per architecture + universal)"
	@echo "  make build-android-signed-apk-beta - Build signed beta APKs (split per architecture + universal)"
	@echo ""
	@echo "Testing Commands:"
	@echo "  make test                          - Run all tests"
	@echo "  make test-unit                     - Run unit tests"
	@echo "  make test-widget                   - Run widget tests"
	@echo "  make test-integration              - Run integration tests"
	@echo ""
	@echo "Analysis Commands:"
	@echo "  make analyze                       - Run Flutter analysis"
	@echo "  make fmt                           - Format code"
	@echo "  make lint                          - Run linting"
	@echo "  make l10n                          - Generate localization files (flutter gen-l10n)"
	@echo "  make check-l10n         			- Check for duplicate keys in ARB files"
	@echo "  make analyze-size                  - Analyze APK size with detailed breakdown"
	@echo ""
	@echo "Maintenance Commands:"
	@echo "  make update-version [NEW_VERSION=1.2.1] - Update version in pubspec.yaml (SemVer style, patch 0-9)"
	@echo "  make update-copyright                 - Update copyright year in all files"
	@echo "  make check-copyright                  - Check copyright headers in Dart files"
	@echo "  make add-copyright                   - Add copyright headers to Dart files"
	@echo "  make changelog                       - Generate CHANGELOG.md from git commits"
	@echo "  make tag                             - Create git tag from current version"
	@echo "  make push-tag                        - Create tag and push to remote repository"
	@echo "  make release-tag                    - Create release: update changelog, create tag and push"
	@echo ""
	@echo "Release Commands:"
	@echo "  make release-android               - Build all signed Android release variants"
	@echo "  make release-ios                   - Build all iOS release variants"
	@echo "  make release                       - Build all release variants"
	@echo "  make copy-apk                      - Copy APK files to ~/Downloads/Jabook with version in filename"
	@echo "  make build-android-signed-apk-copy - Build signed prod APK and copy to ~/Downloads/Jabook with version"
	@echo "  make build-android-signed-apk-beta-copy - Build signed beta APK and copy to ~/Downloads/Jabook with version"

# Development commands
.PHONY: install
install:
	flutter pub get

.PHONY: clean
clean:
	rm -rf build/
	rm -rf debug-info/
	@echo "Cleaned build artifacts"

.PHONY: run
run:
	@APK_PATH="build/app/outputs/apk/release/app-arm64-v8a-release.apk"; \
	if [ ! -f "$$APK_PATH" ]; then \
		echo "Error: APK not found at $$APK_PATH"; \
		echo "Please build the APK first using: make build-android-signed-apk"; \
		exit 1; \
	fi
	flutter run \
		--use-application-binary=build/app/outputs/apk/release/app-arm64-v8a-release.apk \
		--verbose \
		--profile \
		--trace-startup \
		--device-timeout=30

.PHONY: run-profile
run-profile:
	@echo "Running app with profiling (output saved to startup_profile.log)..."
	@echo "Note: DevTools can be accessed via 'flutter pub global run devtools' if needed"
	flutter run --flavor dev --verbose --trace-startup --profile > startup_profile.log 2>&1
	@echo "Profile run complete. Check startup_profile.log for details."

# Android build commands
.PHONY: build-android-dev
build-android-dev:
	flutter build apk --target lib/main.dart --debug

.PHONY: build-android-stage
build-android-stage:
	flutter build apk --target lib/main.dart --release \
		--obfuscate \
		--split-debug-info=./debug-info \
		--split-per-abi \
		--tree-shake-icons
	@echo "Android stage APK built with optimizations at: build/app/outputs/apk/"

.PHONY: build-android-beta
build-android-beta:
	flutter build apk --flavor beta --target lib/main.dart --release \
		--split-per-abi \
		--tree-shake-icons
	@echo "Android beta APK built at: build/app/outputs/apk/"

.PHONY: build-android-prod
build-android-prod:
	flutter build apk --target lib/main.dart --release \
		--obfuscate \
		--split-debug-info=./debug-info \
		--split-per-abi \
		--tree-shake-icons
	@echo "Android production APK built with optimizations at: build/app/outputs/apk/"

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

.PHONY: build-android-bundle
build-android-bundle:
	@if [ ! -f "android/key.properties" ]; then \
		echo "Warning: android/key.properties not found, building unsigned bundle"; \
		flutter build appbundle --target lib/main.dart --release \
			--obfuscate \
			--split-debug-info=./debug-info; \
		echo "Android App Bundle built with optimizations at: build/app/outputs/bundle/release/app-release.aab"; \
	else \
		flutter build appbundle --target lib/main.dart --release \
			--obfuscate \
			--split-debug-info=./debug-info; \
		echo "Android App Bundle built with optimizations at: build/app/outputs/bundle/release/app-release.aab"; \
	fi

.PHONY: build-android-signed
build-android-signed: use-existing-android-cert patch-gradle-signing build-android-bundle
	@echo "Signed Android App Bundle built successfully"

.PHONY: build-android-signed-apk
build-android-signed-apk: use-existing-android-cert patch-gradle-signing
	@echo "Building signed prod APK (with obfuscation for production)..."
	@echo "Building split APKs per architecture..."
	flutter build apk --flavor prod --target lib/main.dart --release \
		--dart-define=FLAVOR=prod \
		--obfuscate \
		--split-debug-info=./debug-info \
		--split-per-abi \
		--tree-shake-icons
	@echo "Building universal APK (all architectures)..."
	flutter build apk --flavor prod --target lib/main.dart --release \
		--dart-define=FLAVOR=prod \
		--obfuscate \
		--split-debug-info=./debug-info \
		--tree-shake-icons
	@echo "✅ Signed prod APKs built at: build/app/outputs/apk/"
	@echo "   - Split APKs: app-*-release.apk (per architecture)"
	@echo "   - Universal APK: app-release.apk (all architectures)"
	@echo "   - Debug symbols saved to: ./debug-info/"

.PHONY: build-android-signed-apk-beta
build-android-signed-apk-beta: use-existing-android-cert patch-gradle-signing
	@echo "Building signed beta APK (with obfuscation for testing)..."
	@echo "Building split APKs per architecture..."
	flutter build apk --flavor beta --target lib/main.dart --release \
		--obfuscate \
		--split-debug-info=./debug-info \
		--split-per-abi \
		--tree-shake-icons
	@echo "Building universal APK (all architectures)..."
	flutter build apk --flavor beta --target lib/main.dart --release \
		--obfuscate \
		--split-debug-info=./debug-info \
		--tree-shake-icons
	@echo "✅ Signed beta APKs built at: build/app/outputs/apk/"
	@echo "   - Split APKs: app-*-release.apk (per architecture)"
	@echo "   - Universal APK: app-release.apk (all architectures)"
	@echo "   - Debug symbols saved to: ./debug-info/"

.PHONY: copy-apk
copy-apk:
	@echo "Copying APK files with version $(VERSION) (without build number)..."
	@mkdir -p $(APK_DEST_DIR)
	@echo "Copying split APKs (per architecture)..."
	@if [ -f "build/app/outputs/flutter-apk/app-x86_64-prod-release.apk" ]; then \
		cp -f build/app/outputs/flutter-apk/app-x86_64-prod-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_x86_64.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_x86_64.apk"; \
	elif [ -f "build/app/outputs/apk/release/app-x86_64-prod-release.apk" ]; then \
		cp -f build/app/outputs/apk/release/app-x86_64-prod-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_x86_64.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_x86_64.apk"; \
	else \
		echo "⚠️  Warning: app-x86_64-prod-release.apk not found"; \
	fi
	@if [ -f "build/app/outputs/flutter-apk/app-arm64-v8a-prod-release.apk" ]; then \
		cp -f build/app/outputs/flutter-apk/app-arm64-v8a-prod-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_v8a.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_v8a.apk"; \
	elif [ -f "build/app/outputs/apk/release/app-arm64-v8a-prod-release.apk" ]; then \
		cp -f build/app/outputs/apk/release/app-arm64-v8a-prod-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_v8a.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_v8a.apk"; \
	else \
		echo "⚠️  Warning: app-arm64-v8a-prod-release.apk not found"; \
	fi
	@if [ -f "build/app/outputs/flutter-apk/app-armeabi-v7a-prod-release.apk" ]; then \
		cp -f build/app/outputs/flutter-apk/app-armeabi-v7a-prod-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_v7a.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_v7a.apk"; \
	elif [ -f "build/app/outputs/apk/release/app-armeabi-v7a-prod-release.apk" ]; then \
		cp -f build/app/outputs/apk/release/app-armeabi-v7a-prod-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_v7a.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_v7a.apk"; \
	else \
		echo "⚠️  Warning: app-armeabi-v7a-prod-release.apk not found"; \
	fi
	@echo "Copying universal APK (all architectures)..."
	@if [ -f "build/app/outputs/flutter-apk/app-prod-release.apk" ]; then \
		cp -f build/app/outputs/flutter-apk/app-prod-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_universal.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_universal.apk"; \
	elif [ -f "build/app/outputs/apk/release/app-prod-release.apk" ]; then \
		cp -f build/app/outputs/apk/release/app-prod-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_universal.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_universal.apk"; \
	else \
		echo "⚠️  Warning: app-prod-release.apk (universal) not found"; \
	fi
	@echo "✅ APK files copied to $(APK_DEST_DIR)/"

.PHONY: build-android-signed-apk-copy
build-android-signed-apk-copy: build-android-signed-apk copy-apk
	@echo "✅ Build and copy complete!"

.PHONY: copy-apk-beta
copy-apk-beta:
	@echo "Copying beta APK files with version $(VERSION) (without build number)..."
	@mkdir -p $(APK_DEST_DIR)
	@echo "Copying split APKs (per architecture)..."
	@if [ -f "build/app/outputs/flutter-apk/app-x86_64-beta-release.apk" ]; then \
		cp -f build/app/outputs/flutter-apk/app-x86_64-beta-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_beta_x86_64.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_beta_x86_64.apk"; \
	elif [ -f "build/app/outputs/apk/release/app-x86_64-beta-release.apk" ]; then \
		cp -f build/app/outputs/apk/release/app-x86_64-beta-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_beta_x86_64.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_beta_x86_64.apk"; \
	else \
		echo "⚠️  Warning: app-x86_64-beta-release.apk not found"; \
	fi
	@if [ -f "build/app/outputs/flutter-apk/app-arm64-v8a-beta-release.apk" ]; then \
		cp -f build/app/outputs/flutter-apk/app-arm64-v8a-beta-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_beta_v8a.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_beta_v8a.apk"; \
	elif [ -f "build/app/outputs/apk/release/app-arm64-v8a-beta-release.apk" ]; then \
		cp -f build/app/outputs/apk/release/app-arm64-v8a-beta-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_beta_v8a.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_beta_v8a.apk"; \
	else \
		echo "⚠️  Warning: app-arm64-v8a-beta-release.apk not found"; \
	fi
	@if [ -f "build/app/outputs/flutter-apk/app-armeabi-v7a-beta-release.apk" ]; then \
		cp -f build/app/outputs/flutter-apk/app-armeabi-v7a-beta-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_beta_v7a.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_beta_v7a.apk"; \
	elif [ -f "build/app/outputs/apk/release/app-armeabi-v7a-beta-release.apk" ]; then \
		cp -f build/app/outputs/apk/release/app-armeabi-v7a-beta-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_beta_v7a.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_beta_v7a.apk"; \
	else \
		echo "⚠️  Warning: app-armeabi-v7a-beta-release.apk not found"; \
	fi
	@echo "Copying universal APK (all architectures)..."
	@if [ -f "build/app/outputs/flutter-apk/app-beta-release.apk" ]; then \
		cp -f build/app/outputs/flutter-apk/app-beta-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_beta_universal.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_beta_universal.apk"; \
	elif [ -f "build/app/outputs/apk/release/app-beta-release.apk" ]; then \
		cp -f build/app/outputs/apk/release/app-beta-release.apk $(APK_DEST_DIR)/Jabook_$(VERSION)_beta_universal.apk && \
		echo "✅ Copied: Jabook_$(VERSION)_beta_universal.apk"; \
	else \
		echo "⚠️  Warning: app-beta-release.apk (universal) not found"; \
	fi
	@echo "✅ Beta APK files copied to $(APK_DEST_DIR)/"

.PHONY: build-android-signed-apk-beta-copy
build-android-signed-apk-beta-copy: build-android-signed-apk-beta copy-apk-beta
	@echo "✅ Build and copy complete!"

.PHONY: build-android-debug-apk
build-android-debug-apk: use-existing-android-cert patch-gradle-signing
	@echo "Building signed debug APK (no obfuscation for easier debugging)..."
	flutter build apk --target lib/main.dart --release \
		--split-per-abi \
		--tree-shake-icons
	@echo "Signed debug APK built at: build/app/outputs/apk/"

# iOS build commands
.PHONY: build-ios-dev
build-ios-dev:
	flutter build ios --flavor dev --target lib/main.dart --debug

.PHONY: build-ios-stage
build-ios-stage:
	flutter build ios --flavor stage --target lib/main.dart --release

.PHONY: build-ios-prod
build-ios-prod:
	flutter build ios --flavor prod --target lib/main.dart --release
	@echo "iOS production build complete. Check ios/Runner/ directory."

# Testing commands
.PHONY: test
test:
	flutter test

.PHONY: test-unit
test-unit:
	flutter test --test=lib/**/*.test.dart

.PHONY: test-widget
test-widget:
	flutter test --test=lib/**/*widget_test.dart

.PHONY: test-integration
test-integration:
	flutter test --test=integration/

# Analysis commands
.PHONY: analyze
analyze:
	flutter analyze

.PHONY: l10n
l10n:
	@echo "Generating localization files via flutter gen-l10n..."
	flutter gen-l10n
	@echo "Localization files generated in lib/l10n/"

.PHONY: check-l10n
check-l10n:
	@if [ -f "hack/check-l10n-duplicates.sh" ]; then \
		hack/check-l10n-duplicates.sh; \
	else \
		echo "Error: hack/check-l10n-duplicates.sh not found"; \
		exit 1; \
	fi

.PHONY: fmt
fmt:
	@find . -name "*.dart" \
		-not -path "./lib/l10n/*" \
		-not -path "./build/*" \
		-not -path "./.dart_tool/*" \
		-not -path "./.git/*" \
		-not -path "./android/*" \
		-not -path "./ios/*" \
		-not -path "./web/*" | xargs dart format || true

.PHONY: lint
lint:
	flutter analyze

# Release commands
.PHONY: release-android
release-android:
	@echo "Building Android release variants..."
	@echo "Building dev variant..."
	make build-android-dev
	@echo "Building stage variant..."
	make build-android-stage
	@echo "Building production variant..."
	make build-android-prod
	@echo "Building signed Android App Bundle..."
	make build-android-signed
	@echo "Android release builds complete!"

.PHONY: release-ios
release-ios:
	@echo "Building iOS release variants..."
	@echo "Building dev variant..."
	make build-ios-dev
	@echo "Building stage variant..."
	make build-ios-stage
	@echo "Building production variant..."
	make build-ios-prod
	@echo "iOS release builds complete!"

.PHONY: release
release:
	@echo "Building all release variants..."
	make release-android
	make release-ios
	@echo "All release builds complete!"

# Code generation
.PHONY: generate
generate:
	flutter pub run build_runner build --delete-conflicting-outputs

# Documentation
.PHONY: docs
docs:
	@echo "Generating documentation..."
	flutter pub run dartdoc
	@echo "Documentation generated in doc/api/"

# Size analysis
.PHONY: size
size:
	flutter build apk --target lib/main.dart --release \
		--split-per-abi \
		--tree-shake-icons
	flutter pub run flutter_launcher_icons:main
	@echo "App size analysis complete. Check build/app/outputs/apk/"
	@echo "Note: Use 'make analyze-size' for detailed size breakdown"

# Analyze APK size (detailed breakdown)
# Note: --analyze-size cannot be used with --split-debug-info
.PHONY: analyze-size
analyze-size:
	@echo "Building APK with size analysis..."
	flutter build apk --target lib/main.dart --release \
		--analyze-size \
		--split-per-abi \
		--tree-shake-icons
	@echo "Size analysis complete. Check the output above for detailed breakdown."

# Version management
.PHONY: version
version:
	@echo "Current version: $$(grep version pubspec.yaml | cut -d' ' -f2)"

# Project maintenance commands
.PHONY: update-version
update-version:
	@if [ -z "$(NEW_VERSION)" ]; then \
		hack/update-version.sh; \
	else \
		hack/update-version.sh "$(NEW_VERSION)"; \
	fi

.PHONY: update-copyright
update-copyright:
	@echo "Updating copyright year in all files..."
	hack/update-copyright.sh

.PHONY: check-copyright
check-copyright:
	@echo "Checking copyright headers in Dart files..."
	hack/check-copyright.sh

.PHONY: add-copyright
add-copyright:
	@echo "Adding copyright headers to Dart files..."
	hack/add-copyright.sh

.PHONY: changelog
changelog:
	@if [ -z "$(OUTPUT)" ]; then \
		hack/generate-changelog.sh; \
	else \
		hack/generate-changelog.sh "$(OUTPUT)"; \
	fi

# Git tagging commands
.PHONY: tag
tag:
	@if [ ! -f "$(PUBSPEC_FILE)" ]; then \
		echo "Error: $(PUBSPEC_FILE) not found"; \
		exit 1; \
	fi
	@TAG_VERSION="v$(VERSION)"; \
	if git rev-parse "$$TAG_VERSION" >/dev/null 2>&1; then \
		echo "Error: Tag $$TAG_VERSION already exists"; \
		exit 1; \
	fi; \
	echo "Creating tag $$TAG_VERSION..."; \
	git tag -a "$$TAG_VERSION" -m "Release $$TAG_VERSION"; \
	echo "✅ Tag $$TAG_VERSION created"

.PHONY: push-tag
push-tag: tag
	@TAG_VERSION="v$(VERSION)"; \
	CURRENT_BRANCH=$$(git branch --show-current 2>/dev/null || echo ""); \
	REMOTE=$$(git config branch.$$CURRENT_BRANCH.remote 2>/dev/null || echo "origin"); \
	if [ -z "$$REMOTE" ] || [ "$$REMOTE" = "" ]; then \
		REMOTE="origin"; \
	fi; \
	echo "Pushing tag $$TAG_VERSION to $$REMOTE..."; \
	git push $$REMOTE "$$TAG_VERSION"; \
	echo "✅ Tag $$TAG_VERSION pushed to $$REMOTE"

.PHONY: release-tag
release-tag: changelog push-tag
	@echo "✅ Release $(VERSION) created and pushed"

# Update dependencies
.PHONY: update-deps
update-deps:
	flutter pub upgrade
	flutter pub deps --style=tree

# Check for outdated packages
.PHONY: outdated
outdated:
	flutter pub outdated

# Install dependencies for specific platforms
.PHONY: install-android
install-android:
	cd android && ./gradlew clean

.PHONY: install-ios
install-ios:
	cd ios && pod install

# Project setup commands
.PHONY: setup-android
setup-android:
	@echo "Setting up Android project configuration..."
	flutter create . --org com.jabook.app --platforms=android -a kotlin
	@echo "Generating custom launcher icons..."
	dart run flutter_launcher_icons:main
	@echo "Android project setup complete!"

.PHONY: setup-ios
setup-ios:
	@echo "Setting up iOS project configuration..."
	flutter create . --org com.jabook.app --platforms=ios
	@echo "iOS project setup complete!"

.PHONY: setup
setup: setup-android setup-ios
	@echo "Project setup complete for both platforms!"

# Platform-specific builds
.PHONY: build-android
build-android:
	make build-android-signed

.PHONY: build-ios
build-ios:
	make build-ios-prod

# Quick development cycle
.PHONY: dev
dev: clean install build-android-prod run

# Production build
.PHONY: build
build: clean install sign-android release

# Check code quality
.PHONY: check
check: analyze test lint format

# Generate icons
.PHONY: icons
icons:
	flutter pub run flutter_launcher_icons:main

# Generate localized strings
.PHONY: localize
localize:
	flutter pub run intl_translation:extract_to_arb --output-dir=lib/l10n
	flutter pub run intl_translation:generate_from_arb --output-dir=lib/l10n lib/l10n/app_en.arb

# Build and run on specific device
.PHONY: run-device
run-device:
	flutter run -d $(DEVICE) --flavor dev --target lib/main.dart

# List available devices
.PHONY: devices
devices:
	flutter devices

# Profile mode build
.PHONY: build-profile
build-profile:
	flutter build apk --flavor prod --target lib/main.dart --profile

# Hot reload development
.PHONY: hot-reload
hot-reload:
	flutter run --hot --flavor dev --target lib/main.dart

# Build with specific flavor
.PHONY: build-flavor
build-flavor:
	@if [ -z "$(FLAVOR)" ]; then \
		echo "Usage: make build-flavor FLAVOR=dev|stage|prod"; \
		exit 1; \
	fi
	flutter build apk --flavor $(FLAVOR) --target lib/main.dart --release \
		--obfuscate \
		--split-debug-info=./debug-info \
		--split-per-abi \
		--tree-shake-icons
	@echo "Android $(FLAVOR) APK built with optimizations at: build/app/outputs/apk/"

# Run tests with coverage
.PHONY: test-coverage
test-coverage:
	flutter test --coverage
	genhtml coverage/lcov.info -o coverage/html
	@echo "Coverage report generated in coverage/html/"

# Upload to app stores (placeholder)
.PHONY: upload-android
upload-android:
	@echo "Android upload not implemented yet"
	@echo "You would typically use Google Play Console or Fastlane"

.PHONY: upload-ios
upload-ios:
	@echo "iOS upload not implemented yet"
	@echo "You would typically use App Store Connect or Fastlane"

# Security check
.PHONY: security
security:
	@echo "Running security checks..."
	flutter pub run dart_code_metrics:metrics lib/
	@echo "Security checks complete"

# Performance analysis
.PHONY: perf
perf:
	@echo "Building for performance analysis..."
	flutter build apk --profile --split-per-abi
	@echo "Run the app on a device and use Flutter DevTools for analysis"
	@echo "Open: flutter pub global run devtools"

# Clean and fresh build
.PHONY: fresh
fresh: clean install

# Show help for specific target
.PHONY: help-%
help-%:
	@echo "Help for target $*: $(.TARGETS)"
	@echo "Description: $(.TARGETS)"