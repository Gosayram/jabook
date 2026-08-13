# Makefile: сборка, проверка и запуск JaBook

Все команды запускаются из корня репозитория, где расположен `Makefile`. Начните с:

```bash
make help
```

`Makefile` — основной интерфейс для локальной разработки. Он выбирает нужные Gradle-задачи и варианты приложения, поэтому прямой вызов `./gradlew` обычно не нужен.

## Быстрый выбор команды

| Задача | Команда | Результат |
|---|---|---|
| Изменили Kotlin-код и хотите быстро проверить dev-вариант | `make dev` | Форматирование, компиляция `dev`, установка на устройство |
| Нужен beta APK без подписи и установки | `make build-beta` | `beta/release` APK в `build/app/outputs/apk/beta/release` |
| Нужна подписанная beta-сборка для устройства | `make build-signed-apk-beta` | Подписанные beta APK; требуется настроенный сертификат |
| Собрать, установить и открыть подписанную beta | `make run-beta` | Сборка, установка arm64 APK, запуск и поток logcat |
| Проверить изменённый код перед передачей | `make check-all` | Линт, проверка документации, компиляция и Hilt-граф |
| Запустить полный локальный набор unit-тестов | `make test` | Unit-тесты варианта `beta` |

> [!IMPORTANT]
> `make beta` форматирует код, компилирует `BetaDebug` и затем устанавливает уже существующий `beta/release` APK. Для первой beta-сборки сначала используйте `make build-beta` или `make build-signed-apk-beta`.

## Beta: рекомендуемые сценарии

### Локально проверить beta APK

```bash
make build-beta
make install-beta
```

`build-beta` создаёт release APK, а `install-beta` устанавливает `app-beta-release.apk` через ADB. Для установки нужен подключённый и авторизованный Android-устройство или эмулятор.

### Собрать и запустить подписанную beta на устройстве

```bash
make run-beta
```

Команда настраивает существующий сертификат, собирает beta, устанавливает arm64 APK, запускает приложение и оставляет logcat открытым. Остановить просмотр логов можно через <kbd>Ctrl</kbd>+<kbd>C</kbd>.

### Подготовить beta для передачи

```bash
make build-beta-and-copy
```

Команда увеличивает номер сборки в `.release-version`, создаёт подписанную beta и копирует APK в `~/Downloads/Jabook`. Используйте её только когда увеличение build number ожидаемо.

### Если сертификат ещё не настроен

```bash
make sign-android
```

После создания сертификата следующие подписанные сборки используют `make build-signed-apk-beta`. Если сертификат уже хранится в `.signing/release.keystore`, достаточно этой команды — дополнительная ручная настройка не требуется.

## Разработка

| Сценарий | Команда | Когда использовать |
|---|---|---|
| Обычный цикл разработки | `make dev` | Форматирует, компилирует и устанавливает `dev`-вариант |
| Только компиляция dev | `make compile-dev` | Быстрая проверка после небольшого изменения |
| Только компиляция beta | `make compile-beta` | Проверка кода, специфичного для beta |
| Все поддерживаемые компиляционные варианты | `make compile` | Перед широкой проверкой изменений |
| Автоматическое форматирование Kotlin | `make fmt-kotlin` | До ревью или после ошибок ktlint/detekt |
| Установка уже собранного dev APK | `make run` | Когда APK уже есть и нужен только install |

## Проверки качества

| Уровень | Команда | Состав |
|---|---|---|
| Форматирование и компиляция | `make lint` | Очистка build-артефактов, форматирование, компиляция |
| Основная локальная проверка | `make check-all` | `lint-kotlin`, согласованность документации, beta/prod-компиляция, Hilt-граф |
| Проверка с CI-профилем тестов | `make check-all-with-tests` | `check-all` плюс строгие unit-тесты |
| Только статический анализ Kotlin | `make lint-kotlin` | ktlint, detekt, dependency verification и project guards |
| Только DI-граф | `make hilt-graph-check` | Hilt для beta и prod debug-вариантов |

После изменения Kotlin-кода обычно достаточно `make check-all`; перед отправкой существенного изменения используйте `make check-all-with-tests`.

## Тесты

| Что проверить | Команда |
|---|---|
| Быстрые unit-тесты без медленных групп | `make test-fast` |
| Все unit-тесты beta | `make test` |
| Строгий набор beta и prod, один worker | `make test-strict` или `make test-all` |
| Аудио и загрузки | `make test-audio` |
| Плеер и UI плеера | `make test-player` |
| Room, репозитории и миграции | `make test-storage` |
| Отчёт покрытия | `make test-coverage` |
| Проверка порога покрытия 85% | `make test-coverage-verify` |

## Устройство и диагностика

```bash
make devices       # Список устройств, видимых ADB
make logcat        # Логи JaBook, AndroidRuntime и FATAL
make clear-logcat  # Очистить буфер logcat
make uninstall     # Удалить prod applicationId с подключённого устройства
make run-beta-debug # Собрать beta и записывать подробный logcat в startup_profile.log
```

## Production-сборки

| Артефакт | Команда |
|---|---|
| Prod release APK | `make build-prod` |
| Prod Android App Bundle | `make build-bundle-prod` |
| Подписанные prod APK (split и universal) | `make build-signed-apk` |
| Подписать, собрать и скопировать prod APK | `make build-and-copy` |

Для анализа готового APK используйте `make apk-size`, `make apk-summary` или `make apk-compare OLD_APK=… NEW_APK=…`.

## Версия и обслуживание

```bash
make version                         # Прочитать версию из .release-version
make increment-build                 # Увеличить только build number
make update-version NEW_VERSION=1.2.3 # Задать версию явно
make android-lint                    # Запустить Android Lint
make clean                           # Удалить build-артефакты
```

> [!TIP]
> Если команда не описана здесь, её краткое описание можно получить через `make help`. Это автоматически формируемый список из текущих Makefile-целей и он остаётся источником истины.
