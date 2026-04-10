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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.Certificate
import javax.inject.Inject
import javax.inject.Singleton

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

@Singleton
public class BackupRuntimeSecurity
    @Inject
    constructor(
        loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("BackupRuntimeSecurity")

        public companion object {
            internal const val KEY_ALIAS: String = "jabook_backup_runtime_signing_key"
            internal const val SIGNATURE_ALGORITHM: String = "SHA256withECDSA"
        }

        public fun createIntegrityMetadata(payloadJson: String): BackupIntegrityMetadata? =
            runCatching {
                val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
                val keyStore = keyStore()
                ensureSigningKey(keyStore)
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
                val signature =
                    Signature
                        .getInstance(SIGNATURE_ALGORITHM)
                        .apply {
                            initSign(entry.privateKey)
                            update(payloadBytes)
                        }.sign()

                BackupIntegrityMetadata(
                    algorithm = SIGNATURE_ALGORITHM,
                    keyAlias = KEY_ALIAS,
                    keyId = buildKeyId(entry.certificate),
                    signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP),
                )
            }.onFailure {
                logger.w { "Failed to create backup integrity metadata: ${it.message}" }
            }.getOrNull()

        public fun verifyIntegrity(
            payloadJson: String,
            metadata: BackupIntegrityMetadata,
        ): BackupIntegrityVerificationResult {
            if (metadata.algorithm != SIGNATURE_ALGORITHM) {
                return BackupIntegrityVerificationResult.UNSUPPORTED_ALGORITHM
            }

            return runCatching {
                val keyStore = keyStore()
                val certificate = keyStore.getCertificate(KEY_ALIAS) ?: return BackupIntegrityVerificationResult.KEY_UNAVAILABLE
                if (buildKeyId(certificate) != metadata.keyId) {
                    return BackupIntegrityVerificationResult.KEY_UNAVAILABLE
                }

                val signatureBytes =
                    Base64.decode(
                        metadata.signatureBase64,
                        Base64.NO_WRAP,
                    )

                val verified =
                    Signature
                        .getInstance(SIGNATURE_ALGORITHM)
                        .apply {
                            initVerify(certificate.publicKey)
                            update(payloadJson.toByteArray(StandardCharsets.UTF_8))
                        }.verify(signatureBytes)

                if (verified) {
                    BackupIntegrityVerificationResult.VERIFIED
                } else {
                    BackupIntegrityVerificationResult.SIGNATURE_INVALID
                }
            }.onFailure {
                logger.w { "Failed to verify backup integrity metadata: ${it.message}" }
            }.getOrDefault(BackupIntegrityVerificationResult.SIGNATURE_INVALID)
        }

        @Synchronized
        private fun ensureSigningKey(keyStore: KeyStore) {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return
            }

            KeyPairGenerator
                .getInstance(
                    KeyProperties.KEY_ALGORITHM_EC,
                    ANDROID_KEYSTORE,
                ).apply {
                    initialize(
                        KeyGenParameterSpec
                            .Builder(
                                KEY_ALIAS,
                                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                            ).setDigests(KeyProperties.DIGEST_SHA256)
                            .setUserAuthenticationRequired(false)
                            .build(),
                    )
                }.generateKeyPair()
        }

        private fun keyStore(): KeyStore =
            KeyStore
                .getInstance(ANDROID_KEYSTORE)
                .apply {
                    load(null)
                }

        private fun buildKeyId(certificate: Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
            return Base64.encodeToString(digest, Base64.NO_WRAP)
        }
    }