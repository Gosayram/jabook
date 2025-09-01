# Makefile for JaBook Flutter Application
# This file provides convenient commands for building and testing the app

# Variables
PROJECT_NAME = jabook
FLAVORS = dev stage prod
ANDROID_BUILD_VARIANTS = $(addsuffix $(FLAVOR), $(FLAVORS))
IOS_BUILD_VARIANTS = $(addprefix $(PROJECT_NAME)-, $(FLAVORS))

# Default target
.PHONY: help
help:
	@echo "JaBook Flutter Application - Build Commands"
	@echo "=========================================="
	@echo ""
	@echo "Development Commands:"
	@echo "  make install          - Install dependencies"
	@echo "  make clean            - Clean build artifacts"
	@echo "  make run-dev         - Run app in development mode"
	@echo "  make run-stage       - Run app in stage mode"
	@echo "  make run-prod        - Run app in production mode"
	@echo "  make setup-android    - Setup Android project configuration"
	@echo "  make setup-ios        - Setup iOS project configuration"
	@echo "  make setup           - Setup both Android and iOS projects"
	@echo ""
	@echo "Build Commands:"
	@echo "  make build-android-dev    - Build Android dev variant"
	@echo "  make build-android-stage  - Build Android stage variant"
	@echo "  make build-android-prod   - Build Android production variant"
	@echo "  make build-ios-dev        - Build iOS dev variant"
	@echo "  make build-ios-stage      - Build iOS stage variant"
	@echo "  make build-ios-prod       - Build iOS production variant"
	@echo ""
	@echo "Testing Commands:"
	@echo "  make test               - Run all tests"
	@echo "  make test-unit          - Run unit tests"
	@echo "  make test-widget        - Run widget tests"
	@echo "  make test-integration   - Run integration tests"
	@echo ""
	@echo "Analysis Commands:"
	@echo "  make analyze            - Run Flutter analysis"
	@echo "  make format             - Format code"
	@echo "  make lint               - Run linting"
	@echo ""
	@echo "Release Commands:"
	@echo "  make release-android    - Build all Android release variants"
	@echo "  make release-ios       - Build all iOS release variants"
	@echo "  make release           - Build all release variants"

# Development commands
.PHONY: install
install:
	flutter pub get

.PHONY: clean
clean:
	flutter clean
	rm -rf build/
	rm -rf .dart_tool/
	rm -rf ios/Pods/
	rm -rf ios/.symlinks/
	rm -rf ios/Flutter/Flutter.framework
	rm -rf ios/Flutter/Flutter.podspec
	@echo "Cleaned build artifacts"

.PHONY: run-dev
run-dev:
	flutter run --flavor dev --target lib/main.dart

.PHONY: run-stage
run-stage:
	flutter run --flavor stage --target lib/main.dart

.PHONY: run-prod
run-prod:
	flutter run --flavor prod --target lib/main.dart

# Android build commands
.PHONY: build-android-dev
build-android-dev:
	flutter build apk --flavor dev --target lib/main.dart --debug

.PHONY: build-android-stage
build-android-stage:
	flutter build apk --flavor stage --target lib/main.dart --release

.PHONY: build-android-prod
build-android-prod:
	flutter build apk --flavor prod --target lib/main.dart --release
	@echo "Android production APK built at: build/app/outputs/flutter-apk/app-prod-release.apk"

.PHONY: build-android-bundle
build-android-bundle:
	flutter build appbundle --flavor prod --target lib/main.dart --release
	@echo "Android App Bundle built at: build/app/outputs/bundle/app-prod-release.aab"

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

.PHONY: format
format:
	flutter format .

.PHONY: lint
lint:
	flutter pub run flutter_lints

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
	@echo "Building Android App Bundle..."
	make build-android-bundle
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
	flutter build apk --split-per-abi --release
	flutter pub run flutter_launcher_icons:main
	@echo "App size analysis complete. Check build/app/outputs/apk/"

# Version management
.PHONY: version
version:
	@echo "Current version: $$(grep version pubspec.yaml | cut -d' ' -f2)"

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
	make build-android-prod

.PHONY: build-ios
build-ios:
	make build-ios-prod

# Quick development cycle
.PHONY: dev
dev: clean install run-dev

# Production build
.PHONY: build
build: clean install release

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
	flutter build apk --flavor $(FLAVOR) --target lib/main.dart --release

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
	@echo "Open: flutter pub global run devtools

# Clean and fresh build
.PHONY: fresh
fresh: clean install

# Show help for specific target
.PHONY: help-%
help-%:
	@echo "Help for target $*: $(.TARGETS)"
	@echo "Description: $(.TARGETS)"