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

package com.jabook.app.jabook.compose.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.jabook.app.jabook.audio.PlayerPersistenceManager
import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.lang.reflect.InvocationTargetException

@RunWith(RobolectricTestRunner::class)
class BackupServiceTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun `importFromFile throws SecurityException when signature is invalid`() {
        val context: Context = mock()
        val contentResolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(contentResolver)

        val payload = testBackupData()
        val envelope =
            BackupIntegrityEnvelope(
                payload = payload,
                integrity =
                    BackupIntegrityMetadata(
                        algorithm = BackupRuntimeSecurity.SIGNATURE_ALGORITHM,
                        keyAlias = BackupRuntimeSecurity.KEY_ALIAS,
                        keyId = "key-id",
                        signatureBase64 = "invalid",
                    ),
            )
        whenever(contentResolver.openInputStream(Uri.parse("content://jabook/backup")))
            .thenReturn(ByteArrayInputStream(json.encodeToString(envelope).toByteArray()))

        val backupRuntimeSecurity: BackupRuntimeSecurity = mock()
        whenever(
            backupRuntimeSecurity.verifyIntegrity(
                payloadJson = org.mockito.kotlin.any(),
                metadata = org.mockito.kotlin.any(),
            ),
        ).thenReturn(BackupIntegrityVerificationResult.SIGNATURE_INVALID)

        val service = createService(context, backupRuntimeSecurity)

        assertThrows(SecurityException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.importFromFile(Uri.parse("content://jabook/backup"))
            }
        }
    }

    @Test
    fun `decodeBackupData returns payload when integrity is verified`() {
        val context: Context = mock()
        val backupRuntimeSecurity: BackupRuntimeSecurity = mock()
        whenever(
            backupRuntimeSecurity.verifyIntegrity(
                payloadJson = org.mockito.kotlin.any(),
                metadata = org.mockito.kotlin.any(),
            ),
        ).thenReturn(BackupIntegrityVerificationResult.VERIFIED)

        val service = createService(context, backupRuntimeSecurity)
        val payload = testBackupData()
        val envelope =
            BackupIntegrityEnvelope(
                payload = payload,
                integrity =
                    BackupIntegrityMetadata(
                        algorithm = BackupRuntimeSecurity.SIGNATURE_ALGORITHM,
                        keyAlias = BackupRuntimeSecurity.KEY_ALIAS,
                        keyId = "key-id",
                        signatureBase64 = "c2ln",
                    ),
            )

        val decoded = invokeDecodeBackupData(service, json.encodeToString(envelope))

        assertEquals(payload, decoded)
    }

    private fun createService(
        context: Context,
        backupRuntimeSecurity: BackupRuntimeSecurity,
    ): BackupService =
        BackupService(
            context = context,
            database = mock<JabookDatabase>(),
            userPreferencesRepository = mock<UserPreferencesRepository>(),
            protoSettingsRepository = mock<ProtoSettingsRepository>(),
            playerPersistenceManager = mock<PlayerPersistenceManager>(),
            mirrorManager = mock<MirrorManager>(),
            backupRuntimeSecurity = backupRuntimeSecurity,
            loggerFactory = NoOpLoggerFactory,
        )

    private fun invokeDecodeBackupData(
        service: BackupService,
        rawJson: String,
    ): BackupData {
        val method = BackupService::class.java.getDeclaredMethod("decodeBackupData", String::class.java)
        method.isAccessible = true
        return try {
            method.invoke(service, rawJson) as BackupData
        } catch (invocation: InvocationTargetException) {
            val cause = invocation.targetException
            if (cause is RuntimeException) throw cause
            throw invocation
        }
    }

    private fun testBackupData(): BackupData =
        BackupData(
            version = "1.0.0",
            schemaVersion = "2.0.0",
            timestamp = "2026-04-11T10:00:00Z",
            settings =
                AppSettings(
                    theme = "SYSTEM",
                    autoPlayNext = true,
                    playbackSpeed = 1.0f,
                    wifiOnlyDownload = false,
                    downloadPath = "/storage/emulated/0/Books",
                    currentMirror = "rutracker.org",
                    autoSwitchMirror = true,
                ),
            bookMetadata = emptyList(),
            favorites = emptyList(),
            searchHistory = emptyList(),
            scanPaths = emptyList(),
        )
}
