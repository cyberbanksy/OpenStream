package com.orgista.openstream.lan

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Minimal DNS wire-format support for the temporary openstream.local A record. */
object LanMdnsProtocol {
    private const val HEADER_BYTES = 12
    private const val TYPE_A = 1
    private const val TYPE_ANY = 255
    private const val CLASS_IN = 1

    fun responseFor(query: ByteArray, host: String, address: ByteArray, ttlSeconds: Int = 120): ByteArray? {
        if (query.size < HEADER_BYTES || address.size != 4 || ttlSeconds < 0) return null
        if (query[2].toInt() and 0x80 != 0) return null
        val questions = unsignedShort(query, 4) ?: return null
        var offset = HEADER_BYTES
        repeat(questions) {
            val name = readName(query, offset) ?: return null
            offset = name.nextOffset
            if (offset + 4 > query.size) return null
            val type = unsignedShort(query, offset) ?: return null
            val questionClass = unsignedShort(query, offset + 2) ?: return null
            offset += 4
            if (isSupportedHost(host, name.value) &&
                (type == TYPE_A || type == TYPE_ANY) &&
                questionClass and 0x7fff == CLASS_IN
            ) {
                return announcementFor(host, address, ttlSeconds)
            }
        }
        return null
    }

    fun announcementFor(host: String, address: ByteArray, ttlSeconds: Int = 120): ByteArray {
        require(address.size == 4) { "An mDNS A record requires an IPv4 address." }
        require(ttlSeconds >= 0) { "TTL cannot be negative." }
        val output = ByteArrayOutputStream(64)
        writeShort(output, 0) // mDNS transaction id
        writeShort(output, 0x8400) // response + authoritative answer
        writeShort(output, 0) // questions
        writeShort(output, 1) // answers
        writeShort(output, 0) // authority records
        writeShort(output, 0) // additional records
        writeName(output, host)
        writeShort(output, TYPE_A)
        writeShort(output, 0x8000 or CLASS_IN) // cache-flush bit + IN
        writeInt(output, ttlSeconds)
        writeShort(output, address.size)
        output.write(address)
        return output.toByteArray()
    }

    fun isSupportedHost(expected: String, candidate: String): Boolean =
        expected.trim().trimEnd('.').equals(candidate.trim().trimEnd('.'), ignoreCase = true)

    private data class ParsedName(val value: String, val nextOffset: Int)

    private fun readName(packet: ByteArray, start: Int): ParsedName? {
        if (start !in packet.indices) return null
        val labels = ArrayList<String>()
        var cursor = start
        var nextOffset = -1
        var jumps = 0
        while (cursor in packet.indices && jumps <= packet.size) {
            val length = packet[cursor].toInt() and 0xff
            when {
                length == 0 -> {
                    if (nextOffset < 0) nextOffset = cursor + 1
                    return ParsedName(labels.joinToString("."), nextOffset)
                }
                length and 0xc0 == 0xc0 -> {
                    if (cursor + 1 >= packet.size) return null
                    val pointer = ((length and 0x3f) shl 8) or (packet[cursor + 1].toInt() and 0xff)
                    if (pointer >= packet.size) return null
                    if (nextOffset < 0) nextOffset = cursor + 2
                    cursor = pointer
                    jumps++
                }
                length and 0xc0 != 0 || length > 63 || cursor + 1 + length > packet.size -> return null
                else -> {
                    labels += String(packet, cursor + 1, length, StandardCharsets.US_ASCII)
                    cursor += length + 1
                    jumps++
                }
            }
        }
        return null
    }

    private fun writeName(output: ByteArrayOutputStream, host: String) {
        host.trim().trimEnd('.').split('.').forEach { label ->
            val bytes = label.toByteArray(StandardCharsets.US_ASCII)
            require(bytes.isNotEmpty() && bytes.size <= 63) { "Invalid mDNS host label." }
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 1 >= bytes.size) return null
        return ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }

    private fun writeShort(output: ByteArrayOutputStream, value: Int) {
        output.write(value ushr 8 and 0xff)
        output.write(value and 0xff)
    }

    private fun writeInt(output: ByteArrayOutputStream, value: Int) {
        output.write(value ushr 24 and 0xff)
        output.write(value ushr 16 and 0xff)
        output.write(value ushr 8 and 0xff)
        output.write(value and 0xff)
    }
}
