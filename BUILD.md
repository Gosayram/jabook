# Сборка приложения JaBook

Этот проект содержит инструменты для удобной сборки приложения JaBook как для разработки, так и для релиза.

## Файлы конфигурации

### `keystore.properties`
Файл с секретными данными для подписи APK.

**Важно:** Этот файл содержит секретные данные и не должен попадать в систему контроля версий.

### `generate-keystore.sh`
Скрипт для генерации keystore файла, если он отсутствует.

**Использование:**
```bash
./generate-keystore.sh
```

Скрипт прочитает данные из `keystore.properties` и сгенерирует keystore файл по указанному пути.

## Инструменты сборки

### 1. Makefile
Основной инструмент для сборки с использованием Gradle.

#### Доступные команды:
```bash
make help        # Показать справку
make clean       # Очистить сборочные файлы
make debug       # Собрать debug APK
make release     # Собрать release APK (подписанный)
make install     # Установить debug APK на устройство
make release-install # Установить release APK на устройство
make info        # Показать информацию о проекте
make deps        # Проверить зависимости
```

#### Примеры использования:
```bash
# Собрать debug версию
make debug

# Собрать release версию
make release

# Очистить и собрать заново
make clean && make release

# Установить на устройство
make install
```

### 2. build.sh
Скрипт-обертка для более удобной работы с Makefile.

#### Доступные команды:
```bash
./build.sh help              # Показать справку
./build.sh deps              # Проверить зависимости
./build.sh build [type]      # Собрать APK (debug|release|signed-release)
./build.sh install [type]    # Установить APK (debug|release)
./build.sh clean             # Очистить сборочные файлы
./build.sh info              # Показать информацию о проекте
```

#### Примеры использования:
```bash
# Собрать debug версию
./build.sh build

# Собрать release версию
./build.sh build release

# Собрать и установить debug версию
./build.sh build debug
./build.sh install debug

# Проверить зависимости
./build.sh deps
```

## Процесс сборки

### Debug сборка
1. Выполняет полную сборку приложения
2. Не требует подписи
3. Позволяет отладку
4. Быстрая сборка

### Release сборка
1. Выполняет полную сборку приложения
2. Подписывает APK с использованием keystore
3. Оптимизирована для производительности
4. Требует больше времени на сборку

### Signed Release сборка
1. Создает AAB файл (Android App Bundle)
2. Подписывает приложение
3. Позволяет создать APK из AAB с помощью bundletool

## Требования

- Java JDK 17+
- Android SDK
- Подключенное устройство или эмулятор
- Gradle (в комплекте проекта)

## Структура выходных файлов

```
app/
├── build/
│   └── outputs/
│       ├── apk/
│       │   ├── debug/
│       │   │   └── app-debug.apk
│       │   └── release/
│       │       └── app-release.apk
│       └── bundle/
│           └── release/
│               └── app-release.aab
```

## Использование

### Быстрый старт
```bash
# 1. Проверить зависимости
./build.sh deps

# 2. Собрать debug версию
./build.sh build

# 3. Установить на устройство
./build.sh install
```

### Сборка релиза
```bash
# 1. Собрать release версию
./build.sh build release

# 2. Установить на устройство
./build.sh install release
```

### Очистка
```bash
# Очистить все сборочные файлы
make clean
```

## Устранение проблем

### Проблемы с подписью
1. Убедитесь, что файл `keystore.properties` существует
2. Проверьте путь к keystore файлу
3. Убедитесь, что keystore файл существует по указанному пути

### Проблемы с зависимостями
1. Проверьте Java версию: `java -version`
2. Убедитесь, что Android SDK настроен правильно
3. Проверьте подключение устройства или эмулятора

### Проблемы с Gradle
1. Очистите кэш Gradle: `./gradlew --refresh-dependencies`
2. Удалите папку `.gradle` в корне проекта
3. Проверьте права доступа к файлам

## Советы

1. Для быстрой разработки используйте `make debug`
2. Для финальной сборки используйте `make release`
3. Регулярно очищайте сборочные файлы перед релизной сборкой
4. Проверяйте приложение на реальных устройствах перед релизом

## Полезные ссылки

- [Android Developer - Build and sign](https://developer.android.com/studio/build/building-cmdline)
- [Gradle User Guide](https://docs.gradle.org/current/userguide/userguide.html)
- [Android App Bundle](https://developer.android.com/guide/playcore)
