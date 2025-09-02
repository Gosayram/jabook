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
  String get languageDescription => 'Выберите предпочитаемый язык для интерфейса приложения';

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
  String get themeTitle => 'Тема';

  @override
  String get themeDescription => 'Настройте внешний вид приложения';

  @override
  String get darkMode => 'Тёмная тема';

  @override
  String get highContrast => 'Высокая контрастность';

  @override
  String get audioTitle => 'Аудио';

  @override
  String get audioDescription => 'Настройте параметры воспроизведения аудио';

  @override
  String get playbackSpeed => 'Скорость воспроизведения';

  @override
  String get skipDuration => 'Пропуск';

  @override
  String get downloadsTitle => 'Загрузки';

  @override
  String get downloadsDescription => 'Управление настройками загрузок и хранилищем';

  @override
  String get downloadLocation => 'Местоположение загрузок';

  @override
  String get wifiOnlyDownloads => 'Только по Wi-Fi';

  @override
  String get debugTools => 'Инструменты отладки';

  @override
  String get logsTab => 'Логи';

  @override
  String get mirrorsTab => 'Зеркала';

  @override
  String get downloadsTab => 'Загрузки';

  @override
  String get cacheTab => 'Кэш';

  @override
  String get testAllMirrors => 'Проверить все зеркала';

  @override
  String get statusLabel => 'Статус: ';

  @override
  String get activeStatus => 'Активно';

  @override
  String get disabledStatus => 'Отключено';

  @override
  String get lastOkLabel => 'Последний OK: ';

  @override
  String get rttLabel => 'RTT: ';

  @override
  String get milliseconds => 'мс';

  @override
  String get cacheStatistics => 'Статистика кэша';

  @override
  String get totalEntries => 'Всего записей: ';

  @override
  String get searchCache => 'Кэш поиска: ';

  @override
  String get topicCache => 'Кэш топиков: ';

  @override
  String get memoryUsage => 'Использование памяти: ';

  @override
  String get clearAllCache => 'Очистить весь кэш';

  @override
  String get cacheClearedSuccessfully => 'Кэш успешно очищен';

  @override
  String get mirrorHealthCheckCompleted => 'Проверка здоровья зеркал завершена';

  @override
  String get exportFunctionalityComingSoon => 'Функция экспорта скоро появится';

  @override
  String get logsExportedSuccessfully => 'Логи успешно экспортированы';

  @override
  String get failedToExportLogs => 'Не удалось экспортировать логи';

  @override
  String get requestTimedOut => 'Время запроса истекло. Пожалуйста, проверьте соединение.';

  @override
  String networkError(Object errorMessage) {
    return 'Ошибка сети: $errorMessage';
  }

  @override
  String get downloadFunctionalityComingSoon => 'Функция загрузки скоро появится!';

  @override
  String get magnetLinkCopied => 'Magnet-ссылка скопирована в буфер обмена';

  @override
  String get dataLoadedFromCache => 'Данные загружены из кэша';

  @override
  String get failedToLoadTopic => 'Не удалось загрузить топик';

  @override
  String errorLoadingTopic(Object error) {
    return 'Ошибка загрузки топика: $error';
  }

  @override
  String get unknownChapter => 'Неизвестная глава';

  @override
  String get chaptersLabel => 'Главы';

  @override
  String get magnetLinkLabel => 'Magnet-ссылка';

  @override
  String get downloadLabel => 'Скачать';

  @override
  String get playLabel => 'Воспроизвести';

  @override
  String get pauseLabel => 'Пауза';

  @override
  String get stopLabel => 'Стоп';

  @override
  String get failedToLoadAudio => 'Не удалось загрузить аудиокнигу';

  @override
  String get sampleAudiobook => 'Пример аудиокниги';

  @override
  String get sampleAuthor => 'Примерный автор';

  @override
  String get chapter1 => 'Глава 1';

  @override
  String get chapter2 => 'Глава 2';

  @override
  String get mirrorsScreenTitle => 'Экран зеркал';

  @override
  String get downloadStatusUnknown => 'неизвестно';

  @override
  String downloadProgressLabel(Object progress) {
    return 'Прогресс: $progress%';
  }

  @override
  String get statusLabelNoColon => 'Статус';

  @override
  String copyToClipboardLabel(Object label) {
    return '$label скопировано в буфер обмена';
  }
}
