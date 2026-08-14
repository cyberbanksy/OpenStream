package com.orgista.openstream.lan

import android.content.Context
import android.net.wifi.WifiManager
import com.orgista.openstream.Logs
import java.io.Closeable
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/** Advertises a browser-friendly hostname only while the temporary setup page is active. */
class LanSetupMdnsResponder(context: Context) : Closeable {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val generation = AtomicLong(0)
    @Volatile private var socket: MulticastSocket? = null
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null

    /** Returns false when the current network does not allow local multicast. */
    fun start(session: LanSetupSession): Boolean {
        close()
        val currentGeneration = generation.incrementAndGet()
        val uri = runCatching { URI.create(session.url) }.getOrNull() ?: return false
        val address = runCatching { InetAddress.getByName(uri.host) }.getOrNull() as? Inet4Address ?: return false
        val networkInterface = runCatching { NetworkInterface.getByInetAddress(address) }.getOrNull() ?: return false
        val group = InetAddress.getByName(MDNS_GROUP)
        val lock = runCatching {
            wifiManager?.createMulticastLock("openstream-lan-setup")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrElse {
            Logs.warn("setup short address could not acquire multicast", it)
            null
        }
        val multicast = try {
            MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(MDNS_PORT))
                this.networkInterface = networkInterface
                timeToLive = 255
                soTimeout = 1_000
                joinGroup(InetSocketAddress(group, MDNS_PORT), networkInterface)
            }
        } catch (error: Exception) {
            runCatching { lock?.release() }
            Logs.warn("setup short address unavailable; numeric address remains active", error)
            return false
        }
        synchronized(this) {
            if (generation.get() != currentGeneration) {
                runCatching { multicast.close() }
                if (lock?.isHeld == true) runCatching { lock.release() }
                return false
            }
            socket = multicast
            multicastLock = lock
        }
        Thread(
            { serve(multicast, group, address, session.expiresAtMillis, currentGeneration) },
            "openstream-setup-mdns",
        ).apply { isDaemon = true }.start()
        return true
    }

    override fun close() {
        val (activeSocket, activeLock) = synchronized(this) {
            generation.incrementAndGet()
            val currentSocket = socket
            val currentLock = multicastLock
            socket = null
            multicastLock = null
            currentSocket to currentLock
        }
        runCatching { activeSocket?.close() }
        releaseLock(activeLock)
    }

    private fun serve(
        multicast: MulticastSocket,
        group: InetAddress,
        address: Inet4Address,
        expiresAtMillis: Long,
        currentGeneration: Long,
    ) {
        try {
            send(multicast, LanMdnsProtocol.announcementFor(HOST, address.address), group, MDNS_PORT)
            val buffer = ByteArray(1_500)
            while (isCurrent(multicast, currentGeneration) && System.currentTimeMillis() < expiresAtMillis) {
                val request = DatagramPacket(buffer, buffer.size)
                try {
                    multicast.receive(request)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: SocketException) {
                    break
                }
                val query = request.data.copyOfRange(request.offset, request.offset + request.length)
                val response = LanMdnsProtocol.responseFor(query, HOST, address.address) ?: continue
                if (request.port == MDNS_PORT) {
                    send(multicast, response, group, MDNS_PORT)
                } else {
                    send(multicast, response, request.address, request.port)
                }
            }
        } catch (error: Exception) {
            if (isCurrent(multicast, currentGeneration)) {
                Logs.warn("setup short address stopped unexpectedly; numeric address remains active", error)
            }
        } finally {
            stopIfCurrent(multicast, currentGeneration)
        }
    }

    private fun send(socket: MulticastSocket, bytes: ByteArray, address: InetAddress, port: Int) {
        socket.send(DatagramPacket(bytes, bytes.size, address, port))
    }

    private fun isCurrent(active: MulticastSocket, expectedGeneration: Long): Boolean =
        generation.get() == expectedGeneration && socket === active && !active.isClosed

    private fun stopIfCurrent(active: MulticastSocket, expectedGeneration: Long) {
        val lock = synchronized(this) {
            if (generation.get() != expectedGeneration || socket !== active) return
            socket = null
            multicastLock.also { multicastLock = null }
        }
        runCatching { active.close() }
        releaseLock(lock)
    }

    private fun releaseLock(lock: WifiManager.MulticastLock?) {
        if (lock?.isHeld == true) runCatching { lock.release() }
    }

    companion object {
        const val HOST = "openstream.local"
        private const val MDNS_GROUP = "224.0.0.251"
        private const val MDNS_PORT = 5_353
    }
}
