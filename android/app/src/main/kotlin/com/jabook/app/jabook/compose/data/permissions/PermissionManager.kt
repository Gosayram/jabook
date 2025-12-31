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

package com.jabook.app.jabook.compose.data.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val context: Context = context

        /**
         * Checks if the app has the comprehensive storage permission required for operation.
         * - Android 11+ (R): Checks Environment.isExternalStorageManager()
         * - Android < 11: Checks WRITE_EXTERNAL_STORAGE
         */
        fun hasStoragePermission(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
            }

        /**
         * Checks if the app has notification permission (Android 13+).
         * On older versions, this is always true.
         */
        fun hasNotificationPermission(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        /**
         * Returns the Intent to request the "All Files Access" permission (Android 11+).
         */
        fun getManageExternalStorageIntent(): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else {
                // Fallback to app settings if called on unsupported version, though it shouldn't be
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }

        /**
         * Returns Intent to open App Settings (for manually enabling permissions).
         */
        fun getAppSettingsIntent(): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
    }
