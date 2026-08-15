package com.orgista.openstream.streaming

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.orgista.openstream.BuildConfig
import com.orgista.openstream.data.UserAgents
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BandwidthMeasurement(
    val mbps: Double,
    val measuredAtMillis: Long,
    val bytes: Int,
    val transport: NetworkTransport = NetworkTransport.OTHER,
    val networkKey: Long = 0L,
)

enum class NetworkTransport { OFFLINE, WIFI, CELLULAR, ETHERNET, OTHER }

data class ActiveNetworkProfile(
    val transport: NetworkTransport,
    val validated: Boolean,
    val metered: Boolean,
    val advertisedDownstreamKbps: Int,
    val networkKey: Long,
)

object ActiveNetworkInspector {
    fun inspect(context: Context): ActiveNetworkProfile {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return ActiveNetworkProfile(NetworkTransport.OFFLINE, false, false, 0, 0L)
        val network = manager.activeNetwork
            ?: return ActiveNetworkProfile(NetworkTransport.OFFLINE, false, false, 0, 0L)
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return ActiveNetworkProfile(NetworkTransport.OFFLINE, false, false, 0, 0L)
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        }
        return ActiveNetworkProfile(
            transport = transport,
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            advertisedDownstreamKbps = capabilities.linkDownstreamBandwidthKbps.coerceAtLeast(0),
            networkKey = network.networkHandle,
        )
    }
}

object NetworkMeasurementPolicy {
    fun shouldMeasureBeforePlayback(transport: NetworkTransport, cached: Boolean): Boolean =
        transport == NetworkTransport.CELLULAR && !cached

    fun sampleSizes(transport: NetworkTransport): List<Int> = when (transport) {
        NetworkTransport.CELLULAR -> listOf(100_000, 500_000, 2_000_000, 5_000_000)
        else -> listOf(256_000, 1_000_000, 4_000_000, 16_000_000)
    }

    /** Median of ramped transfers after discarding the connection-warmup sample. */
    fun representativeMbps(samples: List<Double>): Double {
        val usable = if (samples.size >= 3) samples.drop(1) else samples
        if (usable.isEmpty()) throw IOException("Connection test returned no samples.")
        val sorted = usable.sorted()
        return sorted[sorted.size / 2].coerceIn(0.1, 10_000.0)
    }
}

data class BandwidthProbeRequestProfile(
    val querySuffix: String,
    val headers: Map<String, String>,
)

object BandwidthProbeRequestPolicy {
    fun profiles(nowMillis: Long = System.currentTimeMillis()): List<BandwidthProbeRequestProfile> = listOf(
        BandwidthProbeRequestProfile(
            querySuffix = "",
            headers = mapOf(
                "Accept" to "application/octet-stream",
                "Cache-Control" to "no-cache, no-store",
                "User-Agent" to UserAgents.speedTest,
            ),
        ),
        // Cloudflare's public test is normally called by a browser. Some edge
        // locations reject an app-style request, so retry with the same-origin
        // browser headers and a cache-busting query before reporting failure.
        BandwidthProbeRequestProfile(
            querySuffix = "&openstream=$nowMillis",
            headers = mapOf(
                "Accept" to "*/*",
                "Cache-Control" to "no-cache",
                "Origin" to "https://speed.cloudflare.com",
                "Referer" to "https://speed.cloudflare.com/",
                "User-Agent" to UserAgents.browser,
            ),
        ),
    )

    fun shouldRetry(statusCode: Int): Boolean = statusCode in setOf(401, 403, 429) || statusCode >= 500
}

class BandwidthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("openstream.bandwidth.v2", Context.MODE_PRIVATE)

    fun fresh(
        maxAgeMillis: Long = MAX_AGE_MILLIS,
        transport: NetworkTransport? = null,
        networkKey: Long? = null,
    ): BandwidthMeasurement? {
        val measuredAt = prefs.getLong(KEY_TIME, 0L)
        if (measuredAt <= 0L || System.currentTimeMillis() - measuredAt > maxAgeMillis) return null
        val mbps = Double.fromBits(prefs.getLong(KEY_MBPS, 0L))
        val bytes = prefs.getInt(KEY_BYTES, 0)
        val savedTransport = runCatching {
            NetworkTransport.valueOf(prefs.getString(KEY_TRANSPORT, NetworkTransport.OTHER.name).orEmpty())
        }.getOrDefault(NetworkTransport.OTHER)
        val savedNetworkKey = prefs.getLong(KEY_NETWORK, 0L)
        if (transport != null && savedTransport != transport) return null
        if (networkKey != null && networkKey != 0L && savedNetworkKey != networkKey) return null
        return BandwidthMeasurement(mbps, measuredAt, bytes, savedTransport, savedNetworkKey)
            .takeIf { mbps > 0.0 && bytes > 0 }
    }

    fun save(measurement: BandwidthMeasurement) {
        prefs.edit()
            .putLong(KEY_MBPS, measurement.mbps.toBits())
            .putLong(KEY_TIME, measurement.measuredAtMillis)
            .putInt(KEY_BYTES, measurement.bytes)
            .putString(KEY_TRANSPORT, measurement.transport.name)
            .putLong(KEY_NETWORK, measurement.networkKey)
            .apply()
    }

    companion object {
        const val MAX_AGE_MILLIS = 6L * 60L * 60L * 1_000L
        const val CELLULAR_MAX_AGE_MILLIS = 30L * 60L * 1_000L
        private const val KEY_MBPS = "mbps"
        private const val KEY_TIME = "measuredAt"
        private const val KEY_BYTES = "bytes"
        private const val KEY_TRANSPORT = "transport"
        private const val KEY_NETWORK = "network"
    }
}

