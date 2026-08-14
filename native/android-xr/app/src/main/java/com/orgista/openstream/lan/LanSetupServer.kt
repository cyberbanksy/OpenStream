package com.orgista.openstream.lan

import java.io.Closeable
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

data class LanSetupSession(val url: String, val expiresAtMillis: Long)

object LanSetupPresentation {
    /** The QR carries the complete short-lived setup URL; no manual code is required. */
    fun qrContent(session: LanSetupSession): String = session.url

    /** Friendly mDNS address intended to be typed into a desktop browser. */
    fun computerAddress(session: LanSetupSession): String =
        computerUrl(session).removePrefix("http://").removeSuffix("/")

    fun computerUrl(session: LanSetupSession): String {
        val uri = URI.create(session.url)
        return "http://openstream.local:${uri.port}/"
    }

    /** Numeric address for networks that filter multicast name discovery. */
    fun fallbackComputerAddress(session: LanSetupSession): String =
        fallbackComputerUrl(session).removePrefix("http://").removeSuffix("/")

    fun fallbackComputerUrl(session: LanSetupSession): String {
        val uri = URI.create(session.url)
        return "http://${uri.host}:${uri.port}/"
    }
}

/** Explicit, short-lived trusted-LAN page for entering one HTTPS add-on manifest. */
class LanSetupServer(
    private val addManifest: (String) -> String,
    private val onAdded: (String) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(false)
    @Volatile private var socket: ServerSocket? = null

    fun start(durationMillis: Long = 10 * 60 * 1_000L): LanSetupSession {
        close()
        val address = localIpv4() ?: throw IllegalStateException("Connect this device to a local Wi-Fi network first.")
        val server = ServerSocket().apply {
            reuseAddress = true
            soTimeout = 1_000
            runCatching { bind(InetSocketAddress("0.0.0.0", DEFAULT_PORT)) }
                .getOrElse { bind(InetSocketAddress("0.0.0.0", 0)) }
        }
        val expires = System.currentTimeMillis() + durationMillis
        val session = LanSetupSession(
            url = "http://${address.hostAddress}:${server.localPort}/openstream",
            expiresAtMillis = expires,
        )
        socket = server
        running.set(true)
        Thread({ serve(server, session) }, "openstream-lan-setup").apply { isDaemon = true }.start()
        return session
    }

    override fun close() {
        val active = synchronized(this) {
            running.set(false)
            socket.also { socket = null }
        }
        runCatching { active?.close() }
    }

    private fun serve(server: ServerSocket, session: LanSetupSession) {
        try {
            while (isCurrent(server) && System.currentTimeMillis() < session.expiresAtMillis) {
                val client = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: SocketException) {
                    // Closing the temporary server deliberately unblocks
                    // accept(). It is normal shutdown, not an app failure.
                    break
                }
                runCatching { client.use { handle(it, session) } }
            }
        } finally {
            stopIfCurrent(server)
        }
    }

    private fun isCurrent(server: ServerSocket): Boolean =
        running.get() && socket === server && !server.isClosed

    private fun stopIfCurrent(server: ServerSocket) {
        synchronized(this) {
            if (socket === server) {
                running.set(false)
                socket = null
            }
        }
        runCatching { server.close() }
    }

    private fun handle(client: Socket, session: LanSetupSession) {
        client.soTimeout = 8_000
        if (!LanSetupProtocol.isAllowedClient(client.inetAddress)) {
            respond(client, 403, "Forbidden", "This page is available only on the local network.")
            return
        }
        val reader = client.getInputStream().bufferedReader(StandardCharsets.UTF_8)
        val requestLine = reader.readLine()?.take(2_048).orEmpty()
        val parts = requestLine.split(' ')
        val expectedPath = URI.create(session.url).rawPath
        if (parts.size < 2 || (parts[1] != expectedPath && parts[1] != "/")) {
            respond(client, 404, "Not Found", "Not found.")
            return
        }
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: return
            if (line.isEmpty()) break
            if (line.length > 4_096 || headers.size >= 40) {
                respond(client, 413, "Too Large", "Request headers are too large.")
                return
            }
            val separator = line.indexOf(':')
            if (separator > 0) headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
        }
        when (parts[0]) {
            "GET" -> respond(client, 200, "OK", setupPage(), "text/html; charset=utf-8")
            "POST" -> {
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                if (length !in 1..MAX_BODY_CHARS) {
                    respond(client, 413, "Too Large", "Invalid request size.")
                    return
                }
                val buffer = CharArray(length)
                var read = 0
                while (read < length) {
                    val count = reader.read(buffer, read, length - read)
                    if (count < 0) break
                    read += count
                }
                val form = LanSetupProtocol.parseForm(String(buffer, 0, read))
                val manifest = form["manifest"].orEmpty().trim()
                val urlError = LanSetupProtocol.manifestUrlError(manifest)
                if (urlError != null) {
                    respond(client, 400, "Bad Request", setupPage(urlError, manifest), "text/html; charset=utf-8")
                    return
                }
                val result = runCatching { addManifest(manifest) }
                result.fold(
                    onSuccess = { name ->
                        respond(client, 200, "OK", successPage(name), "text/html; charset=utf-8")
                        onAdded(name)
                        running.set(false)
                    },
                    onFailure = { error ->
                        respond(
                            client,
                            400,
                            "Bad Request",
                            setupPage(error.message ?: "The add-on could not be validated.", manifest),
                            "text/html; charset=utf-8",
                        )
                    },
                )
            }
            else -> respond(client, 405, "Method Not Allowed", "Use GET or POST.")
        }
    }

    private fun respond(client: Socket, code: Int, status: String, body: String, contentType: String = "text/plain; charset=utf-8") {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        client.getOutputStream().buffered().use { output ->
            output.write("HTTP/1.1 $code $status\r\n".toByteArray())
            output.write("Content-Type: $contentType\r\n".toByteArray())
            output.write("Content-Length: ${bytes.size}\r\n".toByteArray())
            output.write("Cache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n".toByteArray())
            output.write("Referrer-Policy: no-referrer\r\n".toByteArray())
            output.write("Content-Security-Policy: default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'\r\n\r\n".toByteArray())
            output.write(bytes)
        }
    }

    private fun setupPage(error: String? = null, manifest: String = ""): String {
        val feedback = error?.let {
            """<div id="server-error" class="feedback error" role="alert"><strong>Couldn’t add this URL</strong><span>${html(it)}</span></div>"""
        }.orEmpty()
        return """
            <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="color-scheme" content="dark"><title>OpenStream setup</title>
            <style>$BASE_STYLES</style></head>
            <body><div class="page">$BRAND_HEADER<main class="card"><p class="eyebrow">SET UP OVER WI-FI</p><h1>Add a source</h1><p class="intro">Paste the secure manifest address supplied by your Stremio add-on.</p>
            <form id="setup-form" method="post" autocomplete="off" novalidate><label for="manifest">Manifest URL</label><input id="manifest" name="manifest" type="url" required inputmode="url" placeholder="https://addon.example/manifest.json" value="${html(manifest)}" autocapitalize="none" autocomplete="url" spellcheck="false" aria-describedby="example status"><p id="example" class="example">Example: https://addon.example/manifest.json<br>You can also paste the add-on’s HTTPS base URL.</p>$feedback<div class="actions"><button id="submit" type="submit">Add source</button><p id="status" class="status" role="status" aria-live="polite"></p></div></form></main></div>
            <script>(function(){var form=document.getElementById('setup-form'),input=document.getElementById('manifest'),button=document.getElementById('submit'),status=document.getElementById('status'),serverError=document.getElementById('server-error');function issue(raw){var value=raw.trim();if(!value)return'Enter the add-on manifest URL.';var match=value.match(/^([a-z][a-z0-9+.-]*):\/\//i);if(!match)return'Add https:// at the beginning. Example: https://addon.example/manifest.json';var scheme=match[1].toLowerCase();if(scheme==='http')return"HTTP links are not supported. Use the add-on's HTTPS manifest URL.";if(scheme!=='https')return scheme+':// links are not supported here. Paste the add-on\'s HTTPS manifest URL.';var url;try{url=new URL(value)}catch(e){return'This URL is not valid. Example: https://addon.example/manifest.json'}if(!url.hostname)return'This URL is missing an add-on host. Example: https://addon.example/manifest.json';if(url.username||url.password)return'URLs containing a username or password are not supported.';if(url.search)return'URL query options are not supported. Paste the complete HTTPS manifest URL supplied by the add-on.';if(url.hash)return'URL fragments are not supported. Remove everything after # and try again.';var parts=url.pathname.split('/').filter(Boolean),last=parts.length?parts[parts.length-1]:'';if(/\.json$/i.test(last)&&last.toLowerCase()!=='manifest.json')return'This URL points to '+last+'. Paste a manifest.json URL or the add-on\'s base URL.';return''}function show(message){status.textContent=message;status.className='status error';input.setAttribute('aria-invalid','true');input.focus()}input.addEventListener('input',function(){input.removeAttribute('aria-invalid');status.textContent='';status.className='status';if(serverError)serverError.hidden=true});form.addEventListener('submit',function(event){var message=issue(input.value);if(message){event.preventDefault();show(message);return}button.disabled=true;button.textContent='Checking add-on…';status.textContent='Checking the manifest and connecting…';status.className='status checking'});})();</script></body></html>
        """.trimIndent()
    }

    private fun successPage(name: String): String = """
        <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="color-scheme" content="dark"><title>Connected to OpenStream</title>
        <style>$BASE_STYLES.check{width:42px;height:42px;margin-bottom:19px;border:1px solid #fafafa;border-radius:50%;color:#fafafa;display:grid;place-items:center;font-size:24px;font-weight:900}.success-copy{margin:0;color:#a1a1a1;font-size:15px;line-height:1.55}.name{color:#fafafa;font-weight:800}.note{margin-top:17px;padding-top:17px;border-top:1px solid #262626;font-size:13px}</style></head>
        <body><div class="page">$BRAND_HEADER<main class="card" role="status"><div class="check" aria-hidden="true">✓</div><p class="eyebrow">SOURCE CONNECTED</p><h1>Connected to OpenStream</h1><p class="success-copy"><span class="name">${html(name)}</span><br>Added to Sources and ready to use.</p><p class="success-copy note">You can close this page and return to OpenStream.</p></main></div></body></html>
    """.trimIndent()

    private fun html(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun localIpv4(): Inet4Address? = Collections.list(NetworkInterface.getNetworkInterfaces())
        .asSequence()
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }

    companion object {
        private const val DEFAULT_PORT = 11_470
        private const val MAX_BODY_CHARS = 8_192
        private const val BASE_STYLES = ":root{font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;color:#fafafa;background:#000}*{box-sizing:border-box}body{min-height:100vh;margin:0;padding:max(22px,env(safe-area-inset-top)) max(18px,env(safe-area-inset-right)) max(22px,env(safe-area-inset-bottom)) max(18px,env(safe-area-inset-left));background:#000;display:grid;place-items:center}.page{width:min(100%,540px)}.brand{display:flex;align-items:center;gap:10px;margin:0 0 22px 2px;color:#fafafa;font-size:22px;font-weight:850;letter-spacing:-.045em}.brand-mark{width:30px;height:30px;object-fit:contain}.card{width:100%;max-width:100%;padding:clamp(22px,6vw,34px);border:1px solid #262626;border-radius:14px;background:#0b0b0b}.eyebrow{margin:0 0 9px;color:#a1a1a1;font-size:11px;font-weight:800;letter-spacing:.14em}h1{margin:0 0 10px;font-size:clamp(27px,8vw,36px);line-height:1.08;letter-spacing:-.035em}.intro{margin:0 0 24px;color:#a1a1a1;font-size:15px;line-height:1.5}form{max-width:100%;display:grid;gap:9px}label{font-size:13px;font-weight:750;color:#fafafa}input{appearance:none;width:100%;min-height:50px;padding:13px 14px;border:1px solid #3a3a3a;border-radius:8px;background:#171717;color:#fafafa;font:inherit;outline:none}input::placeholder{color:#737373}input:focus{border-color:#fafafa;box-shadow:0 0 0 2px #fafafa24}input[aria-invalid=true]{border-color:#fb2c36;box-shadow:0 0 0 2px #fb2c3624}.example{margin:0;color:#858585;font-size:12px;line-height:1.45}.feedback{display:grid;gap:3px;margin:9px 0 2px;padding:11px 12px;border:1px solid #5f2025;border-radius:8px;background:#200b0d;font-size:13px;line-height:1.4}.feedback.error{color:#ffb4b8}.feedback strong{color:#fafafa}.actions{display:flex;align-items:center;gap:13px;flex-wrap:wrap;margin-top:10px}button{width:auto;min-width:126px;min-height:44px;padding:10px 18px;border:1px solid #fafafa;border-radius:8px;background:#fafafa;color:#000;font:inherit;font-size:14px;font-weight:800;cursor:pointer}button:hover{background:#d4d4d4}button:focus-visible{outline:3px solid #fafafa;outline-offset:3px}button:disabled{cursor:wait;opacity:.62}.status{min-height:20px;margin:0;color:#a1a1a1;font-size:12px}.status.error{color:#ff9da3}.status.checking{color:#d4d4d4}@media(max-width:420px){body{padding:18px 14px}.brand{margin-bottom:16px}.card{padding:22px 20px;border-radius:12px}.actions{align-items:flex-start;flex-direction:column}.status{min-height:0}}"
        private const val BRAND_MARK_DATA_URI =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAMAAADVRocKAAAAIGNIUk0AAH" +
            "omAACAhAAA+gAAAIDoAAB1MAAA6mAAADqYAAAXcJy6UTwAAAA8UExURQAAAPX4+fX4+fX4+fX4+fX4+f" +
            "X4+fX4+fX4+fX4+fX4+fX4+fX4+fX4+fX4+fX4+fX4+fX4+fX4+f///1fLw4UAAAASdFJOUwAWdNdbCi" +
            "6ro5SPihP1Ac1AqpUiHEwAAAABYktHRBMMu1yWAAAAB3RJTUUH6ggOESMztzlxhgAAACV0RVh0ZGF0ZT" +
            "pjcmVhdGUAMjAyNi0wOC0xNFQxNzozNTo1MSswMDowMFV76YgAAAAldEVYdGRhdGU6bW9kaWZ5ADIwMj" +
            "YtMDgtMTRUMTc6MzU6NTErMDA6MDAkJlE0AAAAKHRFWHRkYXRlOnRpbWVzdGFtcAAyMDI2LTA4LTE0VD" +
            "E3OjM1OjUxKzAwOjAwczNw6wAAAMVJREFUaN7t11kOwyAMBFAgexOy+P6Hrap+tF9gQ0dB6rwDzCg4Rs" +
            "I5IiIiIrqND51odcGb83t1+lsPzrc2eHO+iOmUQkFBsBTo5/s1aUtBQb5IU18AnwH8L4LvAX6T8XcRtW" +
            "AYp3l52C3zNA6K+LVg1z7WXMUWq/JF4pbM34/KfJFjTxWc1fkiZ6rg+kHBlSqoncBLvLUAfkTwIcN/U/" +
            "ii4a8KB7/sqAF8QmXOp2C3+ITKa+oL4DPgE0p1SnxCERER0R96Ah9altW8VVuhAAAAAElFTkSuQmCC"
        private const val BRAND_HEADER = "<header class=\"brand\" aria-label=\"OpenStream\"><img class=\"brand-mark\" src=\"$BRAND_MARK_DATA_URI\" alt=\"\"><span>OpenStream</span></header>"
    }
}
