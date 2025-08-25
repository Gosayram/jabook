# Makefile для сборки подписанного APK приложения JaBook
# Использует keystore.properties для хранения секретных данных

.PHONY: help clean debug release install debug-install release-install

# Переменные
APP_NAME = JaBook
GRADLE_WRAPPER = ./gradlew
PROPERTIES_FILE = keystore.properties
KEYSTORE_FILE = keystore/jabook-release.jks
APK_OUTPUT_DIR = app/build/outputs/apk/release
APK_NAME = $(APP_NAME)-release.apk
SIGNED_APK = $(APK_OUTPUT_DIR)/$(APK_NAME)
AAB_OUTPUT_DIR = app/build/outputs/bundle/release
AAB_NAME = $(APP_NAME)-release.aab
SIGNED_AAB = $(AAB_OUTPUT_DIR)/$(AAB_NAME)

# Цвета для вывода
GREEN = \033[0;32m
YELLOW = \033[1;33m
RED = \033[0;31m
NC = \033[0m # No Color

# По умолчанию
help:
	@echo "$(GREEN)Доступные команды для сборки JaBook:$(NC)"
	@echo ""
	@echo "$(YELLOW)make clean$(NC)        - Очистить сборочные файлы"
	@echo "$(YELLOW)make debug$(NC)        - Собрать debug APK"
	@echo "$(YELLOW)make release$(NC)      - Собрать release APK (подписанный)"
	@echo "$(YELLOW)make install$(NC)      - Установить debug APK на устройство"
	@echo "$(YELLOW)make release-install$(NC) - Установить release APK на устройство"
	@echo ""
	@echo "$(GREEN)Требования:$(NC)"
	@echo "1. Java JDK 17+"
	@echo "2. Android SDK"
	@echo "3. Подключенное устройство или эмулятор"
	@echo ""
	@echo "$(GREEN)Файл конфигурации:$(NC) $(PROPERTIES_FILE)"
	@echo "$(GREEN)Выходной APK:$(NC) $(SIGNED_APK)"

# Проверка наличия необходимых файлов
check-config:
	@if [ ! -f "$(PROPERTIES_FILE)" ]; then \
		echo "$(RED)Ошибка: Файл $(PROPERTIES_FILE) не найден!$(NC)"; \
		exit 1; \
	fi
	@if [ ! -f "$(KEYSTORE_FILE)" ]; then \
		echo "$(YELLOW)Предупреждение: Файл $(KEYSTORE_FILE) не найден. APK будет собран без подписи.$(NC)"; \
	fi

# Очистка
clean:
	@echo "$(GREEN)Очистка сборочных файлов...$(NC)"
	$(GRADLE_WRAPPER) clean
	@echo "$(GREEN)Очистка завершена$(NC)"

# Сборка debug APK
debug: check-config
	@echo "$(GREEN)Сборка debug APK...$(NC)"
	$(GRADLE_WRAPPER) assembleDebug
	@echo "$(GREEN)Debug APK готов: app/build/outputs/apk/debug/app-debug.apk$(NC)"

# Сборка release APK (подписанный)
release: check-config
	@echo "$(GREEN)Сборка release APK...$(NC)"
	@if [ ! -f "$(PROPERTIES_FILE)" ]; then \
		echo "$(RED)Ошибка: Файл $(PROPERTIES_FILE) не найден!$(NC)"; \
		exit 1; \
	fi
	$(GRADLE_WRAPPER) assembleRelease
	@echo "$(GREEN)Release APK готов: app/build/outputs/apk/release/app-release.apk$(NC)"

# Сборка и подписание release APK
signed-release: check-config
	@echo "$(GREEN)Сборка и подписание release AAB...$(NC)"
	@if [ ! -f "$(PROPERTIES_FILE)" ]; then \
		echo "$(RED)Ошибка: Файл $(PROPERTIES_FILE) не найден!$(NC)"; \
		exit 1; \
	fi
	@if [ ! -f "$(KEYSTORE_FILE)" ]; then \
		echo "$(YELLOW)Предупреждение: Файл $(KEYSTORE_FILE) не найден.$(NC)"; \
		echo "$(YELLOW)Используется debug подпись.$(NC)"; \
	fi
	@echo "$(GREEN)Используется keystore: $(KEYSTORE_FILE)$(NC)"
	$(GRADLE_WRAPPER) bundleRelease
	@echo "$(GREEN)Подписанный AAB готов: $(SIGNED_AAB)$(NC)"
	@echo "$(YELLOW)Для создания APK из AAB используйте:$(NC)"
	@echo "  bundletool build-apk --bundle $(SIGNED_AAB) --output $(SIGNED_APK)"

# Установка debug APK
install: debug
	@echo "$(GREEN)Установка debug APK на устройство...$(NC)"
	$(GRADLE_WRAPPER) installDebug
	@echo "$(GREEN)Debug APK установлен$(NC)"

# Установка release APK
release-install: release
	@echo "$(GREEN)Установка release APK на устройство...$(NC)"
	$(GRADLE_WRAPPER) installRelease
	@echo "$(GREEN)Release APK установлен$(NC)"

# Показать информацию о сборке
info:
	@echo "$(GREEN)Информация о проекте:$(NC)"
	@echo "Название приложения: $(APP_NAME)"
	@echo "Gradle wrapper: $(GRADLE_WRAPPER)"
	@echo "Файл конфигурации: $(PROPERTIES_FILE)"
	@echo "Директория ключей: $(KEYSTORE_DIR)"
	@echo "Выходной APK: $(SIGNED_APK)"

# Проверка зависимостей
deps:
	@echo "$(GREEN)Проверка зависимостей...$(NC)"
	java -version
	$(GRADLE_WRAPPER) --version
	@echo "$(GREEN)Проверка завершена$(NC)"
