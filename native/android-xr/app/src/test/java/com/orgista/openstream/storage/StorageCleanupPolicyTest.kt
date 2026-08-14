package com.orgista.openstream.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageCleanupPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `metadata size includes only the approved catalog files`() {
        val filesDir = temporaryFolder.newFolder("files")
        File(filesDir, "library-cache.json").writeBytes(ByteArray(7))
        File(filesDir, "stremio-catalog-v1.json").writeBytes(ByteArray(11))
        File(filesDir, "unrelated-user-state.json").writeBytes(ByteArray(19))

        assertEquals(18L, StorageCleanupPolicy.metadataSize(filesDir))
    }

    @Test
    fun `clearing metadata preserves credentials history and unrelated files`() {
        val filesDir = temporaryFolder.newFolder("metadata")
        val library = File(filesDir, "library-cache.json").apply { writeText("library") }
        val stremio = File(filesDir, "stremio-catalog-v1.json").apply { writeText("catalog") }
        val credentials = File(filesDir, "credentials.json").apply { writeText("preserve") }

        StorageCleanupPolicy.clearMetadata(filesDir)

        assertFalse(library.exists())
        assertFalse(stremio.exists())
        assertTrue(credentials.exists())
    }

    @Test
    fun `clearing temporary files removes cache children but keeps cache root`() {
        val cacheDir = temporaryFolder.newFolder("cache")
        File(cacheDir, "image.tmp").writeBytes(ByteArray(5))
        File(cacheDir, "nested").apply { mkdirs() }
            .resolve("catalog.tmp")
            .writeBytes(ByteArray(9))

        assertEquals(14L, StorageCleanupPolicy.directorySize(cacheDir))

        StorageCleanupPolicy.clearChildren(cacheDir)

        assertTrue(cacheDir.exists())
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
        assertEquals(0L, StorageCleanupPolicy.directorySize(cacheDir))
    }

    @Test
    fun `storage labels retain useful precision below one megabyte`() {
        assertEquals("0 B", StorageCleanupPolicy.formatBytes(0L))
        assertEquals("457 KB", StorageCleanupPolicy.formatBytes(467_813L))
        assertEquals("1.5 MB", StorageCleanupPolicy.formatBytes(1_572_864L))
        assertEquals("2.0 GB", StorageCleanupPolicy.formatBytes(2_147_483_648L))
    }
}
