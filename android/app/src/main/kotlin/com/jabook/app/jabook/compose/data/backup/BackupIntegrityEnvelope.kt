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

import kotlinx.serialization.Serializable

@Serializable
public data class BackupIntegrityEnvelope(
    val envelopeVersion: String = "1.0.0",
    val payload: BackupData,
    val payloadJson: String,
    val integrity: BackupIntegrityMetadata? = null,
)

@Serializable
public data class BackupIntegrityMetadata(
    val algorithm: String,
    val keyAlias: String,
    val keyId: String,
    val signatureBase64: String,
)

public enum class BackupIntegrityVerificationResult {
    VERIFIED,
    KEY_UNAVAILABLE,
    SIGNATURE_INVALID,
    UNSUPPORTED_ALGORITHM,
}