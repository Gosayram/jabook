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

package com.jabook.app.jabook.compose.data.model

/**
 * Font preference for the app.
 *
 * Supports both bundled fonts and downloadable Google Fonts.
 */
enum class AppFont(
    val displayName: String,
    val googleFontName: String? = null,
) {
    /**
     * Use default app fonts from res/font/ directory (Inter).
     */
    DEFAULT("Inter", null),

    /**
     * Use system fonts as configured in device settings.
     * This respects user's device-wide font preferences.
     */
    SYSTEM("System", null),

    /**
     * Roboto - Google Fonts (Material Design default).
     */
    ROBOTO("Roboto", "Roboto"),

    /**
     * Open Sans - Google Fonts (clean and readable).
     */
    OPEN_SANS("Open Sans", "Open Sans"),

    /**
     * Lato - Google Fonts (modern and friendly).
     */
    LATO("Lato", "Lato"),

    /**
     * Montserrat - Google Fonts (geometric and elegant).
     */
    MONTSERRAT("Montserrat", "Montserrat"),

    /**
     * Source Sans Pro - Google Fonts (professional and clear).
     */
    SOURCE_SANS_PRO("Source Sans Pro", "Source Sans Pro"),

    /**
     * Raleway - Google Fonts (elegant and stylish).
     */
    RALEWAY("Raleway", "Raleway"),

    /**
     * Poppins - Google Fonts (geometric and friendly).
     */
    POPPINS("Poppins", "Poppins"),

    /**
     * Nunito - Google Fonts (rounded and friendly).
     */
    NUNITO("Nunito", "Nunito"),

    /**
     * Playfair Display - Google Fonts (elegant serif for headings).
     */
    PLAYFAIR_DISPLAY("Playfair Display", "Playfair Display"),
}
