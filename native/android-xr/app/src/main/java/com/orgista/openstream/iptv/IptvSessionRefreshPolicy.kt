package com.orgista.openstream.iptv

data class IptvSessionSnapshot(
    val channels: List<IptvChannel> = emptyList(),
    val programs: List<IptvGuideProgram> = emptyList(),
    val revision: Int = Int.MIN_VALUE,
    val refreshedAtMillis: Long = 0L,
    val preparedGuide: LivePreparedGuide? = null,
)

/** Keeps playback Back navigation instant without turning the session into a persistent credential cache. */
object IptvSessionRefreshPolicy {
    const val MAX_AGE_MILLIS = 15 * 60_000L

    fun canReuse(snapshot: IptvSessionSnapshot, revision: Int, nowMillis: Long): Boolean =
        snapshot.channels.isNotEmpty() &&
            snapshot.revision == revision &&
            snapshot.refreshedAtMillis > 0L &&
            nowMillis - snapshot.refreshedAtMillis in 0..MAX_AGE_MILLIS
}
