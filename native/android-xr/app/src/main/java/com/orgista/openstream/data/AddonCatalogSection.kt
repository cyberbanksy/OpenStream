package com.orgista.openstream.data

/** One catalog category exactly as an add-on exposes it. */
data class AddonCatalogSection(
    val addonId: String,
    val addonName: String,
    val catalogId: String,
    val catalogName: String,
    val items: List<MediaItem>,
)
