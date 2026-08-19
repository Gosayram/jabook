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
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

public enum class StorageAccessMode {
    FULL_FILE_SYSTEM,
    LEGACY_RUNTIME_PERMISSIONS,
}

public data class StorageAccessRequest(
    val mode: StorageAccessMode,
    val runtimePermissions: List<String> = emptyList(),
    val intent: Intent? = null,
)

@Singleton
public class PermissionManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val context: Context = context
        private val externalStoragePreflightChecker =
            ExternalStoragePreflightChecker(
                hasFullStoragePermission = { hasStoragePermission() },
            )
        private val storageTransferWorkflow =
            StorageTransferWorkflow(
                preflightChecker = externalStoragePreflightChecker,
            )

        /**
         * Checks if the app has the comprehensive storage permission required for operation.
         * Checks Environment.isExternalStorageManager() (Android 11+).
         */
        public fun hasStoragePermission(): Boolean =
            Environment.isExternalStorageManager()

        /**
         * Checks if the app has notification permission (Android 13+).
         * On older versions, this is always true.
         */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        public fun hasNotificationPermission(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        /**
         * Returns storage access request parameters.
         * Uses all-files access settings screen (Android 11+).
         */
        public fun getStorageAccessRequest(): StorageAccessRequest =
            StorageAccessRequest(
                mode = StorageAccessMode.FULL_FILE_SYSTEM,
                intent = getManageExternalStorageIntent(),
            )

        /**
         * Returns the Intent to request the "All Files Access" permission (Android 11+).
         */
        @RequiresApi(Build.VERSION_CODES.R)
        public fun getManageExternalStorageIntent(): Intent =
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
        public fun getAppSettingsIntent(): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }

        public fun preflightExternalDirectory(path: String?): StoragePathPreflightResult =
            externalStoragePreflightChecker.checkDirectory(path)

        public fun preflightTransferIntegrity(
            sourcePath: String,
            targetPath: String,
        ): StorageTransferPreflightResult =
            externalStoragePreflightChecker.verifyTransferIntegrity(
                sourcePath = sourcePath,
                targetPath = targetPath,
            )

        public fun transferFileWithRollback(
            sourcePath: String,
            targetPath: String,
            overwrite: Boolean = true,
        ): StorageTransferWorkflowResult =
            storageTransferWorkflow.transferFile(
                sourcePath = sourcePath,
                targetPath = targetPath,
                overwrite = overwrite,
            )
    }
