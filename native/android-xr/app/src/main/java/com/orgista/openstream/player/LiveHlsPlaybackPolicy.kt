package com.orgista.openstream.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

object LiveHlsPlaybackPolicy {
    fun useCaptionDescriptorGuard(route: String): Boolean {
        val path = route.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".m3u8") || ".m3u8/" in path
    }
}

/**
 * Selects extraction per fallback route. HLS bypasses malformed in-band caption
 * descriptors while still exposing the conventional CEA-608 track; direct TS,
 * MP4, and other live routes retain Media3's automatic extractor selection.
 */
@OptIn(UnstableApi::class)
class LiveMediaSourceFactory(dataSourceFactory: DataSource.Factory) : MediaSource.Factory {
    private val automaticFactory = DefaultMediaSourceFactory(dataSourceFactory)
    private val guardedHlsFactory = HlsMediaSource.Factory(dataSourceFactory).setExtractorFactory(
        DefaultHlsExtractorFactory(
            DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS,
            true,
        ),
    )

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        automaticFactory.setDrmSessionManagerProvider(provider)
        guardedHlsFactory.setDrmSessionManagerProvider(provider)
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        automaticFactory.setLoadErrorHandlingPolicy(policy)
        guardedHlsFactory.setLoadErrorHandlingPolicy(policy)
    }

    override fun getSupportedTypes(): IntArray =
        (automaticFactory.supportedTypes.asSequence() + guardedHlsFactory.supportedTypes.asSequence())
            .distinct()
            .toList()
            .toIntArray()

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val route = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        return if (LiveHlsPlaybackPolicy.useCaptionDescriptorGuard(route)) {
            guardedHlsFactory.createMediaSource(mediaItem)
        } else {
            automaticFactory.createMediaSource(mediaItem)
        }
    }
}
