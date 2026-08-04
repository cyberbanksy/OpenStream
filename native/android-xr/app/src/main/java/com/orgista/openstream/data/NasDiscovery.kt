package com.orgista.openstream.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.orgista.openstream.Logs
import jcifs.smb.SmbFile
import kotlinx.coroutines.delay
import java.util.Collections

data class DiscoveredNas(val name: String, val host: String)

/**
 * Finds SMB servers on the local network with mDNS/DNS-SD (`_smb._tcp`),
 * the same mechanism macOS and most NAS vendors advertise with. Discovery
 * holds a multicast lock for its window because many Android devices filter
 * multicast otherwise.
 */
object NasDiscovery {
    suspend fun discover(context: Context, windowMs: Long = 4_500): List<DiscoveredNas> {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifi?.createMulticastLock("openstream-nas-discovery")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val found = Collections.synchronizedMap(LinkedHashMap<String, DiscoveredNas>())

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(
                    service,
                    object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val address = resolved.host?.hostAddress ?: return
                            if (address.contains(':')) return // keep IPv4 for SMB URLs
                            found[address] = DiscoveredNas(resolved.serviceName, address)
                            Logs.event("nas discovery: found ${resolved.serviceName} at $address")
                        }

                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    },
                )
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Logs.warn("nas discovery failed to start: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        try {
            nsd.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            delay(windowMs)
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { multicastLock?.release() }
        }
        // Give in-flight resolves a beat to land before reporting.
        delay(400)
        Logs.event("nas discovery: ${found.size} server(s)")
        return found.values.toList()
    }

    /** Lists the browseable (non-admin) shares a host offers for the given credentials. */
    fun listShares(host: String, username: String, password: String): List<String> {
        val probe = NasConnection("probe", host, host, "", username, password)
        val root = SmbFile("smb://$host/", Smb.context(probe))
        val shares = root.listFiles() ?: return emptyList()
        return shares
            .map { it.name.trimEnd('/') }
            .filter { it.isNotEmpty() && !it.endsWith("$") && !it.equals("IPC", ignoreCase = true) }
            .sorted()
            .also { Logs.event("nas shares on $host: ${it.size} visible") }
    }
}
