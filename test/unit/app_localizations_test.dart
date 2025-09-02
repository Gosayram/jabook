import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/l10n/app_localizations.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  
  group('AppLocalizations', () {
    test('delegate should support English and Russian locales', () {
      const delegate = AppLocalizations.delegate;
      
      expect(delegate.isSupported(const Locale('en')), isTrue);
      expect(delegate.isSupported(const Locale('ru')), isTrue);
      expect(delegate.isSupported(const Locale('fr')), isFalse);
      expect(delegate.isSupported(const Locale('de')), isFalse);
    });

    test('should load English translations correctly', () async {
      const delegate = AppLocalizations.delegate;
      final localizations = await delegate.load(const Locale('en'));
      
      expect(localizations.appTitle, equals('JaBook'));
      expect(localizations.searchAudiobooks, equals('Search Audiobooks'));
      expect(localizations.searchPlaceholder, equals('Enter title, author, or keywords'));
      expect(localizations.failedToSearch, equals('Failed to search'));
      expect(localizations.resultsFromCache, equals('Results from cache'));
      expect(localizations.noResults, equals('No results found'));
      expect(localizations.logsExportedSuccessfully, equals('Logs exported successfully'));
      expect(localizations.failedToExportLogs, equals('Failed to export logs'));
    });

    test('should load Russian translations correctly', () async {
      const delegate = AppLocalizations.delegate;
      final localizations = await delegate.load(const Locale('ru'));
      
      expect(localizations.appTitle, equals('JaBook'));
      expect(localizations.searchAudiobooks, equals('Поиск аудиокниг'));
      expect(localizations.searchPlaceholder, equals('Введите название, автора или ключевые слова'));
      expect(localizations.failedToSearch, equals('Не удалось выполнить поиск'));
      expect(localizations.resultsFromCache, equals('Результаты из кэша'));
      expect(localizations.noResults, equals('Результатов не найдено'));
      expect(localizations.logsExportedSuccessfully, equals('Логи успешно экспортированы'));
      expect(localizations.failedToExportLogs, equals('Не удалось экспортировать логи'));
    });

    test('should have correct supported locales', () {
      expect(AppLocalizations.supportedLocales.length, equals(2));
      expect(AppLocalizations.supportedLocales.contains(const Locale('en')), isTrue);
      expect(AppLocalizations.supportedLocales.contains(const Locale('ru')), isTrue);
    });

    test('should have correct localizations delegates', () {
      expect(AppLocalizations.localizationsDelegates.length, equals(4));
      expect(AppLocalizations.localizationsDelegates.contains(AppLocalizations.delegate), isTrue);
    });
  });
}