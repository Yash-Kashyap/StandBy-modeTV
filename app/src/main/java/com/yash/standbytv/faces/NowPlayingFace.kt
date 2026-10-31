package com.yash.standbytv.faces

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.text.TextUtils
import android.view.KeyEvent
import com.yash.standbytv.MediaListener
import com.yash.standbytv.T

/**
 * Shows whatever is playing on the TV — Spotify, YouTube Music, YouTube, Prime Video… — via Android's
 * media sessions, with album art and remote-controllable ⏮ ⏯ ⏭.
 * OK shows the controls; ◀ ▶ pick a button while they're visible; BACK hides them.
 */
class NowPlayingFace(ctx: Context) : Face(ctx) {
    override val id = "music"
    override val title = "Now Playing"
    override val styleNames = listOf("Blurred Art", "Card", "Minimal")
    override val tickMs = 250L

    /** Fake track for the screenshot test. */
    class Preview(val title: String, val artist: String, val app: String, val art: Bitmap?, val durMs: Long, val posMs: Long)
    var preview: Preview? = null

    private val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val comp = ComponentName(context, MediaListener::class.java)
    private var controller: MediaController? = null
    private var needAccess = false
    private var art: Bitmap? = null
    private var blur: Bitmap? = null
    private var appName = ""
    private var controlsVisible = false
    private var controlsUntil = 0L
    private var focus = 1   // 0 prev, 1 play/pause, 2 next

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }
    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { loadArt(); invalidate() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { invalidate() }
        override fun onSessionDestroyed() { runCatching { pick(msm.getActiveSessions(comp)) } }
    }

    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpP = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF(); private val src = Rect()

    override fun onStart() {
        if (preview != null) return
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp)
            pick(msm.getActiveSessions(comp))
            needAccess = false
        } catch (_: SecurityException) {
            needAccess = true
        }
    }

    override fun onStop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(cb)
        controller = null
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(cb)
        controller = best
        best?.registerCallback(cb)
        appName = best?.let {
            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it.packageName, 0)).toString() }
                .getOrDefault(it.packageName)
        } ?: ""
        loadArt()
        invalidate()
    }

    private fun loadArt() {
        val md = controller?.metadata
        val a = preview?.art ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (a === art) return
        art = a
        // cheap blur that works on every Android version: shrink hard, then scale up with filtering
        blur = a?.let { Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(it, 12, 12, true), 48, 48, true) }
    }

    // ------------------------------------------------------------------ remote keys

    private fun transport(action: Int) {
        val tc = controller?.transportControls ?: return
        when (action) {
            0 -> tc.skipToPrevious()
            1 -> if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) tc.pause() else tc.play()
            2 -> tc.skipToNext()
        }
    }

    override fun onKey(e: KeyEvent): Boolean {
        if (controller == null) return false
        when (e.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { transport(1); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { transport(2); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { transport(0); return true }
        }
        val now = System.currentTimeMillis()
        if (!controlsVisible || now > controlsUntil) {
            controlsVisible = false
            if (e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER) {
                controlsVisible = true; focus = 1; controlsUntil = now + 8000; invalidate(); return true
            }
            return false
        }
        controlsUntil = now + 8000
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> focus = (focus + 2) % 3
            KeyEvent.KEYCODE_DPAD_RIGHT -> focus = (focus + 1) % 3
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> transport(focus)
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> controlsVisible = false
            else -> return false
        }
        invalidate()
        return true
    }

    // ------------------------------------------------------------------ drawing

    private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "${s / 60}:${String.format("%02d", s % 60)}" }

    override fun draw(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.BLACK)
        val pv = preview
        if (pv != null && art == null) loadArt()
        val md = controller?.metadata
        val st = controller?.playbackState
        val title = pv?.title ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = pv?.artist ?: md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val app = pv?.app ?: appName
        val dur = pv?.durMs ?: md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val playing = pv != null || st?.state == PlaybackState.STATE_PLAYING
        var pos = pv?.posMs ?: st?.position ?: 0L
        if (pv == null && st != null && playing) pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        if (dur > 0) pos = pos.coerceIn(0, dur)

        if (title == null) { empty(c, w, h); return }

        val b = blur
        if (style == 0 && b != null) {
            src.set(0, 0, b.width, b.height)
            val s = maxOf(w, h)
            r.set((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2)
            c.drawBitmap(b, src, r, bmpP)
            p.shader = LinearGradient(0f, 0f, 0f, h, 0x66000000, 0xCC000000.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
        } else if (style == 1) c.drawColor(0xFF0E0E10.toInt())

        val cal = T.now()
        txt.textAlign = Paint.Align.LEFT; txt.typeface = MEDIUM; txt.textSize = h * 0.04f; txt.color = 0xCCFFFFFF.toInt()
        c.drawText(T.hm(cal, prefs.use24h) + (if (prefs.use24h) "" else " " + T.ampm(cal)), w * 0.06f, h * 0.1f, txt)

        if (style == 2) {
            txt.textAlign = Paint.Align.CENTER
            txt.typeface = BOLD; txt.color = Color.WHITE; txt.textSize = h * 0.08f
            c.drawText(ellipsize(title, w * 0.8f), w / 2, h * 0.45f, txt)
            txt.typeface = MEDIUM; txt.color = 0xFFAEAEB2.toInt(); txt.textSize = h * 0.045f
            c.drawText(ellipsize(artist, w * 0.8f), w / 2, h * 0.53f, txt)
            progress(c, w * 0.2f, w * 0.8f, h * 0.64f, h, pos, dur)
            controls(c, w / 2, h * 0.8f, h, playing)
            return
        }

        // album art square
        val size = h * 0.56f
        val ax = w * 0.08f; val ay = (h - size) / 2
        r.set(ax, ay, ax + size, ay + size)
        val a = art
        if (a != null) {
            c.save()
            path.reset(); path.addRoundRect(r, h * 0.025f, h * 0.025f, Path.Direction.CW); c.clipPath(path)
            src.set(0, 0, a.width, a.height); c.drawBitmap(a, src, r, bmpP)
            c.restore()
        } else {
            p.color = 0xFF2C2C2E.toInt(); c.drawRoundRect(r, h * 0.025f, h * 0.025f, p)
            txt.textAlign = Paint.Align.CENTER; txt.textSize = size * 0.4f; txt.color = 0xFF636366.toInt()
            c.drawText("♪", r.centerX(), Draw.centerY(txt, r.centerY()), txt)
        }

        val tx = ax + size + w * 0.05f
        val maxW = w * 0.92f - tx
        txt.textAlign = Paint.Align.LEFT
        txt.typeface = BOLD; txt.color = Draw.ORANGE; txt.textSize = h * 0.032f
        c.drawText(app.uppercase(), tx, ay + h * 0.05f, txt)
        txt.color = Color.WHITE; txt.textSize = h * 0.075f
        c.drawText(ellipsize(title, maxW), tx, ay + h * 0.16f, txt)
        txt.typeface = MEDIUM; txt.color = 0xFFC7C7CC.toInt(); txt.textSize = h * 0.045f
        c.drawText(ellipsize(artist, maxW), tx, ay + h * 0.235f, txt)
        progress(c, tx, tx + maxW, ay + h * 0.36f, h, pos, dur)
        controls(c, tx + maxW / 2, ay + h * 0.48f, h, playing)
    }

    private fun ellipsize(s: String, maxW: Float) = TextUtils.ellipsize(s, android.text.TextPaint(txt), maxW, TextUtils.TruncateAt.END).toString()

    private fun progress(c: Canvas, x0: Float, x1: Float, y: Float, h: Float, pos: Long, dur: Long) {
        val th = h * 0.008f
        p.color = 0x44FFFFFF; c.drawRoundRect(x0, y - th / 2, x1, y + th / 2, th, th, p)
        if (dur > 0) {
            val fx = x0 + (x1 - x0) * (pos.toFloat() / dur)
            p.color = Color.WHITE; c.drawRoundRect(x0, y - th / 2, fx, y + th / 2, th, th, p)
        }
        txt.typeface = MEDIUM; txt.textSize = h * 0.03f; txt.color = 0xFFAEAEB2.toInt()
        txt.textAlign = Paint.Align.LEFT; c.drawText(fmt(pos), x0, y + h * 0.05f, txt)
        txt.textAlign = Paint.Align.RIGHT; c.drawText(if (dur > 0) fmt(dur) else "LIVE", x1, y + h * 0.05f, txt)
    }

    private fun controls(c: Canvas, cx: Float, cy: Float, h: Float, playing: Boolean) {
        val show = controlsVisible && System.currentTimeMillis() < controlsUntil
        if (!show) {
            txt.textAlign = Paint.Align.CENTER; txt.typeface = MEDIUM; txt.textSize = h * 0.028f; txt.color = 0x99FFFFFF.toInt()
            c.drawText("Press OK for controls", cx, cy, txt)
            return
        }
        val gap = h * 0.17f
        for (i in 0 until 3) {
            val x = cx + (i - 1) * gap
            val rr = h * (if (i == 1) 0.065f else 0.05f)
            if (i == focus) { p.color = Color.WHITE; c.drawCircle(x, cy, rr * 1.35f, p) }
            p.color = if (i == focus) Color.BLACK else Color.WHITE
            when (i) {
                0 -> { tri(c, x + rr * 0.1f, cy, rr * 0.5f, left = true); c.drawRect(x - rr * 0.55f, cy - rr * 0.45f, x - rr * 0.42f, cy + rr * 0.45f, p) }
                2 -> { tri(c, x - rr * 0.1f, cy, rr * 0.5f, left = false); c.drawRect(x + rr * 0.42f, cy - rr * 0.45f, x + rr * 0.55f, cy + rr * 0.45f, p) }
                else -> if (playing) {
                    c.drawRect(x - rr * 0.35f, cy - rr * 0.45f, x - rr * 0.1f, cy + rr * 0.45f, p)
                    c.drawRect(x + rr * 0.1f, cy - rr * 0.45f, x + rr * 0.35f, cy + rr * 0.45f, p)
                } else tri(c, x + rr * 0.1f, cy, rr * 0.55f, left = false)
            }
        }
    }

    private fun tri(c: Canvas, x: Float, y: Float, s: Float, left: Boolean) {
        path.reset()
        if (left) { path.moveTo(x - s, y); path.lineTo(x + s * 0.8f, y - s); path.lineTo(x + s * 0.8f, y + s) }
        else { path.moveTo(x + s, y); path.lineTo(x - s * 0.8f, y - s); path.lineTo(x - s * 0.8f, y + s) }
        path.close(); c.drawPath(path, p)
    }

    private fun empty(c: Canvas, w: Float, h: Float) {
        txt.textAlign = Paint.Align.CENTER
        txt.typeface = BOLD; txt.color = Color.WHITE; txt.textSize = h * 0.06f
        val cal = T.now()
        c.drawText(T.hm(cal, prefs.use24h), w / 2, h * 0.3f, txt)
        txt.typeface = MEDIUM; txt.textSize = h * 0.04f; txt.color = 0xFFAEAEB2.toInt()
        val lines = if (needAccess) listOf(
            "Allow StandBy TV to see what's playing:",
            "Settings → Music controls access   (or from a PC run:)",
            "adb shell cmd notification allow_listener ${context.packageName}/${MediaListener::class.java.name}",
        ) else listOf("Nothing playing", "Start music in Spotify, YouTube Music or YouTube on this TV")
        var y = h * 0.5f
        for (l in lines) { txt.textSize = if (l.startsWith("adb")) h * 0.028f else h * 0.04f; c.drawText(l, w / 2, y, txt); y += h * 0.075f }
    }
}
