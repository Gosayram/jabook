// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.l10n

import cafe.adriel.lyricist.LyricistStrings

/**
 * English localization (default).
 */
@LyricistStrings(languageTag = Locales.EN, default = true)
val EnStrings =
    Strings(
        // Navigation
        navLibrary = "Library",
        navSettings = "Settings",
        // Screen titles
        screenLibrary = "Library",
        screenPlayer = "Now Playing",
        screenSettings = "Settings",
        screenWebView = "WebView",
        // Library Screen
        libraryEmptyTitle = "No audiobooks",
        libraryEmptyMessage = "Add audiobooks to get started",
        librarySearchHint = "Search books",
        // Player Screen
        playerChapter = { number -> "Chapter $number" },
        playerLoading = "Loading...",
        playerUnknown = "Unknown",
        playerPlay = "Play",
        playerPause = "Pause",
        playerSkipPrevious = "Previous chapter",
        playerSkipNext = "Next chapter",
        // Settings Screen
        settingsSectionAppearance = "Appearance",
        settingsSectionPlayback = "Playback",
        settingsSectionAbout = "About",
        settingsTheme = "Theme",
        settingsThemeDescription = "Choose app theme",
        settingsThemeLight = "Light",
        settingsThemeDark = "Dark",
        settingsThemeSystem = "System default",
        settingsAutoPlayNext = "Auto-play next chapter",
        settingsAutoPlayNextDescription = "Automatically play next chapter when current ends",
        settingsPlaybackSpeed = "Playback speed",
        settingsPlaybackSpeedValue = { speed -> "%.1fx".format(speed) },
        settingsVersion = "Version",
        // WebView Screen
        webViewLoading = "Loading...",
        webViewBack = "Back",
        // Common
        commonLoading = "Loading",
        commonError = "Error",
        commonRetry = "Retry",
        commonBack = "Back",
        commonClose = "Close",
    )

/**
 * Russian localization.
 */
@LyricistStrings(languageTag = Locales.RU)
val RuStrings =
    Strings(
        // Навигация
        navLibrary = "Библиотека",
        navSettings = "Настройки",
        // Заголовки экранов
        screenLibrary = "Библиотека",
        screenPlayer = "Сейчас играет",
        screenSettings = "Настройки",
        screenWebView = "Браузер",
        // Экран библиотеки
        libraryEmptyTitle = "Нет аудиокниг",
        libraryEmptyMessage = "Добавьте аудиокниги чтобы начать",
        librarySearchHint = "Поиск книг",
        // Экран плеера
        playerChapter = { number -> "Глава $number" },
        playerLoading = "Загрузка...",
        playerUnknown = "Неизвестно",
        playerPlay = "Играть",
        playerPause = "Пауза",
        playerSkipPrevious = "Предыдущая глава",
        playerSkipNext = "Следующая глава",
        // Экран настроек
        settingsSectionAppearance = "Внешний вид",
        settingsSectionPlayback = "Воспроизведение",
        settingsSectionAbout = "О приложении",
        settingsTheme = "Тема",
        settingsThemeDescription = "Выберите тему приложения",
        settingsThemeLight = "Светлая",
        settingsThemeDark = "Тёмная",
        settingsThemeSystem = "Системная",
        settingsAutoPlayNext = "Авто-проигрывание следующей главы",
        settingsAutoPlayNextDescription = "Автоматически проигрывать следующую главу когда текущая закончится",
        settingsPlaybackSpeed = "Скорость воспроизведения",
        settingsPlaybackSpeedValue = { speed -> "%.1fx".format(speed) },
        settingsVersion = "Версия",
        // Экран WebView
        webViewLoading = "Загрузка...",
        webViewBack = "Назад",
        // Общие
        commonLoading = "Загрузка",
        commonError = "Ошибка",
        commonRetry = "Повторить",
        commonBack = "Назад",
        commonClose = "Закрыть",
    )

/**
 * Localization strings data class.
 *
 * This defines all localizable strings in the app.
 * Each property corresponds to a string resource.
 *
 * NOTE: This file can be generated from JSON/online services:
 * 1. Export translations from Localazy/Localizely/POEditor as JSON
 * 2. Use script to convert JSON to this Kotlin format
 * 3. Commit to version control
 */
data class Strings(
    // Navigation
    val navLibrary: String,
    val navSettings: String,
    // Screen titles
    val screenLibrary: String,
    val screenPlayer: String,
    val screenSettings: String,
    val screenWebView: String,
    // Library Screen
    val libraryEmptyTitle: String,
    val libraryEmptyMessage: String,
    val librarySearchHint: String,
    // Player Screen
    val playerChapter: (Int) -> String,
    val playerLoading: String,
    val playerUnknown: String,
    val playerPlay: String,
    val playerPause: String,
    val playerSkipPrevious: String,
    val playerSkipNext: String,
    // Settings Screen
    val settingsSectionAppearance: String,
    val settingsSectionPlayback: String,
    val settingsSectionAbout: String,
    val settingsTheme: String,
    val settingsThemeDescription: String,
    val settingsThemeLight: String,
    val settingsThemeDark: String,
    val settingsThemeSystem: String,
    val settingsAutoPlayNext: String,
    val settingsAutoPlayNextDescription: String,
    val settingsPlaybackSpeed: String,
    val settingsPlaybackSpeedValue: (Float) -> String,
    val settingsVersion: String,
    // WebView Screen
    val webViewLoading: String,
    val webViewBack: String,
    // Common
    val commonLoading: String,
    val commonError: String,
    val commonRetry: String,
    val commonBack: String,
    val commonClose: String,
)

/**
 * Supported locale tags.
 *
 * To add a new language:
 * 1. Add locale tag constant here
 * 2. Create new @LyricistStrings annotated val with translations
 * 3. Or import from JSON export from translation service
 */
object Locales {
    const val EN = "en"
    const val RU = "ru"
    // Add more languages here:
    // const val ES = "es"
    // const val DE = "de"
}

/**
 * CompositionLocal for accessing localized strings.
 *
 * Usage in Composables:
 * ```
 * val strings = LocalStrings.current
 * Text(strings.navLibrary)
 * ```
 */
val LocalStrings = cafe.adriel.lyricist.LocalStrings
