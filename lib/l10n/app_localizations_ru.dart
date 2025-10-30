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
  String get importedSuccess => 'Импортировано \$count аудиокнига(и)';

  @override
  String get noFilesSelectedMessage => 'Файлы не выбраны';

  @override
  String get importFailedMessage => 'Не удалось импортировать: \$error';

  @override
  String get scanFolderTitle => 'Сканировать папку';

  @override
  String get scanFolderMessage => 'Сканируйте папку на вашем устройстве в поисках файлов аудиокниг';

  @override
  String get scanButtonText => 'Сканировать';

  @override
  String get scanSuccessMessage => 'Найдено и импортировано \$count аудиокнига(и)';

  @override
  String get noAudiobooksFoundMessage => 'В выбранной папке не найдено файлов аудиокниг';

  @override
  String get noFolderSelectedMessage => 'Папка не выбрана';

  @override
  String get scanFailedMessage => 'Не удалось просканировать папку: \$error';

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
  String get loggingOutText => 'Выход из системы...';

  @override
  String get logoutSuccessMessage => 'Выполнен выход из системы';

  @override
  String get logoutErrorMessage => 'Ошибка выхода: \$error';

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
  String get priorityText => 'Приоритет: \$priority';

  @override
  String get responseTimeText => 'Время отклика: \$rtt мс';

  @override
  String get lastCheckedText => 'Последняя проверка: \$date';

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
  String get networkErrorMessage => 'Сетевая ошибка: \$error';

  @override
  String get errorLoadingTopicMessage => 'Ошибка загрузки топика: \$error';

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
  String get copyToClipboardMessage => '\$label скопирован в буфер обмена';

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
  String get authHelpText => 'Введите ваши учетные данные';

  @override
  String get cacheClearedSuccessfullyMessage => 'Кэш успешно очищен';

  @override
  String get downloadStatusUnknownMessage => 'Статус загрузки неизвестен';

  @override
  String get failedToExportLogsMessage => 'Не удалось экспортировать логи';

  @override
  String get logsExportedSuccessfullyMessage => 'Логи успешно экспортированы';

  @override
  String get mirrorHealthCheckCompletedMessage => 'Проверка здоровья зеркал завершена';
}
