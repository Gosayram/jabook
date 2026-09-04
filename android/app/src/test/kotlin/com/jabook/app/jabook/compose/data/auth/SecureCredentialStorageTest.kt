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

package com.jabook.app.jabook.compose.data.auth

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.crash.CrashDiagnosticsSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
class SecureCredentialStorageTest {
    private lateinit var context: Context
    private lateinit var crashSink: TestCrashSink
    private lateinit var previousCrashSinkFactory: () -> CrashDiagnosticsSink

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        crashSink = TestCrashSink()
        previousCrashSinkFactory = CrashDiagnostics.sinkFactory
        CrashDiagnostics.isEnabledOverride = true
        CrashDiagnostics.sinkFactory = { crashSink }
        SecureCredentialStorageFactories.aeadFactoryOverride = null
        SecureCredentialStorageFactories.dataStoreFactoryOverride = null
    }

    @After
    fun tearDown() {
        CrashDiagnostics.isEnabledOverride = null
        CrashDiagnostics.sinkFactory = previousCrashSinkFactory
        SecureCredentialStorageFactories.aeadFactoryOverride = null
        SecureCredentialStorageFactories.dataStoreFactoryOverride = null
    }

    @Test
    fun `getCredentials returns null and clears store when encrypted payload is invalid`() =
        runTest {
            val storage = SecureCredentialStorage(context)
            storage.clearCredentials()
            storage.saveCredentials(UserCredentials(username = "alice", password = "secret"))
            storage.replaceEncryptedCredentialsForTesting(
                encryptedUsername = "invalid-base64",
                encryptedPassword = "still-invalid",
            )

            val credentials = storage.getCredentials()

            assertNull(credentials)
            assertFalse(storage.hasEncryptedCredentialsForTesting())
            assertTrue(
                crashSink.logs.any { it.contains("secure_credentials_decrypt_failed") },
            )
        }

    @Test
    fun `invalid stale credentials do not clear newer credentials saved before cleanup`() =
        runTest {
            val staleCredentials =
                mutablePreferencesOf(
                    stringPreferencesKey("encrypted_username") to "stale-username",
                    stringPreferencesKey("encrypted_password") to "stale-password",
                )
            val newerCredentials =
                mutablePreferencesOf(
                    stringPreferencesKey("encrypted_username") to "newer-username",
                    stringPreferencesKey("encrypted_password") to "newer-password",
                )
            val dataStore = StaleReadDataStore(staleCredentials, newerCredentials)
            SecureCredentialStorageFactories.dataStoreFactoryOverride = { dataStore }
            SecureCredentialStorageFactories.aeadFactoryOverride = { FailingDecryptAead }

            val credentials = SecureCredentialStorage(context).getCredentials()

            assertNull(credentials)
            assertTrue(
                dataStore.current[stringPreferencesKey("encrypted_username")] == "newer-username",
            )
            assertTrue(
                dataStore.current[stringPreferencesKey("encrypted_password")] == "newer-password",
            )
        }

    @Test
    fun `saveCredentials does not crash when keyset init fails`() =
        runTest {
            SecureCredentialStorageFactories.aeadFactoryOverride = {
                throw IllegalStateException("Keyset init failed")
            }
            val storage = SecureCredentialStorage(context)

            storage.saveCredentials(UserCredentials(username = "bob", password = "pw"))

            assertNull(storage.getCredentials())
            assertTrue(
                crashSink.logs.any { it.contains("secure_credentials_keyset_init_failed") },
            )
        }

    @Test
    fun `getCredentials recovers safely when datastore is corrupted`() =
        runTest {
            SecureCredentialStorageFactories.dataStoreFactoryOverride = {
                CorruptedPreferencesDataStore
            }
            val storage = SecureCredentialStorage(context)

            val credentials = storage.getCredentials()

            assertNull(credentials)
            assertTrue(
                crashSink.logs.any {
                    it.contains("secure_credentials_datastore_read_corruption") ||
                        it.contains("secure_credentials_datastore_corruption")
                },
            )
        }

    @Test
    fun `corruption handler resets preferences to empty`() =
        runTest {
            var reported = false
            val handler =
                secureCredentialsCorruptionHandler().also {
                    CrashDiagnostics.sinkFactory = {
                        object : CrashDiagnosticsSink {
                            override fun setCustomKey(
                                key: String,
                                value: String,
                            ) = Unit

                            override fun recordException(throwable: Throwable) = Unit

                            override fun log(message: String) {
                                if (message.contains("secure_credentials_datastore_corruption")) {
                                    reported = true
                                }
                            }
                        }
                    }
                }

            val recovered = handler.handleCorruption(CorruptionException("broken store"))

            assertTrue(recovered == emptyPreferences())
            assertTrue(reported)
        }

    private class TestCrashSink : CrashDiagnosticsSink {
        val logs: MutableList<String> = mutableListOf()

        override fun setCustomKey(
            key: String,
            value: String,
        ) = Unit

        override fun recordException(throwable: Throwable) = Unit

        override fun log(message: String) {
            logs.add(message)
        }
    }

    private object CorruptedPreferencesDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> =
            flow {
                throw CorruptionException("Corrupted DataStore payload")
            }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw CorruptionException("Corrupted DataStore payload")
    }

    private class StaleReadDataStore(
        staleValue: Preferences,
        initialCurrentValue: Preferences,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(staleValue)
        var current: Preferences = initialCurrentValue

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(current).also { current = it }
    }

    private object FailingDecryptAead : Aead {
        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray?,
        ): ByteArray = error("Encryption is not used by this test")

        override fun decrypt(
            ciphertext: ByteArray,
            associatedData: ByteArray?,
        ): ByteArray = error("Invalid stale payload")
    }
}
