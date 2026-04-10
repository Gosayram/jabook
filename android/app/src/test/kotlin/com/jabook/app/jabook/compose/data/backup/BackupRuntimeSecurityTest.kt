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
