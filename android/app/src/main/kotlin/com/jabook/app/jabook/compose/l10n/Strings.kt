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
import androidx.compose.ui.res.stringResource
import com.jabook.app.jabook.R

/**
 * English localization (default).
 */
@LyricistStrings(languageTag = Locales.EN, default = true)
val EnStrings =
    Strings(
        // Navigation
        navLibrary = stringResource(R.string.library),
        navSettings = stringResource(R.string.navSettingsText),
        // Screen titles
        screenLibrary = stringResource(R.string.library),
        screenPlayer = stringResource(R.string.nowPlaying),
        screenSettings = stringResource(R.string.navSettingsText),
        screenWebView = stringResource(R.string.webview),
        // Library Screen
        libraryEmptyTitle = stringResource(R.string.noAudiobooks),
        libraryEmptyMessage = stringResource(R.string.addAudiobooksToGetStarted),
        librarySearchHint = stringResource(R.string.searchBooks1),
        // Player Screen
        playerChapter = { number -> stringResource(R.string.chapterNumber1) },
        playerLoading = stringResource(R.string.loading),
        playerUnknown = stringResource(R.string.unknown),
        playerPlay = stringResource(R.string.playButton),
        playerPause = stringResource(R.string.pauseButton),
        playerSkipPrevious = stringResource(R.string.previousChapter),
        playerSkipNext = stringResource(R.string.nextChapter),
        // Settings Screen
        settingsSectionAppearance = stringResource(R.string.appearance),
        settingsSectionPlayback = stringResource(R.string.playback),
        settingsSectionAbout = stringResource(R.string.aboutTitle),
        settingsTheme = stringResource(R.string.themeTitle),
        settingsThemeDescription = stringResource(R.string.chooseAppTheme),
        settingsThemeLight = stringResource(R.string.light),
        settingsThemeDark = stringResource(R.string.dark),
        settingsThemeSystem = stringResource(R.string.systemDefault1),
        settingsAutoPlayNext = stringResource(R.string.autoplayNextChapter),
        settingsAutoPlayNextDescription = stringResource(R.string.automaticallyPlayNextChapterWhenCurrentEnds),
        settingsPlaybackSpeed = stringResource(R.string.playbackSpeed),
        settingsPlaybackSpeedValue = { speed -> "%.1fx".format(speed) },
        settingsVersion = stringResource(R.string.version),
        // WebView Screen
        webViewLoading = stringResource(R.string.loading),
        webViewBack = stringResource(R.string.back),
        // Common
        commonLoading = stringResource(R.string.loading1),
        commonError = stringResource(R.string.error),
        commonRetry = stringResource(R.string.retryButton),
        commonBack = stringResource(R.string.back),
        commonClose = stringResource(R.string.close),
    )

/**
 * Russian localization.
 */
@LyricistStrings(languageTag = Locales.RU)
val RuStrings =
    Strings(
        // Навигация
        navLibrary = stringResource(R.string.библиотека),
        navSettings = stringResource(R.string.настройки),
        // Заголовки экранов
        screenLibrary = stringResource(R.string.библиотека),
        screenPlayer = stringResource(R.string.сейчасИграет),
        screenSettings = stringResource(R.string.настройки),
        screenWebView = stringResource(R.string.браузер),
        // Экран библиотеки
        libraryEmptyTitle = stringResource(R.string.нетАудиокниг),
        libraryEmptyMessage = stringResource(R.string.добавьтеАудиокнигиЧтобыНачать),
        librarySearchHint = stringResource(R.string.поискКниг),
        // Экран плеера
        playerChapter = { number -> stringResource(R.string.главаNumber) },
        playerLoading = stringResource(R.string.загрузка),
        playerUnknown = stringResource(R.string.неизвестно),
        playerPlay = stringResource(R.string.играть),
        playerPause = stringResource(R.string.пауза),
        playerSkipPrevious = stringResource(R.string.предыдущаяГлава),
        playerSkipNext = stringResource(R.string.следующаяГлава),
        // Экран настроек
        settingsSectionAppearance = stringResource(R.string.внешнийВид),
        settingsSectionPlayback = stringResource(R.string.воспроизведение),
        settingsSectionAbout = stringResource(R.string.оПриложении),
        settingsTheme = stringResource(R.string.тема),
        settingsThemeDescription = stringResource(R.string.выберитеТемуПриложения),
        settingsThemeLight = stringResource(R.string.светлая),
        settingsThemeDark = stringResource(R.string.тёмная),
        settingsThemeSystem = stringResource(R.string.системная),
        settingsAutoPlayNext = stringResource(R.string.автопроигрываниеСледующейГлавы),
        settingsAutoPlayNextDescription = stringResource(R.string.автоматическиПроигрыватьСледующуюГлавуКогдаТекущая),
        settingsPlaybackSpeed = stringResource(R.string.скоростьВоспроизведения),
        settingsPlaybackSpeedValue = { speed -> "%.1fx".format(speed) },
        settingsVersion = stringResource(R.string.версия),
        // Экран WebView
        webViewLoading = stringResource(R.string.загрузка),
        webViewBack = stringResource(R.string.назад),
        // Общие
        commonLoading = stringResource(R.string.загрузка1),
        commonError = stringResource(R.string.ошибка),
        commonRetry = stringResource(R.string.повторить),
        commonBack = stringResource(R.string.назад),
        commonClose = stringResource(R.string.закрыть),
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
    const val EN = stringResource(R.string.en)
    const val RU = stringResource(R.string.ru)
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
