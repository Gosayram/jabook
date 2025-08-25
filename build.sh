#!/bin/bash

# Скрипт для сборки JaBook приложения
# Использует Makefile для удобной сборки

set -e

# Цвета для вывода
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Функция для вывода сообщений
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Функция для проверки зависимостей
check_dependencies() {
    log_info "Проверка зависимостей..."

    # Проверка Java
    if ! command -v java &> /dev/null; then
        log_error "Java не установлена. Пожалуйста, установите JDK 17+"
        exit 1
    fi

    # Проверка Gradle
    if ! command -v ./gradlew &> /dev/null; then
        log_error "Gradle wrapper не найден. Убедитесь, что находитесь в корневой директории проекта"
        exit 1
    fi

    log_info "Зависимости в порядке"
}

# Функция для сборки APK
build_apk() {
    local build_type=$1

    log_info "Начало сборки $build_type APK..."

    # Очистка предыдущих сборок
    log_info "Очистка предыдущих сборок..."
    make clean

    # Сборка APK
    case $build_type in
        "debug")
            make debug
            ;;
        "release")
            make release
            ;;
        "signed-release")
            make signed-release
            ;;
        *)
            log_error "Неизвестный тип сборки: $build_type"
            exit 1
            ;;
    esac

    log_info "Сборка $build_type APK завершена успешно!"
}

# Функция для установки APK
install_apk() {
    local build_type=$1

    log_info "Установка $build_type APK..."

    case $build_type in
        "debug")
            make install
            ;;
        "release")
            make release-install
            ;;
        *)
            log_error "Неизвестный тип установки: $build_type"
            exit 1
            ;;
    esac

    log_info "$build_type APK установлен успешно!"
}

# Основная логика
main() {
    local action=$1
    local build_type=${2:-"debug"}

    case $action in
        "help")
            echo "Доступные команды:"
            echo "  $0 help              - Показать эту справку"
            echo "  $0 deps              - Проверить зависимости"
            echo "  $0 build [type]      - Собрать APK (debug|release|signed-release)"
            echo "  $0 install [type]    - Установить APK (debug|release)"
            echo "  $0 clean             - Очистить сборочные файлы"
            echo "  $0 info              - Показать информацию о проекте"
            ;;
        "deps")
            check_dependencies
            ;;
        "build")
            check_dependencies
            build_apk $build_type
            ;;
        "install")
            check_dependencies
            install_apk $build_type
            ;;
        "clean")
            log_info "Очистка сборочных файлов..."
            make clean
            log_info "Очистка завершена"
            ;;
        "info")
            log_info "Информация о проекте:"
            make info
            ;;
        *)
            log_error "Неизвестная команда: $action"
            echo "Используйте '$0 help' для справки"
            exit 1
            ;;
    esac
}

# Запуск основной функции
main "$@"
