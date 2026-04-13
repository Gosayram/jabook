// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.jabook.app.jabook.compose.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

class AtomicFileWriterTest {
    @Test
    fun `writeAtomically writes data and removes temp file`() {
        val dir = Files.createTempDirectory("jabook-atomic-test").toFile()
        val target = File(dir, "state.json")

        val payload = "{\"position\":12345}".toByteArray()
        val result =
            AtomicFileWriter.writeAtomically(target) { stream ->
                stream.write(payload)
                payload.size.toLong()
            }

        assertTrue(target.exists())
        assertEquals(payload.size.toLong(), result.bytesWritten)
        assertEquals(String(payload), target.readText())
        assertFalse(File(dir, "state.json.tmp").exists())

        target.delete()
        dir.delete()
    }

    @Test(expected = java.io.IOException::class)
    fun `writeAtomically throws when overwrite is false and target exists`() {
        val dir = Files.createTempDirectory("jabook-atomic-overwrite-test").toFile()
        val target = File(dir, "state.json")
        target.writeText("initial")

        try {
            AtomicFileWriter.writeAtomically(target, overwrite = false) { stream ->
                stream.write("new".toByteArray())
                3L
            }
        } finally {
            target.delete()
            dir.delete()
        }
    }

    @Test
    fun `writeWithLock writes data`() {
        val dir = Files.createTempDirectory("jabook-atomic-lock-test").toFile()
        val target = File(dir, "backup.zip")

        val payload = "zip-binary-content".toByteArray()
        AtomicFileWriter.writeWithLock(target) { stream ->
            stream.write(payload)
            payload.size.toLong()
        }

        assertTrue(target.exists())
        assertEquals(String(payload), target.readText())
        File(dir, "backup.zip.lock").toPath().deleteIfExists()

        target.delete()
        dir.delete()
    }
}
