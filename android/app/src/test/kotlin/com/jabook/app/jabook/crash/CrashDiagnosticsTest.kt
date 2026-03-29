package com.jabook.app.jabook.crash

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrashDiagnosticsTest {
    private lateinit var fakeSink: FakeCrashDiagnosticsSink

    @Before
    fun setUp() {
        fakeSink = FakeCrashDiagnosticsSink()
        CrashDiagnostics.isEnabledOverride = true
        CrashDiagnostics.sinkFactory = { fakeSink }
    }

    @After
    fun tearDown() {
        CrashDiagnostics.isEnabledOverride = null
    }

    @Test
    fun `setPlaybackContext stores expected crash keys`() {
        CrashDiagnostics.setPlaybackContext(
            bookTitle = "Book 1",
            playerState = "3",
            playbackSpeed = 1.5f,
            sleepMode = "chapter_end",
        )

        assertEquals("Book 1", fakeSink.keys["current_book"])
        assertEquals("3", fakeSink.keys["player_state"])
        assertEquals("1.5", fakeSink.keys["playback_speed"])
        assertEquals("chapter_end", fakeSink.keys["sleep_mode"])
    }

    @Test
    fun `reportNonFatal records exception and attributes`() {
        val error = IllegalStateException("boom")

        CrashDiagnostics.reportNonFatal(
            tag = "mirror_health_check_failed",
            throwable = error,
            attributes =
                mapOf(
                    "mirror_domain" to "rutracker.org",
                    "attempt" to 2,
                ),
        )

        assertEquals("mirror_health_check_failed", fakeSink.keys["non_fatal_tag"])
        assertEquals("rutracker.org", fakeSink.keys["nf_mirror_domain"])
        assertEquals("2", fakeSink.keys["nf_attempt"])
        assertTrue(fakeSink.logs.any { it.contains("non_fatal:mirror_health_check_failed") })
        assertEquals(error, fakeSink.recorded.single())
    }

    @Test
    fun `configureRuntimeContext stores expected runtime keys`() {
        CrashDiagnostics.configureRuntimeContext(
            buildType = "release",
            flavor = "prod",
            versionName = "1.2.3",
            versionCode = 123L,
        )

        assertEquals("release", fakeSink.keys["build_type"])
        assertEquals("prod", fakeSink.keys["flavor"])
        assertEquals("1.2.3", fakeSink.keys["version_name"])
        assertEquals("123", fakeSink.keys["version_code"])
        assertTrue(fakeSink.logs.any { it.contains("crash_diagnostics_initialized") })
    }

    @Test
    fun `reportUncaughtException stores thread and records throwable`() {
        val error = RuntimeException("fatal")

        CrashDiagnostics.reportUncaughtException(
            threadName = "main",
            throwable = error,
            attributes = mapOf("source" to "global_exception_handler"),
        )

        assertEquals("main", fakeSink.keys["uncaught_thread_name"])
        assertEquals("global_exception_handler", fakeSink.keys["ue_source"])
        assertEquals(error, fakeSink.recorded.single())
        assertTrue(fakeSink.logs.any { it.contains("uncaught_exception") })
    }

    @Test
    fun `telemetry calls are safe no-op when diagnostics disabled`() {
        CrashDiagnostics.isEnabledOverride = false

        CrashDiagnostics.configureRuntimeContext(
            buildType = "release",
            flavor = "prod",
            versionName = "1.2.3",
            versionCode = 123L,
        )
        CrashDiagnostics.log("hello")
        CrashDiagnostics.reportNonFatal(
            tag = "disabled_path",
            throwable = IllegalStateException("skip"),
        )
        CrashDiagnostics.reportUncaughtException(
            threadName = "main",
            throwable = IllegalStateException("skip"),
        )

        assertTrue(fakeSink.keys.isEmpty())
        assertTrue(fakeSink.logs.isEmpty())
        assertTrue(fakeSink.recorded.isEmpty())
    }
}

private class FakeCrashDiagnosticsSink : CrashDiagnosticsSink {
    val keys = mutableMapOf<String, String>()
    val recorded = mutableListOf<Throwable>()
    val logs = mutableListOf<String>()

    override fun setCustomKey(
        key: String,
        value: String,
    ) {
        keys[key] = value
    }

    override fun recordException(throwable: Throwable) {
        recorded.add(throwable)
    }

    override fun log(message: String) {
        logs.add(message)
    }
}
