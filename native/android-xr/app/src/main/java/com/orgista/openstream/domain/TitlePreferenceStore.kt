package com.orgista.openstream.domain

import android.content.Context

enum class TitleRating { NONE, UP, DOWN }

object TitleRatingPolicy {
    fun toggle(current: TitleRating, requested: TitleRating): TitleRating =
        if (current == requested) TitleRating.NONE else requested
}

class TitlePreferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("openstream.title.preferences.v1", Context.MODE_PRIVATE)

    fun rating(mediaId: String): TitleRating = when {
        mediaId in prefs.getStringSet(KEY_DOWN, emptySet()).orEmpty() -> TitleRating.DOWN
        mediaId in prefs.getStringSet(KEY_UP, prefs.getStringSet(KEY_LIKED, emptySet())).orEmpty() -> TitleRating.UP
        else -> TitleRating.NONE
    }

    fun toggle(mediaId: String, requested: TitleRating): TitleRating {
        val next = TitleRatingPolicy.toggle(rating(mediaId), requested)
        val up = prefs.getStringSet(KEY_UP, prefs.getStringSet(KEY_LIKED, emptySet())).orEmpty().toMutableSet()
        val down = prefs.getStringSet(KEY_DOWN, emptySet()).orEmpty().toMutableSet()
        up.remove(mediaId)
        down.remove(mediaId)
        if (next == TitleRating.UP) up.add(mediaId)
        if (next == TitleRating.DOWN) down.add(mediaId)
        prefs.edit().putStringSet(KEY_UP, up).putStringSet(KEY_DOWN, down).remove(KEY_LIKED).apply()
        return next
    }

    companion object {
        private const val KEY_LIKED = "likedIds"
        private const val KEY_UP = "thumbsUpIds"
        private const val KEY_DOWN = "thumbsDownIds"
    }
}
