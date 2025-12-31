// Copyright 2026 Jabook Contributors
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

package com.jabook.app.jabook.compose.core.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.model.AppFont
import com.jabook.app.jabook.ui.theme.InterFontFamily

/**
 * Utility for creating FontFamily based on AppFont selection.
 *
 * Supports both bundled fonts (Inter) and downloadable Google Fonts.
 * Uses Google Fonts Provider for downloading fonts on demand.
 */
object FontUtils {
    /**
     * Google Fonts Provider configuration.
     * This is the official Google Fonts provider for Android.
     */
    private val provider =
        androidx.compose.ui.text.googlefonts.GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs,
        )

    /**
     * Creates FontFamily for the given AppFont.
     *
     * @param font The selected font
     * @param context Android context (required for Google Fonts)
     * @return FontFamily instance
     */
    @Composable
    fun getFontFamily(font: AppFont): FontFamily {
        val context = LocalContext.current
        return remember(font) {
            when (font) {
                AppFont.DEFAULT -> InterFontFamily
                AppFont.SYSTEM -> FontFamily.SansSerif
                else -> {
                    // Use Google Fonts for downloadable fonts
                    // Load multiple font weights for better typography support
                    font.googleFontName?.let { fontName ->
                        try {
                            FontFamily(
                                Font(
                                    googleFont = GoogleFont(fontName),
                                    fontProvider = provider,
                                    weight = FontWeight.Normal,
                                ),
                                Font(
                                    googleFont = GoogleFont(fontName),
                                    fontProvider = provider,
                                    weight = FontWeight.Medium,
                                ),
                                Font(
                                    googleFont = GoogleFont(fontName),
                                    fontProvider = provider,
                                    weight = FontWeight.SemiBold,
                                ),
                                Font(
                                    googleFont = GoogleFont(fontName),
                                    fontProvider = provider,
                                    weight = FontWeight.Bold,
                                ),
                            )
                        } catch (e: Exception) {
                            // Fallback to Inter if font loading fails
                            android.util.Log.w("FontUtils", "Failed to load font: $fontName", e)
                            InterFontFamily
                        }
                    } ?: InterFontFamily
                }
            }
        }
    }

    /**
     * Creates FontFamily synchronously (for non-Composable contexts).
     * Note: This may block if font needs to be downloaded.
     *
     * @param font The selected font
     * @param context Android context
     * @return FontFamily instance
     */
    fun getFontFamilySync(
        font: AppFont,
        context: Context,
    ): FontFamily =
        when (font) {
            AppFont.DEFAULT -> InterFontFamily
            AppFont.SYSTEM -> FontFamily.SansSerif
            else -> {
                font.googleFontName?.let { fontName ->
                    try {
                        FontFamily(
                            Font(
                                googleFont = GoogleFont(fontName),
                                fontProvider = provider,
                                weight = androidx.compose.ui.text.font.FontWeight.Normal,
                            ),
                            Font(
                                googleFont = GoogleFont(fontName),
                                fontProvider = provider,
                                weight = androidx.compose.ui.text.font.FontWeight.Medium,
                            ),
                            Font(
                                googleFont = GoogleFont(fontName),
                                fontProvider = provider,
                                weight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            ),
                            Font(
                                googleFont = GoogleFont(fontName),
                                fontProvider = provider,
                                weight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("FontUtils", "Failed to load font: $fontName", e)
                        InterFontFamily
                    }
                } ?: InterFontFamily
            }
        }
}
