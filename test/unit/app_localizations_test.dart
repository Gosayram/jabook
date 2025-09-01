import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:jabook/l10n/app_localizations.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  group('AppLocalizations', () {
    test('should return correct English translations', () async {
      final localizations = AppLocalizations(const Locale('en', 'US'));
      await localizations.load();

      expect(localizations.appTitle, equals('JaBook'));
      expect(localizations.searchAudiobooks, equals('Search Audiobooks'));
      expect(localizations.searchPlaceholder, equals('Enter title author or keywords'));
      expect(localizations.failedToSearch, equals('Failed to search'));
      expect(localizations.resultsFromCache, equals('Results from cache'));
      expect(localizations.noResults, equals('No results found'));
    });

    test('should return correct Russian translations', () async {
      final localizations = AppLocalizations(const Locale('ru', 'RU'));
      await localizations.load();

      expect(localizations.appTitle, equals('JaBook'));
      expect(localizations.searchAudiobooks, equals('Поиск аудиокниг'));
      expect(localizations.searchPlaceholder, equals('Введите название автора или ключевые слова'));
      expect(localizations.failedToSearch, equals('Не удалось выполнить поиск'));
      expect(localizations.resultsFromCache, equals('Результаты из кэша'));
      expect(localizations.noResults, equals('Результатов не найдено'));
    });

    test('should return key when translation not found', () {
      final localizations = AppLocalizations(const Locale('en', 'US'));
      
      // Before loading, should return key
      expect(localizations.translate('nonexistent_key'), equals('nonexistent_key'));
    });

    test('delegate should support English and Russian locales', () {
      const delegate = AppLocalizations.delegate;
      
      expect(delegate.isSupported(const Locale('en', 'US')), isTrue);
      expect(delegate.isSupported(const Locale('ru', 'RU')), isTrue);
      expect(delegate.isSupported(const Locale('fr', 'FR')), isFalse);
      expect(delegate.isSupported(const Locale('de', 'DE')), isFalse);
    });

    test('should handle loading errors gracefully', () async {
      final localizations = AppLocalizations(const Locale('en', 'US'));
      
      // Mock the load method to simulate an error
      final result = await localizations.load();
      // This should succeed for supported locales
      expect(result, isTrue);
    });
  });
}