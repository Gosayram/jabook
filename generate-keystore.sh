#!/bin/bash

# Скрипт для генерации keystore файла для подписи APK
# Использует данные из keystore.properties

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

# Функция для проверки Java
check_java() {
    if ! command -v java &> /dev/null; then
        log_error "Java не установлена. Пожалуйста, установите JDK 17+"
        exit 1
    fi

    local java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ]; then
        log_error "Требуется Java 17 или выше. Текущая версия: $java_version"
        exit 1
    fi

    log_info "Java версия: $(java -version 2>&1 | head -n 1)"
}

# Функция для чтения свойств
read_properties() {
    local file=$1
    local prefix=$2

    if [ ! -f "$file" ]; then
        log_error "Файл $file не найден"
        exit 1
    fi

    while IFS='=' read -r key value; do
        # Пропускаем комментарии и пустые строки
        [[ $key =~ ^[[:space:]]*# ]] && continue
        [[ -z $key ]] && continue

        # Убираем пробелы и кавычки
        key=$(echo $key | xargs)
        value=$(echo $value | xargs | tr -d '"')

        # Создаем переменные с префиксом
        eval "${prefix}${key}=${value}"
    done < "$file"
}

# Функция для генерации keystore
generate_keystore() {
    local keystore_file=$1
    local store_password=$2
    local key_alias=$3
    local key_password=$4

    log_info "Генерация keystore файла: $keystore_file"

    # Создаем директорию если не существует
    mkdir -p "$(dirname "$keystore_file")"

    # Генерируем keystore
    keytool -genkeypair \
        -keystore "$keystore_file" \
        -storepass "$store_password" \
        -alias "$key_alias" \
        -keypass "$key_password" \
        -keyalg RSA \
        -keysize 4096 \
        -validity 10000 \
        -dname "CN=JaBook, OU=JaBook, O=JaBook, L=Almaty, ST=Almaty, C=KZ" \
        -ext "san=dns:localhost,ip:127.0.0.1"

    if [ $? -eq 0 ]; then
        log_info "Keystore успешно сгенерирован"
        log_info "Файл: $keystore_file"
        log_info "Пароль хранилища: $store_password"
        log_info "Алиас: $key_alias"
        log_info "Пароль ключа: $key_password"
    else
        log_error "Ошибка при генерации keystore"
        exit 1
    fi
}

# Основная функция
main() {
    log_info "Генерация keystore для JaBook"

    # Проверяем Java
    check_java

    # Читаем свойства
    local keystore_properties="keystore.properties"
    if [ ! -f "$keystore_properties" ]; then
        log_error "Файл $keystore_properties не найден"
        exit 1
    fi

    read_properties "$keystore_properties" ""

    # Проверяем наличие необходимых свойств
    if [ -z "$storeFile" ] || [ -z "$storePassword" ] || [ -z "$keyAlias" ] || [ -z "$keyPassword" ]; then
        log_error "Некоторые свойства отсутствуют в keystore.properties"
        exit 1
    fi

    # Определяем путь к keystore
    local keystore_file="$storeFile"

    # Создаем директорию если не существует
    mkdir -p "$(dirname "$keystore_file")"

    # Проверяем, существует ли keystore
    if [ -f "$keystore_file" ]; then
        log_warn "Keystore файл уже существует: $keystore_file"
        log_info "Если вы хотите сгенерировать новый keystore, удалите существующий файл"
        exit 0
    fi

    # Генерируем keystore
    generate_keystore "$keystore_file" "$storePassword" "$keyAlias" "$keyPassword"

    log_info "Генерация keystore завершена"
    log_info "Теперь вы можете использовать команду 'make release' или 'make signed-release' для сборки подписанного APK"
}

# Запуск основной функции
main "$@"
