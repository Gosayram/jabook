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
    return 'Язык изменен на \$languageName';
  }

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
}
