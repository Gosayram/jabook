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
}
