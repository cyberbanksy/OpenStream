package com.orgista.openstream.player

import com.orgista.openstream.data.NasConnection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbDataSourceTest {
    private val connection = NasConnection(
        id = "nas-1",
        name = "Home NAS",
        host = "192.168.1.100",
        share = "media",
        username = "admin",
        password = "secretpassword",
    )

    @Test
    fun `isHostAllowed allows matching host on smb scheme`() {
        assertTrue(SmbHostPolicy.isHostAllowed("smb://192.168.1.100/media/movie.mkv", "192.168.1.100"))
        assertTrue(SmbHostPolicy.isHostAllowed("SMB://192.168.1.100/media/movie.mkv", "192.168.1.100"))
        assertTrue(SmbHostPolicy.isHostAllowed("smb://MY-NAS/media/movie.mkv", "my-nas"))
    }

    @Test
    fun `isHostAllowed rejects mismatched host to prevent SMB credential relay`() {
        assertFalse(SmbHostPolicy.isHostAllowed("smb://attacker.evil.com/share/video.mkv", "192.168.1.100"))
        assertFalse(SmbHostPolicy.isHostAllowed("smb://192.168.1.101/share/video.mkv", "192.168.1.100"))
        assertFalse(SmbHostPolicy.isHostAllowed("smb://rogue-nas.local/share/video.mkv", "safe-nas.local"))
    }

    @Test
    fun `isHostAllowed rejects non-SMB schemes`() {
        assertFalse(SmbHostPolicy.isHostAllowed("http://192.168.1.100/share/video.mkv", "192.168.1.100"))
        assertFalse(SmbHostPolicy.isHostAllowed("https://192.168.1.100/share/video.mkv", "192.168.1.100"))
        assertFalse(SmbHostPolicy.isHostAllowed("ftp://192.168.1.100/share/video.mkv", "192.168.1.100"))
        assertFalse(SmbHostPolicy.isHostAllowed("file:///share/video.mkv", "192.168.1.100"))
    }

    @Test
    fun `isHostAllowed rejects empty, null, or malformed URIs`() {
        assertFalse(SmbHostPolicy.isHostAllowed("smb:///share/video.mkv", "192.168.1.100"))
        assertFalse(SmbHostPolicy.isHostAllowed("", "192.168.1.100"))
        assertFalse(SmbHostPolicy.isHostAllowed("not-a-valid-uri", "192.168.1.100"))
    }

    @Test
    fun `factory creates SmbDataSource instance with configured connection`() {
        val factory = SmbDataSource.Factory(connection)
        val created = factory.createDataSource()
        assertTrue(created is SmbDataSource)
    }
}
