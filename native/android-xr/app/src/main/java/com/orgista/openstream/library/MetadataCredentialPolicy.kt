package com.orgista.openstream.library

/** Keeps user-supplied metadata credentials dormant until explicit consent. */
object MetadataCredentialPolicy {
    private val v3Key = Regex("^[A-Fa-f0-9]{32}$")

    fun reusableCredential(optedIn: Boolean, credential: String): String =
        credential.trim().takeIf { optedIn } ?: ""

    fun looksLikeTmdbCredential(credential: String): Boolean {
        val value = credential.trim()
        return v3Key.matches(value) || (value.startsWith("eyJ") && value.count { it == '.' } == 2)
    }
}
