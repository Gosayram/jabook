// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get appTitle => 'JaBook';

  @override
  String get searchAudiobooks => 'Поиск аудиокниг';

  @override
  String get searchPlaceholder => 'Введите название, автора или ключевые слова';

  @override
  String get libraryTitle => 'Библиотека';

  @override
  String get playerTitle => 'Плеер';

  @override
  String get settingsTitle => 'Настройки';

  @override
  String get debugTitle => 'Отладка';

  @override
  String get mirrorsTitle => 'Зеркала';

  @override
  String get topicTitle => 'Топик';

  @override
  String get noResults => 'Результатов не найдено';

  @override
  String get noResultsForFilters => 'Нет результатов для выбранных фильтров';

  @override
  String get loading => 'Загрузка...';

  @override
  String get error => 'Ошибка';

  @override
  String get retry => 'Повторить';

  @override
  String get language => 'Язык';

  @override
  String get english => 'Английский';

  @override
  String get russian => 'Русский';

  @override
  String get systemDefault => 'Системный по умолчанию';

  @override
  String get failedToSearch => 'Не удалось выполнить поиск';

  @override
  String get resultsFromCache => 'Результаты из кэша';

  @override
  String get resultsFromLocalDb => 'Результаты из локальной базы данных';

  @override
  String get enterSearchTerm => 'Введите поисковый запрос чтобы начать';

  @override
  String get authorLabel => 'Автор: ';

  @override
  String get sizeLabel => 'Размер: ';

  @override
  String get seedersLabel => ' сидеров';

  @override
  String get leechersLabel => ' личеров';

  @override
  String get unknownTitle => 'Неизвестное название';

  @override
  String get unknownAuthor => 'Неизвестный автор';

  @override
  String get unknownSize => 'Неизвестный размер';

  @override
  String get addBookComingSoon => 'Функция добавления книг скоро появится!';

  @override
  String get libraryContentPlaceholder => 'Содержимое библиотеки будет отображаться здесь';

  @override
  String get authenticationRequired => 'Требуется аутентификация';

  @override
  String get loginRequiredForSearch => 'Пожалуйста, войдите в RuTracker для доступа к функции поиска.';

  @override
  String get cancel => 'Отмена';

  @override
  String get reset => 'Сбросить';

  @override
  String get resetToGlobalSettings => 'Сбросить до глобальных настроек';

  @override
  String get resetAllBookSettings => 'Сбросить настройки всех книг';

  @override
  String get resetAllBookSettingsDescription => 'Удалить индивидуальные настройки для всех книг';

  @override
  String get resetAllBookSettingsConfirmation => 'Это удалит индивидуальные аудио-настройки для всех книг. Все книги будут использовать глобальные настройки. Это действие нельзя отменить.';

  @override
  String get settingsResetToGlobal => 'Настройки сброшены до глобальных';

  @override
  String get allBookSettingsReset => 'Все настройки книг сброшены до глобальных';

  @override
  String get errorResettingSettings => 'Ошибка при сбросе настроек';

  @override
  String get login => 'Войти';

  @override
  String get networkConnectionError => 'Не удалось подключиться. Проверьте интернет или выберите другое зеркало в Настройках → Источники.';

  @override
  String get connectionFailed => 'Соединение не установлено. Проверьте интернет-подключение или попробуйте другое зеркало.';

  @override
  String get chooseMirror => 'Выбрать зеркало';

  @override
  String get networkErrorUser => 'Проблема с сетевым подключением';

  @override
  String get dnsError => 'Не удалось разрешить домен. Это может быть связано с сетевыми ограничениями или неактивным зеркалом.';

  @override
  String get timeoutError => 'Запрос занял слишком много времени. Проверьте соединение и попробуйте снова.';

  @override
  String get serverError => 'Сервер временно недоступен. Попробуйте позже или выберите другое зеркало.';

  @override
  String get recentSearches => 'Недавние поиски';

  @override
  String get searchExamples => 'Попробуйте эти примеры';

  @override
  String get quickActions => 'Быстрые действия';

  @override
  String get scanLocalFiles => 'Сканировать локальные файлы';

  @override
  String get featureComingSoon => 'Функция скоро появится';

  @override
  String get changeMirror => 'Сменить зеркало';

  @override
  String get checkConnection => 'Проверить соединение';

  @override
  String get permissionsRequired => 'Требуются разрешения';

  @override
  String get permissionExplanation => 'Это приложение требует разрешения на хранение для загрузки и сохранения аудиофайлов. Пожалуйста, предоставьте необходимые разрешения для продолжения.';

  @override
  String get grantPermissions => 'Предоставить разрешения';

  @override
  String get permissionDeniedTitle => 'В разрешении отказано';

  @override
  String get permissionDeniedMessage => 'Для загрузки файлов требуется разрешение на хранение. Пожалуйста, включите его в настройках приложения.';

  @override
  String get permissionDeniedButton => 'Открыть настройки';

  @override
  String get navLibrary => 'Библиотека';

  @override
  String get navAuth => 'Аутентификация';

  @override
  String get navSearch => 'Поиск';

  @override
  String get navSettings => 'Настройки';

  @override
  String get navDebug => 'Отладка';

  @override
  String get authTitle => 'Вход';

  @override
  String get usernameLabel => 'Имя пользователя';

  @override
  String get passwordLabel => 'Пароль';

  @override
  String get rememberMeLabel => 'Запомнить меня';

  @override
  String get loginButton => 'Войти';

  @override
  String get testConnectionButton => 'Проверить соединение';

  @override
  String get logoutButton => 'Выйти';

  @override
  String get cacheClearedSuccessfully => 'Кэш успешно очищен';

  @override
  String get downloadStatusUnknown => 'Статус загрузки неизвестен';

  @override
  String get failedToExportLogs => 'Не удалось экспортировать логи';

  @override
  String get logsExportedSuccessfully => 'Логи успешно экспортированы';

  @override
  String get mirrorHealthCheckCompleted => 'Проверка здоровья зеркал завершена';

  @override
  String get testAllMirrors => 'Проверить все зеркала';

  @override
  String get statusLabel => 'Статус:';

  @override
  String get lastOkLabel => 'Последний OK:';

  @override
  String get rttLabel => 'RTT:';

  @override
  String get milliseconds => 'мс';

  @override
  String get downloadLabel => 'Загрузка';

  @override
  String get statusLabelNoColon => 'Статус';

  @override
  String get downloadProgressLabel => 'Прогресс';

  @override
  String get cacheStatistics => 'Статистика кэша';

  @override
  String get totalEntries => 'Всего записей';

  @override
  String get searchCache => 'Кэш поиска';

  @override
  String get topicCache => 'Кэш топиков';

  @override
  String get memoryUsage => 'Использование памяти';

  @override
  String get clearAllCache => 'Очистить весь кэш';

  @override
  String get mirrorsScreenTitle => 'Зеркала';

  @override
  String get failedToLoadAudio => 'Не удалось загрузить аудио';

  @override
  String get chaptersLabel => 'Главы';

  @override
  String get searchChaptersHint => 'Поиск по номеру или названию...';

  @override
  String get allFilter => 'Все';

  @override
  String get currentFilter => 'Текущая';

  @override
  String get noChaptersFoundInSearch => 'Главы не найдены';

  @override
  String get downloadFunctionalityComingSoon => 'Функция загрузки скоро появится';

  @override
  String get requestTimedOut => 'Время запроса истекло';

  @override
  String get networkError => 'Сетевая ошибка';

  @override
  String get mirrorStatusDisabled => 'Отключено';

  @override
  String get mirrorResponseTime => 'Время отклика';

  @override
  String get mirrorLastCheck => 'Последняя проверка';

  @override
  String get mirrorTestIndividual => 'Проверить отдельное зеркало';

  @override
  String get activeStatus => 'Активно';

  @override
  String get disabledStatus => 'Отключено';

  @override
  String get languageDescription => 'Выберите язык';

  @override
  String get themeTitle => 'Тема';

  @override
  String get themeDescription => 'Выберите тему приложения';

  @override
  String get darkMode => 'Темный режим';

  @override
  String get highContrast => 'Высокая контрастность';

  @override
  String get audioTitle => 'Аудио';

  @override
  String get audioDescription => 'Настройки аудио';

  @override
  String get audioEnhancementTitle => 'Улучшение звука';

  @override
  String get audioEnhancementDescription => 'Улучшение качества звука и стабильности громкости';

  @override
  String get normalizeVolumeTitle => 'Нормализация громкости';

  @override
  String get normalizeVolumeDescription => 'Поддержание стабильной громкости для разных аудиокниг';

  @override
  String get volumeBoostTitle => 'Усиление громкости';

  @override
  String get drcLevelTitle => 'Компрессия динамического диапазона';

  @override
  String get speechEnhancerTitle => 'Улучшение речи';

  @override
  String get speechEnhancerDescription => 'Улучшение четкости речи и уменьшение шипящих звуков';

  @override
  String get autoVolumeLevelingTitle => 'Автоматическое выравнивание громкости';

  @override
  String get autoVolumeLevelingDescription => 'Автоматическая регулировка громкости для поддержания стабильного уровня';

  @override
  String get playbackSpeed => 'Скорость воспроизведения';

  @override
  String get skipDuration => 'Длительность пропуска';

  @override
  String get downloadsTitle => 'Загрузки';

  @override
  String get downloadsDescription => 'Настройки загрузки';

  @override
  String get downloadLocation => 'Место загрузки';

  @override
  String get wifiOnlyDownloads => 'Загрузки только по Wi-Fi';

  @override
  String get dataLoadedFromCache => 'Данные загружены из кэша';

  @override
  String get failedToLoadTopic => 'Не удалось загрузить топик';

  @override
  String get magnetLinkLabel => 'Magnet-ссылка';

  @override
  String get unknownChapter => 'Неизвестная глава';

  @override
  String get magnetLinkCopied => 'Magnet-ссылка скопирована';

  @override
  String get copyToClipboardLabel => 'Скопировать в буфер';

  @override
  String get debugToolsTitle => 'Инструменты отладки';

  @override
  String get logsTab => 'Логи';

  @override
  String get mirrorsTab => 'Зеркала';

  @override
  String get downloadsTab => 'Загрузки';

  @override
  String get cacheTab => 'Кэш';

  @override
  String get testAllMirrorsButton => 'Проверить все зеркала';

  @override
  String get statusLabelText => 'Статус: ';

  @override
  String get lastOkLabelText => 'Последний OK: ';

  @override
  String get rttLabelText => 'RTT: ';

  @override
  String get millisecondsText => 'мс';

  @override
  String get downloadLabelText => 'Загрузка';

  @override
  String get statusLabelNoColonText => 'Статус';

  @override
  String get downloadProgressLabelText => 'Прогресс';

  @override
  String get cacheStatisticsTitle => 'Статистика кэша';

  @override
  String get totalEntriesText => 'Всего записей: ';

  @override
  String get searchCacheText => 'Кэш поиска: ';

  @override
  String get topicCacheText => 'Кэш топиков: ';

  @override
  String get memoryUsageText => 'Использование памяти: ';

  @override
  String get clearAllCacheButton => 'Очистить весь кэш';

  @override
  String get cacheIsEmptyMessage => 'Кэш пуст';

  @override
  String get refreshDebugDataButton => 'Обновить данные отладки';

  @override
  String get exportLogsButton => 'Экспортировать логи';

  @override
  String get deleteDownloadButton => 'Удалить загрузку';

  @override
  String get doneButtonText => 'Готово';

  @override
  String get webViewTitle => 'RuTracker';

  @override
  String get webViewLoginInstruction => 'Войдите в RuTracker. После успешного входа нажмите «Готово», чтобы извлечь cookie для клиента.';

  @override
  String get cloudflareMessage => 'Этот сайт использует проверки безопасности Cloudflare. Пожалуйста, дождитесь завершения проверки и взаимодействуйте с открывшейся страницей при необходимости.';

  @override
  String get retryButtonText => 'Повторить';

  @override
  String get goHomeButtonText => 'Перейти на главную';

  @override
  String get browserCheckMessage => 'Сайт проверяет ваш браузер - пожалуйста, дождитесь завершения проверки на этой странице.';

  @override
  String get downloadTorrentTitle => 'Скачать торрент';

  @override
  String get selectActionText => 'Выберите действие:';

  @override
  String get openButtonText => 'Открыть';

  @override
  String get downloadButtonText => 'Скачать';

  @override
  String get downloadInBrowserMessage => 'Чтобы скачать файл, пожалуйста, откройте ссылку в браузере';

  @override
  String get importAudiobooksTitle => 'Импортировать аудиокниги';

  @override
  String get selectFilesMessage => 'Выберите файлы аудиокниг с вашего устройства для добавления в библиотеку';

  @override
  String get importButtonText => 'Импортировать';

  @override
  String importedSuccess(int count) {
    return 'Импортировано \$count аудиокнига(и)';
  }

  @override
  String get noFilesSelectedMessage => 'Файлы не выбраны';

  @override
  String importFailedMessage(String error) {
    return 'Не удалось импортировать: \$error';
  }

  @override
  String get scanFolderTitle => 'Сканировать папку';

  @override
  String get scanFolderMessage => 'Сканируйте папку на вашем устройстве в поисках файлов аудиокниг';

  @override
  String get scanButtonText => 'Сканировать';

  @override
  String scanSuccessMessage(int count) {
    return 'Найдено и импортировано \$count аудиокнига(и)';
  }

  @override
  String get noAudiobooksFoundMessage => 'В выбранной папке не найдено файлов аудиокниг';

  @override
  String get noFolderSelectedMessage => 'Папка не выбрана';

  @override
  String scanFailedMessage(String error) {
    return 'Не удалось просканировать папку: \$error';
  }

  @override
  String get authScreenTitle => 'Подключение к RuTracker';

  @override
  String get usernameLabelText => 'Имя пользователя';

  @override
  String get passwordLabelText => 'Пароль';

  @override
  String get rememberMeLabelText => 'Запомнить меня';

  @override
  String get loginButtonText => 'Войти';

  @override
  String get testConnectionButtonText => 'Проверить соединение';

  @override
  String get logoutButtonText => 'Выйти';

  @override
  String get loggingInText => 'Вход в систему...';

  @override
  String get loginSuccessMessage => 'Вход выполнен успешно!';

  @override
  String get loginFailedMessage => 'Вход не удался. Пожалуйста, проверьте учетные данные';

  @override
  String loginErrorMessage(String error) {
    return 'Ошибка входа: \$error';
  }

  @override
  String connectionSuccessMessage(String endpoint) {
    return 'Соединение установлено! Используется: \$endpoint';
  }

  @override
  String connectionFailedMessage(String error) {
    return 'Проверка соединения не удалась: \$error';
  }

  @override
  String get loggingOutText => 'Выход из системы...';

  @override
  String get logoutSuccessMessage => 'Выполнен выход из системы';

  @override
  String logoutErrorMessage(String error) {
    return 'Ошибка выхода: \$error';
  }

  @override
  String get configureMirrorsSubtitle => 'Настройте и проверьте зеркала RuTracker';

  @override
  String get playbackSpeedTitle => 'Скорость воспроизведения';

  @override
  String get skipDurationTitle => 'Длительность пропуска';

  @override
  String get downloadLocationTitle => 'Место загрузки';

  @override
  String get darkModeTitle => 'Темный режим';

  @override
  String get highContrastTitle => 'Высокая контрастность';

  @override
  String get wifiOnlyDownloadsTitle => 'Загрузки только по Wi-Fi';

  @override
  String get failedToLoadMirrorsMessage => 'Не удалось загрузить зеркала: \$error';

  @override
  String get mirrorTestSuccessMessage => 'Зеркало \$url проверено успешно';

  @override
  String get mirrorTestFailedMessage => 'Не удалось проверить зеркало \$url: \$error';

  @override
  String get mirrorStatusText => 'Зеркало \$status';

  @override
  String get failedToUpdateMirrorMessage => 'Не удалось обновить зеркало: \$error';

  @override
  String get addCustomMirrorTitle => 'Добавить зеркало';

  @override
  String get mirrorUrlLabelText => 'URL зеркала';

  @override
  String get mirrorUrlHintText => 'https://rutracker.example.com';

  @override
  String get priorityLabelText => 'Приоритет (1-10)';

  @override
  String get priorityHintText => '5';

  @override
  String get addMirrorButtonText => 'Добавить';

  @override
  String get mirrorAddedMessage => 'Зеркало \$url добавлено';

  @override
  String get failedToAddMirrorMessage => 'Не удалось добавить зеркало: \$error';

  @override
  String get mirrorSettingsTitle => 'Настройки зеркал';

  @override
  String get mirrorSettingsDescription => 'Настройте зеркала RuTracker для оптимальной производительности поиска. Включенные зеркала будут использоваться автоматически.';

  @override
  String get addCustomMirrorButtonText => 'Добавить зеркало';

  @override
  String priorityText(int priority) {
    return 'Приоритет: \$priority';
  }

  @override
  String get responseTimeText => 'Время отклика: \$rtt мс';

  @override
  String lastCheckedText(String date) {
    return 'Последняя проверка: \$date';
  }

  @override
  String get testMirrorButtonText => 'Проверить это зеркало';

  @override
  String get activeStatusText => 'Активно';

  @override
  String get disabledStatusText => 'Отключено';

  @override
  String get neverDateText => 'Никогда';

  @override
  String get invalidDateText => 'Неверная дата';

  @override
  String get playerScreenTitle => 'Плеер';

  @override
  String get failedToLoadAudioMessage => 'Не удалось загрузить аудиокнигу';

  @override
  String get byAuthorText => 'автор: \$author';

  @override
  String get chaptersLabelText => 'Главы';

  @override
  String get downloadFunctionalityComingSoonMessage => 'Функция загрузки скоро появится!';

  @override
  String get sampleTitleText => 'Пример аудиокниги';

  @override
  String get sampleAuthorText => 'Пример автора';

  @override
  String get sampleCategoryText => 'Художественная';

  @override
  String get sampleSizeText => '150 МБ';

  @override
  String get sampleChapter1Text => 'Глава 1';

  @override
  String get sampleChapter2Text => 'Глава 2';

  @override
  String chapterNumber(int number) {
    return 'Глава $number';
  }

  @override
  String get topicScreenTitle => 'Топик';

  @override
  String get requestTimedOutMessage => 'Время запроса истекло. Проверьте соединение.';

  @override
  String networkErrorMessage(String error) {
    return 'Сетевая ошибка: \$error';
  }

  @override
  String errorLoadingTopicMessage(String error) {
    return 'Ошибка загрузки топика: \$error';
  }

  @override
  String get failedToLoadTopicMessage => 'Не удалось загрузить топик';

  @override
  String get dataLoadedFromCacheMessage => 'Данные загружены из кэша';

  @override
  String get unknownChapterText => 'Неизвестная глава';

  @override
  String get magnetLinkLabelText => 'Magnet-ссылка';

  @override
  String get magnetLinkCopiedMessage => 'Magnet-ссылка скопирована в буфер обмена';

  @override
  String copyToClipboardMessage(String label) {
    return '\$label скопирован в буфер обмена';
  }

  @override
  String get navLibraryText => 'Библиотека';

  @override
  String get navAuthText => 'Подключение';

  @override
  String get navSearchText => 'Поиск';

  @override
  String get navSettingsText => 'Настройки';

  @override
  String get navDebugText => 'Отладка';

  @override
  String get authDialogTitle => 'Вход';

  @override
  String get authHelpText => 'Войдите в RuTracker для доступа к поиску и загрузке аудиокниг. Ваши учетные данные хранятся в безопасности.';

  @override
  String get cacheClearedSuccessfullyMessage => 'Кэш успешно очищен';

  @override
  String get downloadStatusUnknownMessage => 'Статус загрузки неизвестен';

  @override
  String get failedToExportLogsMessage => 'Не удалось экспортировать логи';

  @override
  String get logsExportedSuccessfullyMessage => 'Логи успешно экспортированы';

  @override
  String mirrorHealthCheckCompletedMessage(int tested, int total) {
    return 'Проверка завершена: \$tested/\$total зеркал';
  }

  @override
  String get noActiveMirrorsMessage => 'Нет активных зеркал для проверки';

  @override
  String activeMirrorSetMessage(String url) {
    return 'Активное зеркало установлено: \$url';
  }

  @override
  String failedToSetActiveMirrorMessage(String error) {
    return 'Не удалось установить активное зеркало: \$error';
  }

  @override
  String activeMirrorText(String url) {
    return 'Активное зеркало: \$url';
  }

  @override
  String failedToSetBestMirrorMessage(String error) {
    return 'Не удалось установить лучшее зеркало: \$error';
  }

  @override
  String activeMirrorDisabledMessage(String url) {
    return 'Активное зеркало отключено. Переключено на: \$url';
  }

  @override
  String warningFailedToSelectNewActiveMirrorMessage(String error) {
    return 'Предупреждение: не удалось выбрать новое активное зеркало: \$error';
  }

  @override
  String get urlCopiedToClipboardMessage => 'URL скопирован в буфер обмена';

  @override
  String get editPriorityTitle => 'Изменить приоритет';

  @override
  String get priorityHelperText => 'Меньше число = выше приоритет';

  @override
  String get saveButtonText => 'Сохранить';

  @override
  String priorityUpdatedMessage(int priority) {
    return 'Приоритет обновлен: \$priority';
  }

  @override
  String failedToUpdatePriorityMessage(String error) {
    return 'Не удалось обновить приоритет: \$error';
  }

  @override
  String get deleteMirrorTitle => 'Удалить зеркало?';

  @override
  String get defaultMirrorWarningMessage => 'Внимание: это зеркало по умолчанию. Оно будет удалено из списка, но может быть добавлено снова.';

  @override
  String get confirmDeleteMirrorMessage => 'Вы уверены, что хотите удалить это зеркало?';

  @override
  String get deleteButtonText => 'Удалить';

  @override
  String mirrorDeletedMessage(String url) {
    return 'Зеркало удалено: \$url';
  }

  @override
  String failedToDeleteMirrorMessage(String error) {
    return 'Не удалось удалить зеркало: \$error';
  }

  @override
  String get urlMustStartWithHttpMessage => 'URL должен начинаться с http:// или https://';

  @override
  String get invalidUrlFormatMessage => 'Неверный формат URL';

  @override
  String get mirrorAlreadyExistsMessage => 'Это зеркало уже существует в списке';

  @override
  String get onlyActiveFilterText => 'Только активные';

  @override
  String get onlyHealthyFilterText => 'Только здоровые';

  @override
  String get sortByPriorityText => 'По приоритету';

  @override
  String get sortByHealthText => 'По здоровью';

  @override
  String get sortBySpeedText => 'По скорости';

  @override
  String get noMirrorsMatchFiltersMessage => 'Нет зеркал, соответствующих фильтрам';

  @override
  String get setBestMirrorButtonText => 'Установить лучшее зеркало';

  @override
  String healthScoreText(int score) {
    return 'Здоровье: \$score%';
  }

  @override
  String get setAsActiveTooltip => 'Установить как активное';

  @override
  String get copyUrlTooltip => 'Копировать URL';

  @override
  String get deleteMirrorTooltip => 'Удалить зеркало';

  @override
  String get activeLabelText => 'Активно';

  @override
  String failedToClearCacheMessage(String error) {
    return 'Не удалось очистить кэш: \$error';
  }

  @override
  String failedToTestMirrorsMessage(String error) {
    return 'Не удалось проверить зеркала: \$error';
  }

  @override
  String get mirrorStatusDegraded => 'Деградировано';

  @override
  String get mirrorStatusUnhealthy => 'Неисправно';

  @override
  String get pleaseEnterCredentials => 'Пожалуйста, введите имя пользователя и пароль';

  @override
  String get testingConnectionText => 'Проверка соединения...';

  @override
  String get favoritesTitle => 'Избранное';

  @override
  String get refreshTooltip => 'Обновить';

  @override
  String get noFavoritesMessage => 'Нет избранных аудиокниг';

  @override
  String get addFavoritesHint => 'Добавьте аудиокниги в избранное из результатов поиска';

  @override
  String get goToSearchButton => 'Перейти к поиску';

  @override
  String get addedToFavorites => 'Добавлено в избранное';

  @override
  String get removedFromFavorites => 'Удалено из избранного';

  @override
  String get failedToRemoveFromFavorites => 'Не удалось удалить из избранного';

  @override
  String get favoritesTooltip => 'Избранное';

  @override
  String get filterLibraryTooltip => 'Фильтр библиотеки';

  @override
  String get addAudiobookTooltip => 'Добавить аудиокнигу';

  @override
  String get libraryEmptyMessage => 'Ваша библиотека пуста';

  @override
  String get addAudiobooksHint => 'Добавьте аудиокниги в библиотеку, чтобы начать прослушивание';

  @override
  String get searchForAudiobooksButton => 'Поиск аудиокниг';

  @override
  String get importFromFilesButton => 'Импорт из файлов';

  @override
  String get biometricAuthInProgress => 'Подождите, выполняется биометрическая аутентификация...';

  @override
  String get authorizationSuccessful => 'Авторизация успешна';

  @override
  String get authorizationTitle => 'Авторизация';

  @override
  String get biometricUnavailableMessage => 'Биометрическая аутентификация недоступна или не удалась. Открыть WebView для входа?';

  @override
  String get openWebViewButton => 'Открыть WebView';

  @override
  String authorizationCheckError(String error) {
    return 'Ошибка при проверке авторизации: \$error';
  }

  @override
  String get authorizationFailedMessage => 'Авторизация не удалась. Проверьте логин и пароль';

  @override
  String authorizationPageError(String error) {
    return 'Ошибка открытия страницы авторизации: \$error';
  }

  @override
  String get filtersLabel => 'Фильтры:';

  @override
  String get resetButton => 'Сбросить';

  @override
  String get otherCategory => 'Другое';

  @override
  String get searchHistoryTitle => 'История поиска';

  @override
  String get clearButton => 'Очистить';

  @override
  String get failedToAddToFavorites => 'Не удалось добавить в избранное';

  @override
  String get loadMoreButton => 'Загрузить еще';

  @override
  String get allowButton => 'Разрешить';

  @override
  String get copyMagnetLink => 'Скопировать Magnet-ссылку';

  @override
  String get downloadTorrentMenu => 'Скачать торрент';

  @override
  String get openTorrentInExternalApp => 'Открыть файл торрента во внешнем приложении';

  @override
  String failedToOpenTorrent(String error) {
    return 'Не удалось открыть торрент: \$error';
  }

  @override
  String get urlUnavailable => 'URL недоступен';

  @override
  String invalidUrlFormat(String url) {
    return 'Неверный формат URL: \$url';
  }

  @override
  String genericError(String error) {
    return 'Ошибка: \$error';
  }

  @override
  String retryConnectionMessage(int current, int max) {
    return 'Повторная попытка подключения (\$current/\$max)...';
  }

  @override
  String loadError(String desc) {
    return 'Ошибка загрузки: \$desc';
  }

  @override
  String get pageLoadError => 'Произошла ошибка при загрузке страницы';

  @override
  String get securityVerificationInProgress => 'Проверка безопасности выполняется - пожалуйста, подождите...';

  @override
  String get openInBrowserButton => 'Открыть в браузере';

  @override
  String get fileWillOpenInBrowser => 'Для загрузки файл будет открыт в браузере';

  @override
  String get resetFiltersButton => 'Сбросить фильтры';

  @override
  String languageChangedMessage(String languageName) {
    return 'Язык изменен на $languageName';
  }

  @override
  String get followSystemTheme => 'Следовать системной теме';

  @override
  String get rutrackerSessionDescription => 'Управление сессией RuTracker (cookie)';

  @override
  String get loginViaWebViewButton => 'Войти в RuTracker через WebView';

  @override
  String get loginViaWebViewSubtitle => 'Пройти Cloudflare/капчу и сохранить cookie для клиента';

  @override
  String get cookiesSavedForHttpClient => 'Cookie сохранены для HTTP-клиента';

  @override
  String get clearSessionButton => 'Очистить сессию RuTracker (cookie)';

  @override
  String get clearSessionSubtitle => 'Удалить сохранённые cookie и выйти из аккаунта';

  @override
  String get sessionClearedMessage => 'Сессия RuTracker очищена';

  @override
  String get metadataSectionTitle => 'Метаданные аудиокниг';

  @override
  String get metadataSectionDescription => 'Управление локальной базой метаданных аудиокниг';

  @override
  String get totalRecordsLabel => 'Всего записей';

  @override
  String get lastUpdateLabel => 'Последнее обновление';

  @override
  String get updatingText => 'Обновление...';

  @override
  String get updateMetadataButton => 'Обновить метаданные';

  @override
  String get metadataUpdateStartedMessage => 'Начато обновление метаданных...';

  @override
  String metadataUpdateCompletedMessage(int total) {
    return 'Обновление завершено: собрано \$total записей';
  }

  @override
  String metadataUpdateError(String error) {
    return 'Ошибка обновления: \$error';
  }

  @override
  String get neverDate => 'Никогда';

  @override
  String daysAgo(int days) {
    return '\$days дн. назад';
  }

  @override
  String hoursAgo(int hours) {
    return '\$hours ч. назад';
  }

  @override
  String minutesAgo(int minutes) {
    return '\$minutes мин. назад';
  }

  @override
  String get justNow => 'Только что';

  @override
  String get unknownDate => 'Неизвестно';

  @override
  String get playbackSpeedDefault => '1.0x';

  @override
  String get skipDurationDefault => '15 секунд';

  @override
  String get clearExpiredCacheButton => 'Очистить устаревший кэш';

  @override
  String get appPermissionsTitle => 'Разрешения приложения';

  @override
  String get storagePermissionName => 'Хранилище';

  @override
  String get storagePermissionDescription => 'Сохранять файлы аудиокниг и данные кэша';

  @override
  String get notificationsPermissionName => 'Уведомления';

  @override
  String get notificationsPermissionDescription => 'Показывать элементы управления воспроизведением и обновления';

  @override
  String get grantAllPermissionsButton => 'Предоставить все разрешения';

  @override
  String get allPermissionsGranted => 'Все разрешения предоставлены';

  @override
  String get fileAccessAvailable => 'Доступ к файлам доступен';

  @override
  String get fileAccessUnavailable => 'Доступ к файлам недоступен';

  @override
  String get storagePermissionGuidanceTitle => 'Разрешение на доступ к файлам';

  @override
  String get storagePermissionGuidanceMessage => 'Для доступа к аудиофайлам необходимо предоставить разрешение на доступ к файлам.';

  @override
  String get storagePermissionGuidanceStep1 => '1. Откройте настройки приложения JaBook';

  @override
  String get storagePermissionGuidanceStep2 => '2. Перейдите в раздел \"Разрешения\"';

  @override
  String get storagePermissionGuidanceStep3 => '3. Включите разрешение \"Файлы и медиа\" или \"Хранилище\"';

  @override
  String get storagePermissionGuidanceMiuiTitle => 'Разрешение на доступ к файлам (MIUI)';

  @override
  String get storagePermissionGuidanceMiuiMessage => 'На устройствах Xiaomi/Redmi/Poco (MIUI) необходимо предоставить разрешение на доступ к файлам:';

  @override
  String get storagePermissionGuidanceMiuiStep1 => '1. Откройте Настройки → Приложения → JaBook → Разрешения';

  @override
  String get storagePermissionGuidanceMiuiStep2 => '2. Включите разрешение \"Файлы и медиа\" или \"Хранилище\"';

  @override
  String get storagePermissionGuidanceMiuiStep3 => '3. Если доступ ограничен, включите \"Управление всеми файлами\" в настройках безопасности MIUI';

  @override
  String get storagePermissionGuidanceMiuiNote => 'Примечание: В некоторых версиях MIUI может потребоваться дополнительно включить \"Управление всеми файлами\" в Настройки → Безопасность → Управление разрешениями.';

  @override
  String get storagePermissionGuidanceColorosTitle => 'Разрешение на доступ к файлам (ColorOS/RealmeUI)';

  @override
  String get storagePermissionGuidanceColorosMessage => 'На устройствах Oppo/Realme (ColorOS/RealmeUI) необходимо предоставить разрешение на доступ к файлам:';

  @override
  String get storagePermissionGuidanceColorosStep1 => '1. Откройте Настройки → Приложения → JaBook → Разрешения';

  @override
  String get storagePermissionGuidanceColorosStep2 => '2. Включите разрешение \"Файлы и медиа\"';

  @override
  String get storagePermissionGuidanceColorosStep3 => '3. Если доступ ограничен, проверьте настройки \"Файлы и медиа\" в разделе разрешений';

  @override
  String get storagePermissionGuidanceColorosNote => 'Примечание: В некоторых версиях ColorOS может потребоваться дополнительно разрешить доступ к файлам в настройках безопасности.';

  @override
  String get openSettings => 'Открыть настройки';

  @override
  String get safFallbackTitle => 'Альтернативный метод доступа к файлам';

  @override
  String get safFallbackMessage => 'Разрешения на доступ к файлам не работают должным образом на вашем устройстве. Вы можете использовать Storage Access Framework (SAF) для выбора папок вручную. Этот метод работает без специальных разрешений.';

  @override
  String get safPermissionCheckboxMessage => 'Пожалуйста, отметьте чекбокс \'Разрешить доступ к этой папке\' в диалоге выбора папки и попробуйте снова. Без этого чекбокса приложение не сможет получить доступ к выбранной папке.';

  @override
  String get safNoAccessMessage => 'Нет доступа к выбранной папке. Пожалуйста, отметьте чекбокс \'Разрешить доступ к этой папке\' в диалоге выбора папки и попробуйте снова.';

  @override
  String get safFolderPickerHintTitle => 'Важно: Отметьте чекбокс';

  @override
  String get safFolderPickerHintMessage => 'При выборе папки обязательно отметьте чекбокс \'Разрешить доступ к этой папке\' в диалоге выбора папки. Без этого чекбокса приложение не сможет получить доступ к выбранной папке.';

  @override
  String get safAndroidDataObbWarning => 'Примечание: Доступ к папкам Android/data и Android/obb заблокирован на устройствах Android 11+ с обновлениями безопасности от марта 2024 года. Пожалуйста, выберите другую папку.';

  @override
  String get safFallbackBenefits => 'Преимущества использования SAF:\n• Работает на всех Android устройствах\n• Не требует специальных разрешений\n• Вы выбираете, к каким папкам предоставить доступ';

  @override
  String get useSafMethod => 'Использовать выбор папок';

  @override
  String get tryPermissionsAgain => 'Попробовать разрешения снова';

  @override
  String get notificationsAvailable => 'Уведомления доступны';

  @override
  String get notificationsUnavailable => 'Уведомления недоступны';

  @override
  String capabilitiesStatus(int grantedCount, int total) {
    return 'Возможности: \$grantedCount/\$total';
  }

  @override
  String get backupRestoreTitle => 'Резервное копирование и восстановление';

  @override
  String get backupRestoreDescription => 'Экспорт и импорт ваших данных (избранное, история, метаданные)';

  @override
  String get exportDataButton => 'Экспортировать данные';

  @override
  String get exportDataSubtitle => 'Сохранить все ваши данные в файл резервной копии';

  @override
  String get importDataButton => 'Импортировать данные';

  @override
  String get importDataSubtitle => 'Восстановить данные из файла резервной копии';

  @override
  String get exportingDataMessage => 'Экспорт данных...';

  @override
  String get dataExportedSuccessfullyMessage => 'Данные успешно экспортированы';

  @override
  String failedToExportMessage(String error) {
    return 'Не удалось экспортировать: \$error';
  }

  @override
  String get importBackupTitle => 'Импорт резервной копии';

  @override
  String get importBackupConfirmationMessage => 'Это импортирует данные из файла резервной копии. Существующие данные могут быть объединены или заменены. Продолжить?';

  @override
  String get importButton => 'Импортировать';

  @override
  String get importingDataMessage => 'Импорт данных...';

  @override
  String failedToImportMessage(String error) {
    return 'Не удалось импортировать: \$error';
  }

  @override
  String get rutrackerLoginTooltip => 'Вход в RuTracker';

  @override
  String get mirrorsTooltip => 'Зеркала';

  @override
  String currentMirrorLabel(String host) {
    return 'Текущее зеркало: $host';
  }

  @override
  String get allMirrorsFailedMessage => 'Все зеркала недоступны';

  @override
  String get unknownError => 'Неизвестная ошибка';

  @override
  String get mirrorConnectionError => 'Не удалось подключиться к зеркалам RuTracker. Проверьте подключение к интернету или попробуйте выбрать другое зеркало в настройках';

  @override
  String get mirrorConnectionFailed => 'Не удалось подключиться к зеркалам RuTracker';

  @override
  String get refresh => 'Обновить';

  @override
  String get refreshCurrentSearch => 'Обновить текущий поиск';

  @override
  String get clearSearchCache => 'Очистить кэш поиска';

  @override
  String get cacheCleared => 'Кэш очищен';

  @override
  String get allCacheCleared => 'Весь кэш очищен';

  @override
  String get downloadViaMagnet => 'Скачать через Magnet';

  @override
  String get downloadStarted => 'Загрузка начата';

  @override
  String get refreshChaptersFromTorrent => 'Обновить главы из торрента';

  @override
  String get chaptersRefreshed => 'Главы обновлены из торрента';

  @override
  String get failedToRefreshChapters => 'Не удалось обновить главы';

  @override
  String get noChaptersFound => 'Главы в торренте не найдены';

  @override
  String get failedToStartDownload => 'Не удалось начать загрузку';

  @override
  String get noActiveDownloads => 'Нет активных загрузок';

  @override
  String get cacheExpires => 'Истекает через';

  @override
  String get cacheExpired => 'Истек';

  @override
  String get cacheExpiresSoon => 'Скоро истечет';

  @override
  String get days => 'дн.';

  @override
  String get hours => 'ч.';

  @override
  String get minutes => 'мин.';

  @override
  String get showMore => 'Показать больше';

  @override
  String get showLess => 'Показать меньше';

  @override
  String get selectFolderDialogTitle => 'Выбор папки для загрузок';

  @override
  String get selectFolderDialogMessage => 'Пожалуйста, выберите папку, в которую будут сохраняться загруженные аудиокниги. В файловом менеджере перейдите к нужной папке и нажмите \"Использовать эту папку\" для подтверждения.';

  @override
  String get folderSelectedSuccessMessage => 'Папка для загрузок выбрана успешно';

  @override
  String get folderSelectionCancelledMessage => 'Выбор папки отменен';

  @override
  String currentDownloadFolder(String path) {
    return 'Текущая папка: $path';
  }

  @override
  String get defaultDownloadFolder => 'Папка по умолчанию';

  @override
  String get pressBackAgainToExit => 'Нажмите еще раз для выхода';

  @override
  String get filterOptionsComingSoon => 'Фильтры будут доступны в ближайшее время. Вы сможете фильтровать по:';

  @override
  String get filterByCategory => 'Категория';

  @override
  String get filterByAuthor => 'Автор';

  @override
  String get filterByDate => 'Дата добавления';

  @override
  String get close => 'Закрыть';

  @override
  String get openInExternalClient => 'Открыть во внешнем клиенте';

  @override
  String get downloadTorrentInApp => 'Скачать через встроенный клиент';

  @override
  String get openedInExternalClient => 'Открыто во внешнем торрент-клиенте';

  @override
  String get failedToOpenExternalClient => 'Не удалось открыть во внешнем клиенте';

  @override
  String get noMagnetLinkAvailable => 'Magnet-ссылка недоступна';

  @override
  String get recommended => 'Рекомендуем';

  @override
  String noResultsForQuery(String query) {
    return 'Ничего не найдено по запросу: \"$query\"';
  }

  @override
  String get tryDifferentKeywords => 'Попробуйте изменить ключевые слова';

  @override
  String get clearSearch => 'Очистить поиск';

  @override
  String get libraryFolderTitle => 'Папки библиотеки';

  @override
  String get libraryFolderDescription => 'Выберите папки, где хранятся ваши аудиокниги';

  @override
  String get defaultLibraryFolder => 'Папка по умолчанию';

  @override
  String get addLibraryFolderTitle => 'Добавить папку библиотеки';

  @override
  String get addLibraryFolderSubtitle => 'Добавить дополнительную папку для сканирования аудиокниг';

  @override
  String get allLibraryFoldersTitle => 'Все папки библиотеки';

  @override
  String get primaryLibraryFolder => 'Основная папка';

  @override
  String get selectLibraryFolderDialogTitle => 'Выбрать папку библиотеки';

  @override
  String get selectLibraryFolderDialogMessage => 'Чтобы выбрать папку библиотеки:\n\n1. Перейдите к нужной папке в файловом менеджере\n2. Нажмите кнопку \"Использовать эту папку\" в правом верхнем углу\n\nВыбранная папка будет использоваться для сканирования аудиокниг.';

  @override
  String get migrateLibraryFolderTitle => 'Перенести файлы?';

  @override
  String get migrateLibraryFolderMessage => 'Хотите переместить существующие аудиокниги из старой папки в новую?';

  @override
  String get yes => 'Да';

  @override
  String get no => 'Нет';

  @override
  String get libraryFolderSelectedSuccessMessage => 'Папка библиотеки успешно выбрана';

  @override
  String get libraryFolderAlreadyExistsMessage => 'Эта папка уже есть в списке папок библиотеки';

  @override
  String get libraryFolderAddedSuccessMessage => 'Папка библиотеки успешно добавлена';

  @override
  String get removeLibraryFolderTitle => 'Удалить папку?';

  @override
  String get removeLibraryFolderMessage => 'Вы уверены, что хотите удалить эту папку из библиотеки? Это не удалит файлы, только прекратит сканирование этой папки.';

  @override
  String get remove => 'Удалить';

  @override
  String get libraryFolderRemovedSuccessMessage => 'Папка библиотеки успешно удалена';

  @override
  String get libraryFolderRemoveFailedMessage => 'Не удалось удалить папку библиотеки';

  @override
  String get migratingLibraryFolderMessage => 'Перенос файлов...';

  @override
  String get migrationCompletedSuccessMessage => 'Перенос успешно завершен';

  @override
  String get migrationFailedMessage => 'Перенос не удался';

  @override
  String get deleteConfirmationTitle => 'Удалить аудиокнигу?';

  @override
  String get deleteConfirmationMessage => 'Вы уверены, что хотите удалить эту аудиокнигу?';

  @override
  String get deleteWarningMessage => 'Файлы будут безвозвратно удалены и не могут быть восстановлены.';

  @override
  String get deleteFilesButton => 'Удалить файлы';

  @override
  String get removeFromLibraryButton => 'Удалить из библиотеки';

  @override
  String get removeFromLibraryTitle => 'Удалить из библиотеки?';

  @override
  String get removeFromLibraryMessage => 'Это удалит аудиокнигу из вашей библиотеки, но не удалит файлы. Вы можете добавить её обратно, выполнив повторное сканирование.';

  @override
  String get removingFromLibraryMessage => 'Удаление...';

  @override
  String get removedFromLibrarySuccessMessage => 'Успешно удалено из библиотеки';

  @override
  String get removeFromLibraryFailedMessage => 'Не удалось удалить из библиотеки';

  @override
  String get deletingMessage => 'Удаление...';

  @override
  String get deletedSuccessMessage => 'Файлы успешно удалены';

  @override
  String get deleteFailedMessage => 'Не удалось удалить файлы';

  @override
  String get fileInUseMessage => 'Нельзя удалить: файл сейчас воспроизводится';

  @override
  String get showInfoButton => 'Показать информацию';

  @override
  String get path => 'Путь';

  @override
  String get fileCount => 'Файлы';

  @override
  String get totalSize => 'Общий размер';

  @override
  String get audiobookGroupName => 'Группа';

  @override
  String get storageManagementTitle => 'Управление хранилищем';

  @override
  String get deleteSelectedButton => 'Удалить выбранные';

  @override
  String get storageSummaryTitle => 'Сводка хранилища';

  @override
  String get totalLibrarySize => 'Общий размер библиотеки';

  @override
  String get audiobookGroupsCount => 'Группы аудиокниг';

  @override
  String get cacheSize => 'Размер кэша';

  @override
  String get libraryFoldersTitle => 'Папки библиотеки';

  @override
  String get cacheSectionTitle => 'Кэш';

  @override
  String get totalCacheSize => 'Общий кэш';

  @override
  String get clearPlaybackCacheButton => 'Очистить кэш воспроизведения';

  @override
  String get audiobookGroupsTitle => 'Группы аудиокниг';

  @override
  String get deselectAllButton => 'Снять выделение';

  @override
  String get selectAllButton => 'Выбрать все';

  @override
  String get noAudiobooksMessage => 'Аудиокниги не найдены';

  @override
  String get deleteSelectedTitle => 'Удалить выбранные?';

  @override
  String deleteSelectedMessage(int count) {
    return 'Вы уверены, что хотите удалить $count выбранную(ых) аудиокнигу(и)?';
  }

  @override
  String deleteSelectedResultMessage(int success, int failed) {
    return 'Удалено: $success, Не удалось: $failed';
  }

  @override
  String get clearPlaybackCacheTitle => 'Очистить кэш воспроизведения?';

  @override
  String get clearPlaybackCacheMessage => 'Это очистит кэш воспроизведения. Воспроизведение может быть медленнее, пока кэш не будет восстановлен.';

  @override
  String get clearingCacheMessage => 'Очистка кэша...';

  @override
  String get cacheClearedSuccessMessage => 'Кэш успешно очищен';

  @override
  String get clearAllCacheTitle => 'Очистить весь кэш?';

  @override
  String get clearAllCacheMessage => 'Это очистит весь кэш, включая кэш воспроизведения, временные файлы и старые логи.';

  @override
  String get storageManagementDescription => 'Управление размером библиотеки, кэшем и файлами';

  @override
  String get openStorageManagementButton => 'Открыть управление хранилищем';

  @override
  String partialDeletionSuccessMessage(int deleted, int total) {
    return 'Частично удалено: $deleted/$total файлов';
  }

  @override
  String get showDetailsButton => 'Детали';

  @override
  String get permissionDeniedDeletionMessage => 'Доступ запрещен: Нельзя удалить файл';

  @override
  String get deletionDetailsTitle => 'Детали удаления';

  @override
  String deletionSummaryMessage(int deleted, int total) {
    return 'Удалено: $deleted/$total файлов';
  }

  @override
  String get errorsTitle => 'Ошибки:';

  @override
  String get retryButton => 'Повторить';

  @override
  String get backgroundWorkTitle => 'Фоновая работа';

  @override
  String get refreshDiagnostics => 'Обновить диагностику';

  @override
  String get deviceInformation => 'Информация об устройстве';

  @override
  String get manufacturer => 'Производитель';

  @override
  String get customRom => 'Оболочка';

  @override
  String get androidVersion => 'Версия Android';

  @override
  String get backgroundActivityMode => 'Режим фоновой активности';

  @override
  String get compatibilityOk => 'Совместимость: ОК';

  @override
  String get issuesDetected => 'Обнаружены проблемы';

  @override
  String get detectedIssues => 'Обнаруженные проблемы:';

  @override
  String get recommendations => 'Рекомендации';

  @override
  String get manufacturerSettings => 'Настройки производителя';

  @override
  String get manufacturerSettingsDescription => 'Для стабильной работы приложения в фоне необходимо настроить параметры производителя устройства.';

  @override
  String get autostartApp => 'Автозапуск приложения';

  @override
  String get enableAutostart => 'Включите автозапуск для стабильной работы';

  @override
  String get batteryOptimization => 'Оптимизация батареи';

  @override
  String get disableBatteryOptimization => 'Отключите оптимизацию батареи для приложения';

  @override
  String get backgroundActivity => 'Фоновая активность';

  @override
  String get allowBackgroundActivity => 'Разрешите фоновую активность';

  @override
  String get showInstructions => 'Показать инструкции';

  @override
  String get workManagerDiagnostics => 'Диагностика WorkManager';

  @override
  String get lastExecutions => 'Последние выполнения:';

  @override
  String get executionHistoryEmpty => 'История выполнения задач пока пуста';

  @override
  String get total => 'Всего';

  @override
  String get successful => 'Успешно';

  @override
  String get errors => 'Ошибок';

  @override
  String get avgDelay => 'Ср. задержка';

  @override
  String get minutesAbbr => 'мин';

  @override
  String delay(int minutes) {
    return 'Задержка: $minutes мин';
  }

  @override
  String errorLabel(String reason) {
    return 'Ошибка: $reason';
  }

  @override
  String appStandbyBucketRestricted(String bucket) {
    return 'Приложение находится в режиме ограниченной фоновой активности (Standby Bucket: $bucket)';
  }

  @override
  String get useAppMoreFrequently => 'Используйте приложение чаще, чтобы система перевела его в активный режим';

  @override
  String aggressiveBatteryOptimization(String manufacturer, String rom) {
    return 'Обнаружено устройство от производителя с агрессивной оптимизацией батареи ($manufacturer, $rom)';
  }

  @override
  String get configureAutostartAndBattery => 'Рекомендуется настроить автозапуск и отключить оптимизацию батареи для приложения';

  @override
  String get openManufacturerSettings => 'Откройте настройки производителя через меню приложения';

  @override
  String get android14ForegroundServices => 'На Android 14+ убедитесь, что Foreground Services запускаются корректно';

  @override
  String get compatibilityIssuesDetected => 'Обнаружены проблемы с фоновой работой приложения';

  @override
  String workManagerTaskDelayed(String taskName, int hours, int minutes) {
    return 'Задача WorkManager \"$taskName\" задержана на $hours часов (ожидалось: $minutes минут)';
  }

  @override
  String foregroundServiceKilled(String serviceName) {
    return 'Foreground Service \"$serviceName\" был неожиданно завершён системой';
  }

  @override
  String get standbyBucketActive => 'Активно';

  @override
  String get standbyBucketWorkingSet => 'Часто используется';

  @override
  String get standbyBucketFrequent => 'Регулярно';

  @override
  String get standbyBucketRare => 'Редко';

  @override
  String get standbyBucketNever => 'Никогда (ограничено)';

  @override
  String standbyBucketUnknown(int bucket) {
    return 'Неизвестно ($bucket)';
  }

  @override
  String get standbyBucketActiveUsed => 'Активно используется';

  @override
  String get standbyBucketFrequentlyUsed => 'Часто используется';

  @override
  String get standbyBucketRegularlyUsed => 'Регулярно используется';

  @override
  String get standbyBucketRarelyUsed => 'Редко используется';

  @override
  String get standbyBucketNeverUsed => 'Никогда не используется (ограничено)';

  @override
  String get manufacturerSettingsTitle => 'Настройки для стабильной работы';

  @override
  String get manufacturerSettingsDialogDescription => 'Для стабильной работы приложения необходимо настроить следующие параметры:';

  @override
  String get enableAutostartStep => '1. Включите автозапуск приложения';

  @override
  String get disableBatteryOptimizationStep => '2. Отключите оптимизацию батареи для приложения';

  @override
  String get allowBackgroundActivityStep => '3. Разрешите фоновую активность';

  @override
  String detectedRom(String rom) {
    return 'Обнаружена оболочка: $rom';
  }

  @override
  String get skip => 'Пропустить';

  @override
  String get gotIt => 'Понятно';

  @override
  String get backgroundCompatibilityBannerTitle => 'Настройки фоновой работы';

  @override
  String get backgroundCompatibilityBannerMessage => 'Для стабильной работы приложения в фоне может потребоваться настройка параметров устройства.';

  @override
  String get dismiss => 'Закрыть';

  @override
  String get loginSuccessfulMessage => 'Авторизация успешна!';

  @override
  String get openSettingsButton => 'Открыть настройки';

  @override
  String get compatibilityDiagnosticsTitle => 'Совместимость и диагностика';

  @override
  String get compatibilityDiagnosticsSubtitle => 'Проверка совместимости и настройка параметров производителя';

  @override
  String get selectFolderButton => 'Выбрать папку';

  @override
  String get continueButton => 'Продолжить';

  @override
  String errorAddingFolder(String error) {
    return 'Ошибка добавления папки: \$error';
  }

  @override
  String get noAudiobookGroupProvided => 'Группа аудиокниг не предоставлена';

  @override
  String get sleepTimerPaused => 'Таймер сна: Воспроизведение приостановлено';

  @override
  String failedToChangeSpeed(String error) {
    return 'Не удалось изменить скорость: \$error';
  }

  @override
  String get cancelTimerButton => 'Отменить таймер';

  @override
  String get endOfChapterLabel => 'Конец главы';

  @override
  String get atEndOfChapterLabel => 'В конце главы';

  @override
  String sleepTimerTooltip(String duration) {
    return 'Таймер сна: $duration';
  }

  @override
  String get setSleepTimerTooltip => 'Установить таймер сна';

  @override
  String sleepTimerMinutes(int minutes) {
    final intl.NumberFormat minutesNumberFormat = intl.NumberFormat.compact(
      locale: localeName,
      
    );
    final String minutesString = minutesNumberFormat.format(minutes);

    return '$minutesString мин.';
  }

  @override
  String get sleepTimerHour => '1 ч.';

  @override
  String get sleepTimerAppWillExit => 'Таймер сна: Приложение закроется';

  @override
  String get itemsInTrashLabel => 'Элементы в корзине';

  @override
  String get trashSizeLabel => 'Размер корзины';

  @override
  String get manageTrashButton => 'Управление корзиной';

  @override
  String get fileLabel => 'файл';

  @override
  String get filesLabel => 'файлов';

  @override
  String failedToLoadTrash(String error) {
    return 'Не удалось загрузить корзину: \$error';
  }

  @override
  String get itemRestoredSuccessfully => 'Элемент успешно восстановлен';

  @override
  String get failedToRestoreItem => 'Не удалось восстановить элемент';

  @override
  String errorRestoringItem(String error) {
    return 'Ошибка восстановления элемента: \$error';
  }

  @override
  String get permanentlyDeleteTitle => 'Удалить навсегда';

  @override
  String permanentlyDeleteMessage(String name) {
    return 'Вы уверены, что хотите безвозвратно удалить \"$name\"? Это действие нельзя отменить.';
  }

  @override
  String get itemPermanentlyDeleted => 'Элемент безвозвратно удален';

  @override
  String get failedToDeleteItem => 'Не удалось удалить элемент';

  @override
  String errorDeletingItem(String error) {
    return 'Ошибка удаления элемента: \$error';
  }

  @override
  String get clearAllTrashTitle => 'Очистить всю корзину';

  @override
  String get clearAllTrashMessage => 'Вы уверены, что хотите безвозвратно удалить все элементы в корзине? Это действие нельзя отменить.';

  @override
  String get filePickerAlreadyOpen => 'Файловый менеджер уже открыт. Пожалуйста, закройте его сначала.';

  @override
  String get applyButton => 'Применить';

  @override
  String get lastMessageSort => 'Посл. сообщение';

  @override
  String get topicNameSort => 'Название темы';

  @override
  String get postingTimeSort => 'Время размещения';

  @override
  String get operationTimedOut => 'Операция превысила время ожидания. Пожалуйста, попробуйте снова.';

  @override
  String get permissionDeniedDownloads => 'Доступ запрещен. Пожалуйста, проверьте разрешения приложения в настройках.';

  @override
  String get downloadNotFound => 'Загрузка не найдена. Возможно, она была удалена.';

  @override
  String get anErrorOccurred => 'Произошла ошибка. Пожалуйста, попробуйте снова.';

  @override
  String initializationError(String error) {
    return 'Ошибка инициализации: \$error';
  }

  @override
  String criticalError(String error) {
    return 'Критическая ошибка: \$error';
  }

  @override
  String capabilityCheckError(String error) {
    return 'Ошибка проверки возможностей: \$error';
  }

  @override
  String filesSelected(int count) {
    return 'Выбрано файлов: \$count';
  }

  @override
  String fileSelectionError(String error) {
    return 'Ошибка выбора файлов: \$error';
  }

  @override
  String imagesSelected(int count) {
    return 'Выбрано изображений: \$count';
  }

  @override
  String imageSelectionError(String error) {
    return 'Ошибка выбора изображений: \$error';
  }

  @override
  String get testNotificationTitle => 'Тест уведомления';

  @override
  String get testNotificationBody => 'Это тестовое уведомление от JaBook';

  @override
  String get notificationSent => 'Уведомление отправлено';

  @override
  String get failedToSendNotification => 'Не удалось отправить уведомление (канал не реализован)';

  @override
  String bluetoothAvailable(String available) {
    return 'Bluetooth доступен: \$available';
  }

  @override
  String pairedDevicesCount(int count) {
    return 'Сопряженных устройств: \$count';
  }

  @override
  String bluetoothCheckError(String error) {
    return 'Ошибка проверки Bluetooth: \$error';
  }

  @override
  String get systemCapabilitiesTitle => 'Системные возможности';

  @override
  String get fileAccessCapability => 'Доступ к файлам';

  @override
  String get imageAccessCapability => 'Доступ к изображениям';

  @override
  String get cameraCapability => 'Камера';

  @override
  String photoTaken(String path) {
    return 'Фото сделано: \$path';
  }

  @override
  String get photoNotTaken => 'Фото не сделано';

  @override
  String cameraError(String error) {
    return 'Ошибка камеры: \$error';
  }

  @override
  String get notificationsCapability => 'Уведомления';

  @override
  String get capabilityExplanationButton => 'Объяснение возможностей';

  @override
  String get testButton => 'Тест';

  @override
  String get permissionsForJaBookTitle => 'Разрешения для JaBook';

  @override
  String get fileAccessPermissionTitle => 'Доступ к файлам';

  @override
  String get fileAccessPermissionDescription => 'Нужен для сохранения и воспроизведения аудиокниг. На Android 11+ потребуется включить \"Доступ ко всем файлам\" в настройках системы.';

  @override
  String get notificationsPermissionTitle => 'Уведомления';

  @override
  String get batteryOptimizationPermissionTitle => 'Оптимизация батареи';

  @override
  String get batteryOptimizationPermissionDescription => 'Чтобы приложение работало в фоне для воспроизведения.';

  @override
  String get permissionsHelpMessage => 'Эти разрешения помогут обеспечить лучший опыт использования приложения.';

  @override
  String urlLabel(String url) {
    return 'URL: \$url';
  }

  @override
  String get rutrackerTitle => 'RuTracker';

  @override
  String get webViewLoginButton => 'Войти в RuTracker через WebView';

  @override
  String get webViewLoginSubtitle => 'Пройти Cloudflare/капчу и сохранить cookie для клиента';

  @override
  String get cookieSavedMessage => 'Cookie сохранены для HTTP-клиента';

  @override
  String get failedToParseSearchResultsEncoding => 'Не удалось распарсить результаты поиска из-за проблемы с кодировкой. Это может быть временная проблема сервера. Пожалуйста, попробуйте снова. Если проблема сохраняется, попробуйте изменить зеркало в Настройках → Источники.';

  @override
  String get failedToParseSearchResultsStructure => 'Не удалось распарсить результаты поиска. Структура страницы могла измениться. Пожалуйста, попробуйте снова. Если проблема сохраняется, попробуйте изменить зеркало в Настройках → Источники.';

  @override
  String searchFailedMessage(String errorType) {
    return 'Поиск не удался: $errorType';
  }

  @override
  String get topicTitleSort => 'Название темы';

  @override
  String get postTimeSort => 'Время размещения';

  @override
  String localStreamServerStarted(String host, int port) {
    return 'Локальный сервер потоковой передачи запущен на http://$host:$port';
  }

  @override
  String failedToStartStreamServer(String error) {
    return 'Не удалось запустить сервер потоковой передачи: $error';
  }

  @override
  String get localStreamServerStopped => 'Локальный сервер потоковой передачи остановлен';

  @override
  String failedToStopStreamServer(String error) {
    return 'Не удалось остановить сервер потоковой передачи: $error';
  }

  @override
  String get missingBookIdParameter => 'Отсутствует параметр ID книги';

  @override
  String get invalidFileIndexParameter => 'Неверный параметр индекса файла';

  @override
  String get fileNotFound => 'Файл не найден';

  @override
  String get streamingError => 'Ошибка потоковой передачи';

  @override
  String get invalidRangeHeader => 'Неверный заголовок диапазона';

  @override
  String get requestedRangeNotSatisfiable => 'Запрошенный диапазон не может быть выполнен';

  @override
  String get rangeRequestError => 'Ошибка запроса диапазона';

  @override
  String get staticFileError => 'Ошибка статического файла';

  @override
  String failedToStartAudioService(String error) {
    return 'Не удалось запустить аудио сервис: $error';
  }

  @override
  String failedToPlayMedia(String error) {
    return 'Не удалось воспроизвести медиа: $error';
  }

  @override
  String failedToPauseMedia(String error) {
    return 'Не удалось приостановить медиа: $error';
  }

  @override
  String failedToStopMedia(String error) {
    return 'Не удалось остановить медиа: $error';
  }

  @override
  String failedToSeek(String error) {
    return 'Не удалось перейти к позиции: $error';
  }

  @override
  String failedToSetSpeed(String error) {
    return 'Не удалось установить скорость: $error';
  }

  @override
  String get failedToInitializeLogger => 'Не удалось инициализировать логгер';

  @override
  String get failedToWriteLog => 'Не удалось записать лог';

  @override
  String get logRotationFailed => 'Не удалось выполнить ротацию логов';

  @override
  String get failedToRotateLogs => 'Не удалось выполнить ротацию логов';

  @override
  String get failedToCleanOldLogs => 'Не удалось очистить старые логи';

  @override
  String errorSharingLogs(String error) {
    return 'Ошибка при отправке логов: $error';
  }

  @override
  String get failedToShareLogs => 'Не удалось отправить логи';

  @override
  String get failedToReadLogs => 'Не удалось прочитать логи';

  @override
  String get cacheManagerNotInitialized => 'CacheManager не инициализирован';

  @override
  String failedToParseSearchResults(String error) {
    return 'Не удалось распарсить результаты поиска: $error';
  }

  @override
  String failedToParseTopicDetails(String error) {
    return 'Не удалось распарсить детали темы: $error';
  }

  @override
  String get failedToParseCategories => 'Не удалось распарсить категории';

  @override
  String get failedToParseCategoryTopics => 'Не удалось распарсить темы категории';

  @override
  String get radioPlayCategory => 'Радиоспектакль';

  @override
  String get audiobookCategory => 'Аудиокнига';

  @override
  String get biographyCategory => 'Биография';

  @override
  String get memoirsCategory => 'Мемуары';

  @override
  String get historyCategory => 'История';

  @override
  String get addedLabel => 'Добавлено';

  @override
  String get invalidMagnetUrlMissingHash => 'Неверный magnet URL: отсутствует info hash';

  @override
  String get invalidInfoHashLength => 'Неверная длина info hash';

  @override
  String failedToStartDownloadWithError(String error) {
    return 'Не удалось начать загрузку: $error';
  }

  @override
  String get downloadNotFoundTorrent => 'Загрузка не найдена';

  @override
  String failedToPauseDownload(String error) {
    return 'Не удалось приостановить загрузку: $error';
  }

  @override
  String failedToResumeDownload(String error) {
    return 'Не удалось возобновить загрузку: $error';
  }

  @override
  String failedToRemoveDownload(String error) {
    return 'Не удалось удалить загрузку: $error';
  }

  @override
  String failedToGetActiveDownloads(String error) {
    return 'Не удалось получить активные загрузки: $error';
  }

  @override
  String failedToShutdownTorrentManager(String error) {
    return 'Не удалось остановить менеджер торрентов: $error';
  }

  @override
  String get noHealthyEndpointsAvailable => 'Нет доступных здоровых конечных точек';

  @override
  String get authRepositoryProviderMustBeOverridden => 'AuthRepositoryProvider должен быть переопределен с правильным контекстом';

  @override
  String get useEndpointManagerGetActiveEndpoint => 'Используйте EndpointManager.getActiveEndpoint() для динамического выбора зеркала';

  @override
  String get cacheManagerNotInitializedConfig => 'CacheManager не инициализирован';

  @override
  String searchFailedWithMessage(String message) {
    return 'Поиск не удался: $message';
  }

  @override
  String get failedToSearchAudiobooks => 'Не удалось выполнить поиск аудиокниг';

  @override
  String failedToFetchCategories(String message) {
    return 'Не удалось получить категории: $message';
  }

  @override
  String get failedToGetCategories => 'Не удалось получить категории';

  @override
  String failedToGetCategoryAudiobooksWithMessage(String message) {
    return 'Не удалось получить аудиокниги категории: $message';
  }

  @override
  String get failedToGetCategoryAudiobooks => 'Не удалось получить аудиокниги категории';

  @override
  String failedToFetchAudiobookDetails(String message) {
    return 'Не удалось получить детали аудиокниги: $message';
  }

  @override
  String get failedToGetAudiobookDetails => 'Не удалось получить детали аудиокниги';

  @override
  String failedToFetchNewReleases(String message) {
    return 'Не удалось получить новые релизы: $message';
  }

  @override
  String failedToSaveCredentials(String error) {
    return 'Не удалось сохранить учетные данные: $error';
  }

  @override
  String failedToRetrieveCredentials(String error) {
    return 'Не удалось получить учетные данные: $error';
  }

  @override
  String failedToClearCredentials(String error) {
    return 'Не удалось очистить учетные данные: $error';
  }

  @override
  String get noCredentialsToExport => 'Нет учетных данных для экспорта';

  @override
  String unsupportedExportFormat(String format) {
    return 'Неподдерживаемый формат экспорта: $format';
  }

  @override
  String get invalidCsvFormat => 'Неверный формат CSV';

  @override
  String get invalidCsvData => 'Неверные данные CSV';

  @override
  String get invalidJsonFormat => 'Неверный формат JSON';

  @override
  String unsupportedImportFormat(String format) {
    return 'Неподдерживаемый формат импорта: $format';
  }

  @override
  String failedToImportCredentials(String error) {
    return 'Не удалось импортировать учетные данные: $error';
  }

  @override
  String errorWithDetails(String error) {
    return 'Ошибка: $error';
  }

  @override
  String get fileSingular => 'файл';

  @override
  String get filePlural => 'файлов';

  @override
  String deleteSelectedAudiobooksConfirmation(int count) {
    return 'Вы уверены, что хотите удалить $count выбранную(ых) аудиокнигу(и)?';
  }

  @override
  String get clearPlaybackCacheDescription => 'Это очистит кэш воспроизведения. Воспроизведение может быть медленнее, пока кэш не будет восстановлен.';

  @override
  String get clearAllCacheDescription => 'Это очистит весь кэш, включая кэш воспроизведения, временные файлы и старые логи.';

  @override
  String get sessionExpiredTitle => 'Сессия истекла';

  @override
  String get invalidCredentialsTitle => 'Ошибка авторизации';

  @override
  String get invalidCredentialsMessage => 'Неверный логин или пароль. Проверьте введенные данные.';

  @override
  String get loginRequiredTitle => 'Требуется авторизация';

  @override
  String get loginRequiredMessage => 'Для выполнения этого действия необходимо войти в систему.';

  @override
  String get authorizationErrorTitle => 'Ошибка авторизации';

  @override
  String get accessDeniedMessage => 'Доступ запрещен. Проверьте правильность учетных данных или войдите снова.';

  @override
  String get networkErrorTitle => 'Ошибка сети';

  @override
  String get networkRequestFailedMessage => 'Не удалось выполнить запрос. Проверьте подключение к интернету.';

  @override
  String get errorOccurredMessage => 'Произошла ошибка при выполнении операции.';

  @override
  String get sessionExpiredSnackBar => 'Сессия истекла. Пожалуйста, войдите снова.';

  @override
  String get invalidCredentialsSnackBar => 'Неверный логин или пароль.';

  @override
  String get authorizationErrorSnackBar => 'Ошибка авторизации. Проверьте учетные данные.';

  @override
  String get networkErrorSnackBar => 'Ошибка сети. Проверьте подключение.';

  @override
  String get captchaVerificationRequired => 'Требуется проверка капчи. Пожалуйста, попробуйте позже.';

  @override
  String get networkErrorCheckConnection => 'Ошибка сети. Пожалуйста, проверьте подключение и попробуйте снова.';

  @override
  String get authenticationFailedMessage => 'Ошибка аутентификации. Пожалуйста, проверьте учетные данные и попробуйте снова.';

  @override
  String loginFailedWithError(String error) {
    return 'Вход не удался: $error';
  }

  @override
  String get noAccessibleAudioFiles => 'Не найдено доступных аудиофайлов';

  @override
  String get aboutTitle => 'О приложении';

  @override
  String get aboutAppSlogan => 'Современный аудиоплеер для торрентов';

  @override
  String get appVersion => 'Версия приложения';

  @override
  String get buildNumber => 'Сборка';

  @override
  String get packageId => 'ID пакета';

  @override
  String get licenseTitle => 'Лицензия';

  @override
  String get licenseDescription => 'Приложение JaBook распространяется по лицензии Apache 2.0. Нажмите, чтобы посмотреть полный текст лицензии и список сторонних библиотек.';

  @override
  String get mainLicense => 'Основная лицензия';

  @override
  String get thirdPartyLicenses => 'Сторонние библиотеки';

  @override
  String get telegramChannel => 'Telegram канал';

  @override
  String get openTelegramChannel => 'Открыть канал в Telegram';

  @override
  String get contactDeveloper => 'Написать разработчику';

  @override
  String get openEmailClient => 'Открыть почтовый клиент';

  @override
  String get supportProject => 'Поддержать проект';

  @override
  String get supportProjectDescription => 'Пожертвовать или поддержать разработку';

  @override
  String get githubRepository => 'GitHub';

  @override
  String get githubRepositoryDescription => 'Исходный код и список изменений';

  @override
  String get changelog => 'Список изменений';

  @override
  String get changelogDescription => 'История версий и обновлений';

  @override
  String get issues => 'Issues';

  @override
  String get issuesDescription => 'Сообщить об ошибках и запросить функции';

  @override
  String get aboutDeveloper => 'О разработчике';

  @override
  String get aboutDeveloperText => 'JaBook разработан командой Jabook Contributors. Это open source проект, созданный для удобного прослушивания аудиокниг из открытых источников и торрентов. Без рекламы, без регистрации — простое прослушивание.';

  @override
  String get copyInfo => 'Скопировать';

  @override
  String get infoCopied => 'Информация скопирована';

  @override
  String get failedToOpenLink => 'Не удалось открыть ссылку';

  @override
  String get failedToOpenEmail => 'Не удалось открыть почтовый клиент';

  @override
  String get viewInApp => 'Просмотр в приложении';

  @override
  String get viewLicenses => 'Просмотр лицензий';

  @override
  String get licenseOnGitHub => 'Лицензия на GitHub';

  @override
  String get viewLicenseFile => 'Просмотр файла LICENSE';

  @override
  String get appDiscussionQuestionsReviews => 'Обсуждение приложения, вопросы и отзывы';

  @override
  String get jabookContributors => 'Jabook Contributors';

  @override
  String get github => 'GitHub';

  @override
  String get versionInformationLongPress => 'Информация о версии. Долгое нажатие для копирования.';

  @override
  String get unknown => 'Неизвестно';

  @override
  String get emailFeedbackSubject => 'JaBook – обратная связь';

  @override
  String get emailFeedbackApp => 'Приложение: JaBook';

  @override
  String get emailFeedbackVersion => 'Версия:';

  @override
  String get emailFeedbackDevice => 'Устройство:';

  @override
  String get emailFeedbackAndroid => 'Android:';

  @override
  String get emailFeedbackDescription => 'Описание проблемы / предложения:';

  @override
  String get aboutSectionDescription => 'Информация о приложении и ссылки';

  @override
  String get aboutSectionSubtitle => 'Версия, лицензия и информация о разработчике';

  @override
  String get forum4Pda => '4PDA';

  @override
  String get rewindDurationTitle => 'Длительность перемотки назад';

  @override
  String get forwardDurationTitle => 'Длительность перемотки вперед';

  @override
  String get inactivityTimeoutTitle => 'Таймаут неактивности';

  @override
  String get inactivityTimeoutLabel => 'Установить таймаут неактивности';

  @override
  String get minute => 'минута';

  @override
  String get rewind => 'Назад';

  @override
  String get forward => 'Вперед';

  @override
  String get currentPosition => 'Текущая';

  @override
  String get newPosition => 'Новая';

  @override
  String get secondsLabel => 'секунд';

  @override
  String get languageSettingsLabel => 'Настройки языка';

  @override
  String get mirrorSourceSettingsLabel => 'Настройки зеркал и источников';

  @override
  String get rutrackerSessionLabel => 'Управление сессией RuTracker';

  @override
  String get metadataManagementLabel => 'Управление метаданными';

  @override
  String get themeSettingsLabel => 'Настройки темы';

  @override
  String get audioPlaybackSettingsLabel => 'Настройки воспроизведения аудио';

  @override
  String get downloadSettingsLabel => 'Настройки загрузки';

  @override
  String get libraryFolderSettingsLabel => 'Настройки папки библиотеки';

  @override
  String get storageManagementLabel => 'Управление хранилищем';

  @override
  String get cacheSettingsLabel => 'Настройки кэша';

  @override
  String get appPermissionsLabel => 'Разрешения приложения';

  @override
  String get aboutAppLabel => 'О приложении';

  @override
  String get backgroundTaskCompatibilityLabel => 'Совместимость фоновых задач';

  @override
  String get backupRestoreLabel => 'Резервное копирование и восстановление';

  @override
  String get selectFavorites => 'Выбрать';

  @override
  String get clearSelectedFavorites => 'Удалить выбранные';

  @override
  String get clearAllFavorites => 'Очистить все';

  @override
  String get clearAllFavoritesTitle => 'Очистить все закладки?';

  @override
  String get clearAllFavoritesMessage => 'Это удалит все избранные аудиокниги. Это действие нельзя отменить.';

  @override
  String get favoritesCleared => 'Закладки очищены';

  @override
  String favoritesDeleted(int count) {
    return 'Удалено закладок: $count';
  }

  @override
  String get noFavoritesSelected => 'Закладки не выбраны';

  @override
  String get selected => 'выбрано';

  @override
  String get sortByLabel => 'Сортировать по:';

  @override
  String get groupByLabel => 'Группировать по:';

  @override
  String get sortByNameAsc => 'Имя (А-Я)';

  @override
  String get sortByNameDesc => 'Имя (Я-А)';

  @override
  String get sortBySizeAsc => 'Размер (Меньший)';

  @override
  String get sortBySizeDesc => 'Размер (Больший)';

  @override
  String get sortByDateAsc => 'Дата (Старые)';

  @override
  String get sortByDateDesc => 'Дата (Новые)';

  @override
  String get sortByFilesAsc => 'Файлы (Меньше)';

  @override
  String get sortByFilesDesc => 'Файлы (Больше)';

  @override
  String get groupByNone => 'Нет';

  @override
  String get groupByFirstLetter => 'Первая буква';

  @override
  String get scanningLibrary => 'Сканирование библиотеки...';

  @override
  String get manufacturerSettingsNotAvailable => 'Настройки недоступны';

  @override
  String get manufacturerSettingsNotAvailableMessage => 'Настройки производителя доступны только на устройствах Android.';

  @override
  String get manufacturerSettingsDefaultTitle => 'Настройки для стабильной работы';

  @override
  String get manufacturerSettingsDefaultMessage => 'Для стабильной работы приложения необходимо настроить следующие параметры:';

  @override
  String get manufacturerSettingsDefaultStep1 => '1. Включите автозапуск приложения';

  @override
  String get manufacturerSettingsDefaultStep2 => '2. Отключите оптимизацию батареи для приложения';

  @override
  String get manufacturerSettingsDefaultStep3 => '3. Разрешите фоновую активность';

  @override
  String get manufacturerSettingsMiuiTitle => 'Настройки MIUI для стабильной работы';

  @override
  String get manufacturerSettingsMiuiMessage => 'На устройствах Xiaomi/Redmi/Poco необходимо настроить следующие параметры:';

  @override
  String manufacturerSettingsMiuiStep1(String appName) {
    return '1. Автозапуск: Настройки → Приложения → Управление разрешениями → Автозапуск → Включите для $appName';
  }

  @override
  String manufacturerSettingsMiuiStep2(String appName) {
    return '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → Выберите $appName → Не оптимизировать';
  }

  @override
  String manufacturerSettingsMiuiStep3(String appName) {
    return '3. Фоновая активность: Настройки → Приложения → $appName → Батарея → Фоновая активность → Разрешить';
  }

  @override
  String get manufacturerSettingsEmuiTitle => 'Настройки EMUI для стабильной работы';

  @override
  String get manufacturerSettingsEmuiMessage => 'На устройствах Huawei/Honor необходимо настроить следующие параметры:';

  @override
  String manufacturerSettingsEmuiStep1(String appName) {
    return '1. Защита приложений: Настройки → Приложения → Защита приложений → $appName → Включите автозапуск';
  }

  @override
  String manufacturerSettingsEmuiStep2(String appName) {
    return '2. Управление батареей: Настройки → Батарея → Управление батареей → $appName → Не оптимизировать';
  }

  @override
  String manufacturerSettingsEmuiStep3(String appName) {
    return '3. Фоновая активность: Настройки → Приложения → $appName → Батарея → Разрешить фоновую активность';
  }

  @override
  String get manufacturerSettingsColorosTitle => 'Настройки ColorOS/RealmeUI для стабильной работы';

  @override
  String get manufacturerSettingsColorosMessage => 'На устройствах Oppo/Realme необходимо настроить следующие параметры:';

  @override
  String manufacturerSettingsColorosStep1(String appName) {
    return '1. Автозапуск: Настройки → Приложения → Автозапуск → Включите для $appName';
  }

  @override
  String manufacturerSettingsColorosStep2(String appName) {
    return '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → $appName → Не оптимизировать';
  }

  @override
  String manufacturerSettingsColorosStep3(String appName) {
    return '3. Фоновая активность: Настройки → Приложения → $appName → Батарея → Разрешить фоновую активность';
  }

  @override
  String get manufacturerSettingsOxygenosTitle => 'Настройки OxygenOS для стабильной работы';

  @override
  String get manufacturerSettingsOxygenosMessage => 'На устройствах OnePlus необходимо настроить следующие параметры:';

  @override
  String manufacturerSettingsOxygenosStep1(String appName) {
    return '1. Автозапуск: Настройки → Приложения → Автозапуск → Включите для $appName';
  }

  @override
  String manufacturerSettingsOxygenosStep2(String appName) {
    return '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → $appName → Не оптимизировать';
  }

  @override
  String manufacturerSettingsOxygenosStep3(String appName) {
    return '3. Фоновая активность: Настройки → Приложения → $appName → Батарея → Разрешить фоновую активность';
  }

  @override
  String get manufacturerSettingsFuntouchosTitle => 'Настройки FuntouchOS/OriginOS для стабильной работы';

  @override
  String get manufacturerSettingsFuntouchosMessage => 'На устройствах Vivo необходимо настроить следующие параметры:';

  @override
  String manufacturerSettingsFuntouchosStep1(String appName) {
    return '1. Автозапуск: Настройки → Приложения → Автозапуск → Включите для $appName';
  }

  @override
  String manufacturerSettingsFuntouchosStep2(String appName) {
    return '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → $appName → Не оптимизировать';
  }

  @override
  String manufacturerSettingsFuntouchosStep3(String appName) {
    return '3. Фоновая активность: Настройки → Приложения → $appName → Батарея → Разрешить фоновую активность';
  }

  @override
  String get manufacturerSettingsFlymeTitle => 'Настройки Flyme для стабильной работы';

  @override
  String get manufacturerSettingsFlymeMessage => 'На устройствах Meizu необходимо настроить следующие параметры:';

  @override
  String manufacturerSettingsFlymeStep1(String appName) {
    return '1. Автозапуск: Настройки → Приложения → Автозапуск → Включите для $appName';
  }

  @override
  String manufacturerSettingsFlymeStep2(String appName) {
    return '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → $appName → Не оптимизировать';
  }

  @override
  String manufacturerSettingsFlymeStep3(String appName) {
    return '3. Фоновая активность: Настройки → Приложения → $appName → Батарея → Разрешить фоновую активность';
  }

  @override
  String get manufacturerSettingsOneuiTitle => 'Настройки One UI для стабильной работы';

  @override
  String get manufacturerSettingsOneuiMessage => 'На устройствах Samsung рекомендуется настроить следующие параметры:';

  @override
  String manufacturerSettingsOneuiStep1(String appName) {
    return '1. Оптимизация батареи: Настройки → Приложения → $appName → Батарея → Не оптимизировать';
  }

  @override
  String manufacturerSettingsOneuiStep2(String appName) {
    return '2. Фоновая активность: Настройки → Приложения → $appName → Батарея → Фоновая активность → Разрешить';
  }

  @override
  String get manufacturerSettingsOneuiStep3 => '3. Автозапуск: Обычно не требуется на Samsung, но можно проверить в настройках приложения';

  @override
  String trackOfTotal(int currentTrack, int totalTracks) {
    return 'Трек $currentTrack из $totalTracks';
  }

  @override
  String tracksTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Треки',
      one: 'Трек',
      zero: 'Нет треков',
    );
    return '$_temp0';
  }

  @override
  String get noRepeat => 'Без повтора';

  @override
  String get repeatTrack => 'Повторять трек';

  @override
  String get repeatPlaylist => 'Повторять плейлист';

  @override
  String get timerLabel => 'Таймер';

  @override
  String get speedLabel => 'Скорость';

  @override
  String get rewindButton => 'Назад';

  @override
  String get forwardButton => 'Вперёд';

  @override
  String get playButton => 'Воспроизвести';

  @override
  String get pauseButton => 'Пауза';

  @override
  String get nextButton => 'Следующий';

  @override
  String get previousButton => 'Предыдущий';

  @override
  String get accessRequired => 'Требуется доступ';

  @override
  String featureRequiresAuth(String feature) {
    return 'Функция \"$feature\" требует авторизации';
  }

  @override
  String get signInToAccessFeature => 'Войдите в аккаунт, чтобы получить доступ к этой функции';

  @override
  String get upgradeToFull => 'Полный доступ';

  @override
  String get restrictedFeature => 'Ограниченная функция';

  @override
  String get featureRestricted => 'Эта функция недоступна в демо-режиме';

  @override
  String get featureRestrictedDescription => 'Для доступа к этой функции необходимо войти в аккаунт';

  @override
  String get signInToUnlock => 'Войти, чтобы разблокировать';

  @override
  String get continueAsGuest => 'Продолжить как гость';

  @override
  String get sessionExpired => 'Сессия истекла';

  @override
  String get sessionExpiredMessage => 'Ваша сессия истекла. Пожалуйста, войдите в систему снова.';

  @override
  String get signInAgain => 'Войти снова';

  @override
  String get authorizationRequired => 'Требуется авторизация';

  @override
  String get demoMode => 'Демо-режим';

  @override
  String get demoModeDescription => 'Вы используете демо-режим с ограниченной функциональностью';

  @override
  String get upgradeToFullAccess => 'Получить полный доступ';

  @override
  String get searchRestricted => 'Поиск недоступен в демо-режиме';

  @override
  String get downloadRestricted => 'Загрузки недоступны в демо-режиме';

  @override
  String get browseRestricted => 'Просмотр топиков недоступен в демо-режиме';

  @override
  String get account => 'Аккаунт';

  @override
  String get compactPlayerLayout => 'Компактный плеер';

  @override
  String get standardPlayerLayout => 'Стандартный плеер';

  @override
  String get adaptiveNavigation => 'Адаптивная навигация';

  @override
  String get smallScreenOptimizations => 'Оптимизации для маленьких экранов';
}
