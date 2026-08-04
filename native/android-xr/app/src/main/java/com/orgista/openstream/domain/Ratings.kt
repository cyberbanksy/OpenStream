package com.orgista.openstream.domain

/**
 * Certification normalization shared by every screen: MPAA and US TV labels
 * map to a comparable minimum age. Unknown or missing labels normalize to
 * null, and Kids Mode always treats null as blocked.
 */
enum class KidsCap(val label: String, val description: String, val maxAge: Int, val examples: String) {
    LITTLE_KIDS("Little kids", "Only all-ages titles.", 6, "G · TV-Y · TV-G"),
    BIG_KIDS("Big kids", "All-ages plus parental-guidance titles.", 9, "Up to PG · TV-Y7 · TV-PG"),
    TEENS("Teens", "Everything below restricted certifications.", 14, "Up to PG-13 · TV-14"),
}

private val mpaaAges = mapOf(
    "G" to 0,
    "PG" to 8,
    "PG-13" to 13,
    "PG13" to 13,
    "PG-14" to 14,
    "R" to 17,
    "NC-17" to 18,
    "NC17" to 18,
    "X" to 18,
)

private val tvAges = mapOf(
    "TV-Y" to 0,
    "TV-Y7" to 7,
    "TV-Y7-FV" to 7,
    "TV-G" to 0,
    "TV-PG" to 8,
    "TV-14" to 14,
    "TV-MA" to 17,
)

fun normalizedRatingAge(rating: String?): Int? {
    if (rating.isNullOrBlank()) return null
    val label = rating.trim().uppercase().replace(Regex("\\s+"), "-")
    return mpaaAges[label] ?: tvAges[label]
}

fun isRatingAllowed(rating: String?, maxAge: Int): Boolean {
    val age = normalizedRatingAge(rating) ?: return false
    return age <= maxAge
}
