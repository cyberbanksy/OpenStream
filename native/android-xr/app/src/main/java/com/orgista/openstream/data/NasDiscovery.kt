package com.orgista.openstream.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.orgista.openstream.Logs
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections

data class DiscoveredNas(val name: String, val host: String)

/**
 * Finds SMB servers on the local network. mDNS/DNS-SD (`_smb._tcp`) is tried
 * first, then — because many routers filter multicast — a fast TCP sweep of
 * the local /24 on port 445 as a fallback. Between them, a server on the
 * same subnet is found without the user typing an address.
 */
object NasDiscovery {
    suspend fun discover(context: Context, windowMs: Long = 4_000): List<DiscoveredNas> {
        val found = LinkedHashMap<String, DiscoveredNas>()
        mdns(context, windowMs).forEach { found[it.host] = it }
        if (found.isEmpty()) {
            Logs.event("nas discovery: mDNS found nothing, sweeping subnet")
            sweepSubnet(context).forEach { found.putIfAbsent(it.host, it) }
        }
        Logs.event("nas discovery: ${found.size} server(s) total")
        return found.values.toList()
    }

    private suspend fun mdns(context: Context, windowMs: Long): List<DiscoveredNas> {
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
                            if (address.contains(':')) return
                            found[address] = DiscoveredNas(resolved.serviceName, address)
                            Logs.event("nas discovery: mDNS found ${resolved.serviceName} at $address")
                        }

                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    },
                )
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Logs.warn("nas discovery: mDNS failed to start ($errorCode)")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        return try {
            nsd.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            delay(windowMs)
            runCatching { nsd.stopServiceDiscovery(listener) }
            delay(300)
            found.values.toList()
        } catch (error: Exception) {
            Logs.warn("nas discovery: mDNS error", error)
            emptyList()
        } finally {
            if (multicastLock?.isHeld == true) {
                runCatching { multicastLock.release() }
            }
        }
    }

    /** TCP-connect to port 445 across the device's /24; whatever answers hosts SMB. */
    private suspend fun sweepSubnet(context: Context): List<DiscoveredNas> {
        val prefix = localSubnetPrefix(context) ?: return emptyList()
        Logs.event("nas discovery: sweeping ${prefix}0/24:445")
        return coroutineScope {
            (1..254).map { host ->
                async(Dispatchers.IO) {
                    val address = "$prefix$host"
                    if (portOpen(address, 445, 220)) {
                        Logs.event("nas discovery: sweep found $address")
                        DiscoveredNas(address, address)
                    } else {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun portOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun localSubnetPrefix(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ip = wifi.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        val a = ip and 0xff
        val b = ip shr 8 and 0xff
        val c = ip shr 16 and 0xff
        return "$a.$b.$c."
    }

    /** Lists the browseable (non-admin) shares a host offers for the given credentials. */
    suspend fun listShares(host: String, username: String, password: String): List<String> =
        withContext(Dispatchers.IO) {
            val probe = NasConnection("probe", host, host, "", username, password)
            val root = SmbFile("smb://$host/", Smb.context(probe))
            val shares = root.listFiles() ?: return@withContext emptyList()
            shares
                .map { it.name.trimEnd('/') }
                .filter { it.isNotEmpty() && !it.endsWith("$") && !it.equals("IPC", ignoreCase = true) }
                .sorted()
                .also { Logs.event("nas shares on $host: ${it.size} visible") }
        }
}
