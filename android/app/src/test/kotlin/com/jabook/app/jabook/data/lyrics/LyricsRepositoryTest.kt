// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.jabook.app.jabook.data.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LyricsRepositoryTest {
    @Test
    fun `getLyrics returns empty when no sidecar lrc file exists`() =
        runBlocking {
            val mediaFile = File(Files.createTempDirectory("jabook-lyrics-test").toFile(), "chapter.mp3")

            try {
                assertTrue(LyricsRepository().getLyrics(mediaFile.path).isEmpty())
            } finally {
                mediaFile.parentFile?.delete()
            }
        }
}
