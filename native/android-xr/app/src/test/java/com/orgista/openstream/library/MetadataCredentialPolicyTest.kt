package com.orgista.openstream.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCredentialPolicyTest {
    @Test
    fun `credential is unavailable until the user opts in`() {
        assertEquals("", MetadataCredentialPolicy.reusableCredential(false, "secret"))
        assertEquals("secret", MetadataCredentialPolicy.reusableCredential(true, "  secret  "))
    }

    @Test
    fun `tmdb accepts v3 keys and v4 read tokens`() {
        assertTrue(MetadataCredentialPolicy.looksLikeTmdbCredential("a".repeat(32)))
        assertTrue(MetadataCredentialPolicy.looksLikeTmdbCredential("eyJheader.payload.signature"))
        assertFalse(MetadataCredentialPolicy.looksLikeTmdbCredential("short"))
    }
}
