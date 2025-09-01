# Makefile для Flutter проекта JaBook

.PHONY: help analyze create-android check-keys

APP_NAME = jabook
ORG_NAME = com.jabook.app
PLATFORMS = android
ANDROID_LANG = kotlin
PROJECT_DIR = $(APP_NAME)

GREEN = \033[0;32m
YELLOW = \033[1;33m
RED = \033[0;31m
NC = \033[0m # No Color

help:
	@echo "$(GREEN)Доступные команды для Flutter проекта JaBook:$(NC)"
	@echo ""
	@echo "$(YELLOW)make analyze$(NC)           - Запустить flutter analyze"
	@echo "$(YELLOW)make create-android$(NC)    - Создать Android проект: $(APP_NAME)"
	@echo "$(YELLOW)make check-keys$(NC)       - Проверить ключи подписи (если есть $(PROJECT_DIR))"
	@echo ""
	@echo "$(GREEN)Переменные для настройки:$(NC)"
	@echo "APP_NAME = $(APP_NAME)"
	@echo "ORG_NAME = $(ORG_NAME)"
	@echo "PLATFORMS = $(PLATFORMS)"
	@echo "ANDROID_LANG = $(ANDROID_LANG)"
	@echo "KEYSTORE_PATH = $(KEYSTORE_PATH)"
	@echo "KEYSTORE_ALIAS = $(KEYSTORE_ALIAS)"

analyze:
	@echo "$(GREEN)Запуск flutter analyze...$(NC)"
	flutter analyze
	@echo "$(GREEN)Анализ завершен$(NC)"

create-android:
	@echo "$(GREEN)Создание Android проекта: $(APP_NAME)...$(NC)"
	flutter create $(PROJECT_DIR) --org $(ORG_NAME) --platforms=$(PLATFORMS) -a $(ANDROID_LANG)
	@echo "$(GREEN)Android проект создан: $(PROJECT_DIR)$(NC)"

build-apk:
	@echo "$(GREEN)Сборка APK для $(APP_NAME)...$(NC)"
	cd $(PROJECT_DIR) && flutter pub get && flutter build apk --release
	@echo "$(GREEN)APK собран: $(PROJECT_DIR)/build/app/outputs/flutter-apk/app-release.apk$(NC)"
	mkdir -p .bin
	mv $(PROJECT_DIR)/build/app/outputs/flutter-apk/app-release.apk .bin/jabook.apk
