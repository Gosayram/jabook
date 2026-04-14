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

import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupRuntimeSecurityTest {
    @Test
    fun `createIntegrityMetadata returns null when Android keystore is unavailable in unit test`() {
        val security = BackupRuntimeSecurity(NoOpLoggerFactory)

        val result = security.createIntegrityMetadata("""{"hello":"world"}""")

        assertNull(result)
    }

    @Test
    fun `verifyIntegrity returns unsupported algorithm without touching keystore`() {
        val security = BackupRuntimeSecurity(NoOpLoggerFactory)
        val metadata =
            BackupIntegrityMetadata(
                algorithm = "MD5withRSA",
                keyAlias = "alias",
                keyId = "key-id",
                signatureBase64 = "ZmFrZQ==",
            )

        val result = security.verifyIntegrity("""{"a":1}""", metadata)

        assertEquals(BackupIntegrityVerificationResult.UNSUPPORTED_ALGORITHM, result)
    }
}
