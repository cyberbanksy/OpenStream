package com.orgista.openstream.ui

enum class SourceCollection {
    LIBRARIES,
    NAS_SHARES,
    YOUTUBE,
    ADD_ONS,
    LIVE_TV,
}

object SourceManagementPolicy {
    fun showDeleteAction(itemCount: Int): Boolean = itemCount > 0

    fun showRemoveControl(collection: SourceCollection, activeCollection: SourceCollection?): Boolean =
        collection == activeCollection

    fun nextDeleteCollection(collection: SourceCollection, activeCollection: SourceCollection?): SourceCollection? =
        if (collection == activeCollection) null else collection

    fun deleteActionLabel(collection: SourceCollection, active: Boolean): String {
        if (active) return "Done"
        return when (collection) {
            SourceCollection.LIBRARIES -> "Delete libraries"
            SourceCollection.NAS_SHARES -> "Delete shares"
            SourceCollection.YOUTUBE -> "Delete channels"
            SourceCollection.ADD_ONS -> "Delete add-ons"
            SourceCollection.LIVE_TV -> "Delete source"
        }
    }
}
