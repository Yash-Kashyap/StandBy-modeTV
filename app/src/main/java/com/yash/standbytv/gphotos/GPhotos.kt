package com.yash.standbytv.gphotos

import android.content.Context
import com.yash.standbytv.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID

/**
 * Google Photos via the official Picker API (the only way apps can read Google Photos since March 2025).
 * You pick photos on your phone (individual photos, or search an album and select all);
 * the TV downloads them once into a local "album" so the slideshow works offline and never expires.
 */
object GPhotos {
    private const val API = "https://photospicker.googleapis.com/v1"
    const val MAX_PER_IMPORT = 600

    class Session(val id: String, val pickerUri: String, val pollMs: Long, val timeoutAt: Long)
    data class Album(val id: String, val name: String, val enabled: Boolean, val count: Int)

    // ---------------------------------------------------------------- picker sessions (background thread)

    fun createSession(ctx: Context): Session {
        val tok = GoogleAuth.accessToken(ctx)
        val r = Http.request("$API/sessions?requestId=${UUID.randomUUID()}", "POST", "{}", bearer = tok)
        val j = JSONObject(r.body)
        if (!r.ok) error(j.optJSONObject("error")?.optString("message") ?: "HTTP ${r.code}")
        return parseSession(j)
    }

    private fun dur(s: String?, def: Long): Long =
        s?.removeSuffix("s")?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: def

    private fun parseSession(j: JSONObject): Session {
        val pc = j.optJSONObject("pollingConfig")
        return Session(
            j.getString("id"), j.getString("pickerUri"),
            dur(pc?.optString("pollInterval"), 5000L).coerceIn(2000L, 30000L),
            System.currentTimeMillis() + dur(pc?.optString("timeoutIn"), 30 * 60_000L)
        )
    }

    /** True once the user tapped Done in Google Photos. */
    fun isPicked(ctx: Context, s: Session): Boolean {
        val r = Http.request("$API/sessions/${s.id}", bearer = GoogleAuth.accessToken(ctx))
        if (!r.ok) error("HTTP ${r.code}")
        return JSONObject(r.body).optBoolean("mediaItemsSet", false)
    }

    fun deleteSession(ctx: Context, s: Session) {
        runCatching { Http.request("$API/sessions/${s.id}", "DELETE", bearer = GoogleAuth.accessToken(ctx)) }
    }

    /** Downloads every picked photo into [albumId]. Returns number of new photos. */
    fun importSession(ctx: Context, s: Session, albumId: String, progress: (done: Int, total: Int) -> Unit): Int {
        val urls = ArrayList<Pair<String, String>>()   // id -> baseUrl
        var page: String? = null
        do {
            var url = "$API/mediaItems?sessionId=${s.id}&pageSize=100"
            if (page != null) url += "&pageToken=" + URLEncoder.encode(page, "UTF-8")
            val r = Http.request(url, bearer = GoogleAuth.accessToken(ctx))
            if (!r.ok) error("HTTP ${r.code}")
            val j = JSONObject(r.body)
            val arr = j.optJSONArray("mediaItems") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val mf = it.optJSONObject("mediaFile") ?: continue
                val mime = mf.optString("mimeType")
                if (it.optString("type") == "VIDEO" || mime.startsWith("video")) continue
                urls += it.getString("id") to mf.getString("baseUrl")
            }
            page = j.optString("nextPageToken").ifEmpty { null }
        } while (page != null && urls.size < MAX_PER_IMPORT)

        val dir = albumDir(ctx, albumId).apply { mkdirs() }
        var added = 0
        val list = urls.take(MAX_PER_IMPORT)
        for ((i, pair) in list.withIndex()) {
            val (id, base) = pair
            val f = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_").takeLast(80) + ".jpg")
            if (!f.exists() && Http.download("$base=w1920-h1080", f, GoogleAuth.accessToken(ctx))) added++
            progress(i + 1, list.size)
        }
        deleteSession(ctx, s)
        updateCount(ctx, albumId)
        return added
    }

    // ---------------------------------------------------------------- local albums

    private fun root(ctx: Context) = File(ctx.filesDir, "gphotos")
    fun albumDir(ctx: Context, id: String) = File(root(ctx), id)

    fun albums(ctx: Context): List<Album> {
        val arr = runCatching { JSONArray(Prefs(ctx).sp.getString("gp_albums", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Album(o.getString("id"), o.getString("name"), o.optBoolean("enabled", true), o.optInt("count", 0))
        }
    }

    private fun save(ctx: Context, list: List<Album>) {
        val arr = JSONArray()
        for (a in list) arr.put(JSONObject().put("id", a.id).put("name", a.name).put("enabled", a.enabled).put("count", a.count))
        Prefs(ctx).sp.edit().putString("gp_albums", arr.toString()).apply()
    }

    fun createAlbum(ctx: Context, name: String): Album {
        val a = Album("a" + System.currentTimeMillis().toString(36), name, true, 0)
        save(ctx, albums(ctx) + a)
        return a
    }

    fun setEnabled(ctx: Context, id: String, on: Boolean) = save(ctx, albums(ctx).map { if (it.id == id) it.copy(enabled = on) else it })

    fun rename(ctx: Context, id: String, name: String) = save(ctx, albums(ctx).map { if (it.id == id) it.copy(name = name) else it })

    fun delete(ctx: Context, id: String) {
        albumDir(ctx, id).deleteRecursively()
        save(ctx, albums(ctx).filter { it.id != id })
    }

    fun updateCount(ctx: Context, id: String) {
        val n = albumDir(ctx, id).listFiles { f -> f.name.endsWith(".jpg") }?.size ?: 0
        save(ctx, albums(ctx).map { if (it.id == id) it.copy(count = n) else it })
    }

    /** All photo files from albums switched on for the slideshow. */
    fun enabledFiles(ctx: Context): List<File> =
        albums(ctx).filter { it.enabled }.flatMap { a ->
            albumDir(ctx, a.id).listFiles { f -> f.name.endsWith(".jpg") }?.toList() ?: emptyList()
        }
}