object NetworkBandwidthProbe {
    fun measure(transport: NetworkTransport): BandwidthMeasurement {
        // Blanking SPEED_TEST_URL (see build.gradle.kts) disables the probe
        // without a code change; surface it as the usual IOException callers
        // already treat as best-effort.
        if (BuildConfig.SPEED_TEST_URL.isBlank()) {
            throw IOException("Speed test is disabled for this build.")
        }
        val rates = ArrayList<Double>()
        var totalBytes = 0
        NetworkMeasurementPolicy.sampleSizes(transport).forEach { requestedBytes ->
            val sample = downloadSample(requestedBytes)
            rates += sample.first
            totalBytes += sample.second
        }
        return BandwidthMeasurement(
            mbps = NetworkMeasurementPolicy.representativeMbps(rates),
            measuredAtMillis = System.currentTimeMillis(),
            bytes = totalBytes,
            transport = transport,
        )
    }

    private fun downloadSample(sampleBytes: Int): Pair<Double, Int> {
        var lastFailure: IOException? = null
        val profiles = BandwidthProbeRequestPolicy.profiles()
        profiles.forEachIndexed { index, profile ->
            try {
                return downloadSample(sampleBytes, profile)
            } catch (failure: ProbeHttpException) {
                lastFailure = IOException("Speed test unavailable (HTTP ${failure.statusCode}).")
                if (!BandwidthProbeRequestPolicy.shouldRetry(failure.statusCode) || index == profiles.lastIndex) {
                    throw requireNotNull(lastFailure)
                }
            } catch (failure: IOException) {
                lastFailure = failure
                if (index == profiles.lastIndex) throw failure
            }
        }
        throw lastFailure ?: IOException("Speed test unavailable.")
    }

    private fun downloadSample(sampleBytes: Int, profile: BandwidthProbeRequestProfile): Pair<Double, Int> {
        val connection = URL(
            "${BuildConfig.SPEED_TEST_URL}?bytes=$sampleBytes${profile.querySuffix}",
        ).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 25_000
        connection.useCaches = false
        profile.headers.forEach(connection::setRequestProperty)
        val start = SystemClock.elapsedRealtimeNanos()
        try {
            if (connection.responseCode !in 200..299) {
                throw ProbeHttpException(connection.responseCode)
            }
            var bytes = 0
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (bytes < sampleBytes) {
                    val count = input.read(buffer, 0, minOf(buffer.size, sampleBytes - bytes))
                    if (count < 0) break
                    bytes += count
                }
            }
            if (bytes < sampleBytes / 2) throw IOException("Connection test ended too early.")
            val seconds = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000_000.0
            return ((bytes * 8.0 / seconds / 1_000_000.0).coerceIn(0.1, 10_000.0)) to bytes
        } finally {
            connection.disconnect()
        }
    }

    private class ProbeHttpException(val statusCode: Int) : IOException()
}

object BandwidthRuntime {
    /**
     * Automatic preflight is cellular-only. Unmetered Wi-Fi/Ethernet starts
     * with the best device-compatible route and is measured only after a slow start.
     */
    suspend fun forPlayback(context: Context): BandwidthMeasurement? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val profile = ActiveNetworkInspector.inspect(appContext)
        if (!profile.validated) return@withContext null
        val store = BandwidthStore(appContext)
        val maxAge = if (profile.transport == NetworkTransport.CELLULAR) {
            BandwidthStore.CELLULAR_MAX_AGE_MILLIS
        } else {
            BandwidthStore.MAX_AGE_MILLIS
        }
        val cached = store.fresh(maxAge, profile.transport, profile.networkKey)
        if (profile.transport != NetworkTransport.CELLULAR) return@withContext null
        cached ?: NetworkBandwidthProbe.measure(profile.transport)
            .copy(networkKey = profile.networkKey)
            .also(store::save)
    }

    suspend fun optimizeAfterSlowStart(context: Context): BandwidthMeasurement? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val profile = ActiveNetworkInspector.inspect(appContext)
        if (!profile.validated || profile.transport == NetworkTransport.OFFLINE) return@withContext null
        val store = BandwidthStore(appContext)
        store.fresh(transport = profile.transport, networkKey = profile.networkKey)
            ?: NetworkBandwidthProbe.measure(profile.transport)
                .copy(networkKey = profile.networkKey)
                .also(store::save)
    }

    suspend fun currentOrMeasure(context: Context, force: Boolean = false): BandwidthMeasurement =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val profile = ActiveNetworkInspector.inspect(appContext)
            if (!profile.validated) throw IOException("No validated internet connection.")
            val store = BandwidthStore(appContext)
            if (!force) store.fresh(transport = profile.transport, networkKey = profile.networkKey)
                ?.let { return@withContext it }
            NetworkBandwidthProbe.measure(profile.transport)
                .copy(networkKey = profile.networkKey)
                .also(store::save)
        }
}
