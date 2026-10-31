package com.yash.standbytv.gphotos

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * A tiny web page served by the TV on your home Wi-Fi, so the Google client ID and secret
 * can be pasted from a phone or PC instead of typed with the remote.
 * Only reachable on the local network, protected by a random one-time path, and stopped
 * as soon as the setup screen closes.
 */
class SetupServer(
    private val ipProvider: () -> String? = { lanIp() },
    private val onSaved: (id: String, secret: String) -> Unit,
) {
    private var server: ServerSocket? = null
    private val token = (1..8).map { "abcdefghjkmnpqrstuvwxyz23456789"[Random.nextInt(31)] }.joinToString("")
    @Volatile private var running = false

    /** Starts listening; returns the URL to show as a QR code, or null if the TV has no LAN address. */
    fun start(): String? {
        val ip = ipProvider() ?: return null
        val s = ServerSocket(0)
        server = s
        running = true
        thread(name = "setup-server", isDaemon = true) {
            while (running) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }
        return "http://$ip:${s.localPort}/$token"
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 15_000
        val inp = BufferedInputStream(c.getInputStream())
        val head = readHead(inp) ?: return
        val lines = head.split("\r\n")
        val (method, path) = lines[0].split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        if (path.trimEnd('/') != "/$token") { respond(c, 404, page("Not found", "<p>Scan the QR code on the TV again.</p>")); return }
        if (method == "POST") {
            val len = lines.firstOrNull { it.lowercase().startsWith("content-length:") }
                ?.substringAfter(":")?.trim()?.toIntOrNull()?.coerceIn(0, 16_384) ?: 0
            val buf = ByteArray(len); var read = 0
            while (read < len) { val n = inp.read(buf, read, len - read); if (n < 0) break; read += n }
            val form = String(buf, 0, read).split("&").mapNotNull {
                val kv = it.split("=", limit = 2); if (kv.size == 2) URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8") else null
            }.toMap()
            val id = form["client_id"].orEmpty().trim()
            val secret = form["client_secret"].orEmpty().trim()
            if (!id.endsWith(".apps.googleusercontent.com") || secret.length < 10) {
                respond(c, 200, page("Check the values", "<p class=err>That doesn't look like a Google OAuth client. The Client ID ends with <b>.apps.googleusercontent.com</b>.</p>" + form(id, secret)))
                return
            }
            onSaved(id, secret)
            respond(c, 200, page("Saved ✓", "<p>All set. Look at the TV and choose <b>Sign in with Google</b>.</p>"))
        } else {
            respond(c, 200, page("Connect Google Photos", "<p>Paste the OAuth client you created in Google Cloud " +
                "(type <b>TVs and Limited Input devices</b>). Steps are in the README.</p>" + form("", "")))
        }
    }

    private fun readHead(inp: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var last4 = 0
        while (out.size() < 16_384) {
            val b = inp.read(); if (b < 0) return null
            out.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) return out.toString("UTF-8")
        }
        return null
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

    private fun form(id: String, secret: String) = """
        <form method=post>
          <label>Client ID</label><input name=client_id value="${esc(id)}" placeholder="1234-abc.apps.googleusercontent.com" autocomplete=off>
          <label>Client secret</label><input name=client_secret value="${esc(secret)}" placeholder="GOCSPX-…" autocomplete=off>
          <button>Save to TV</button>
        </form>"""

    private fun page(title: String, body: String) = """<!doctype html><html><head><meta charset=utf-8>
        <meta name=viewport content="width=device-width,initial-scale=1"><title>StandBy TV</title>
        <style>body{font-family:system-ui,sans-serif;background:#000;color:#fff;max-width:520px;margin:0 auto;padding:28px 20px}
        h1{font-size:24px}p{color:#c7c7cc;line-height:1.5}.err{color:#ff9f0a}label{display:block;margin:18px 0 6px;color:#8e8e93;font-size:14px}
        input{width:100%;box-sizing:border-box;padding:14px;border-radius:12px;border:1px solid #3a3a3c;background:#1c1c1e;color:#fff;font-size:16px}
        button{margin-top:24px;width:100%;padding:15px;border:0;border-radius:12px;background:#ff9f0a;color:#000;font-weight:700;font-size:17px}</style>
        </head><body><h1>$title</h1>$body</body></html>"""

    private fun respond(c: Socket, code: Int, html: String) {
        val bytes = html.toByteArray()
        val status = if (code == 200) "200 OK" else "404 Not Found"
        c.getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
            write(bytes); flush()
        }
    }

    companion object {
        /** The TV's address on the home network (Wi-Fi or Ethernet). */
        fun lanIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}
