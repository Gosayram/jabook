.PHONY: help analyze create-android build-apk

APP_NAME = jabook
ORG_NAME = com.jabook.app
PLATFORMS = android
ANDROID_LANG = kotlin

create-android:
	@echo "\033[0;32mИнициализация Android в текущем каталоге...\033[0m"
	@if [ ! -d android ]; then flutter create . --platforms=$(PLATFORMS) --org $(ORG_NAME) -a $(ANDROID_LANG); else echo "\033[1;33mandroid/ уже существует — пропускаю\033[0m"; fi

build-apk:
	@echo "\033[0;32mСборка APK...\033[0m"
	flutter pub get
	flutter build apk --release
	mkdir -p .bin
	cp build/app/outputs/flutter-apk/app-release.apk .bin/$(APP_NAME).apk
	@echo "\033[0;32mГотово: .bin/$(APP_NAME).apk\033[0m"