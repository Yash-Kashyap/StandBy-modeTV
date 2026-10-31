package com.yash.standbytv.gphotos

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Tiny blocking HTTP helper (call from background threads only). */
object Http {
    class Resp(val code: Int, val body: String) { val ok get() = code in 200..299 }

    fun form(vararg kv: Pair<String, String>) =
        kv.joinToString("&") { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8") }

    fun request(
        url: String, method: String = "GET", body: String? = null,
        contentType: String? = null, bearer: String? = null,
    ): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "StandByTV/1.1 (Android TV)")
        bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Resp(code, text)
        } finally {
            c.disconnect()
        }
    }

    /** Streams [url] to [dest]. Returns false on failure. */
    fun download(url: String, dest: File, bearer: String?): Boolean {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 60_000
        bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        return try {
            if (c.responseCode !in 200..299) return false
            val tmp = File(dest.parentFile, dest.name + ".part")
            c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            tmp.renameTo(dest)
        } catch (_: Exception) {
            false
        } finally {
            c.disconnect()
        }
    }
}
