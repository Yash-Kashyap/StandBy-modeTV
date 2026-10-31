package com.yash.standbytv.faces

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.yash.standbytv.T
import com.yash.standbytv.gphotos.GPhotos
import kotlin.concurrent.thread

/** Ken-Burns slideshow of photos on the TV (internal storage or USB), clock in the corner. */
class PhotoFace(ctx: Context) : Face(ctx) {
    override val id = "photos"
    override val title = "Photos"
    override val styleNames = listOf("Clock bottom-left", "Large clock", "Photos only")
    override val tickMs = 33L

    var onNeedPermission: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var uris: List<Uri> = emptyList()
    private var idx = 0
    private var cur: Bitmap? = null
    private var prev: Bitmap? = null
    private var shownAt = 0L
    private var loading = false
    private var status = "Looking for photos…"
    private val SHOW_MS = 15_000L
    private val FADE_MS = 1_500L

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val m = Matrix()

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission() = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private val useDevice get() = prefs.photoSource != 2
    private val useGoogle get() = prefs.photoSource != 1

    override fun onStart() {
        if (useDevice && !hasPermission()) {
            onNeedPermission?.invoke()
            if (!useGoogle || GPhotos.enabledFiles(context).isEmpty()) {
                status = "Allow photo access to show your pictures"
                return
            }
        }
        if (uris.isEmpty()) scan() else loadNext()
    }

    override fun onStop() {
        cur?.recycle(); prev?.recycle(); cur = null; prev = null
    }

    private fun scan() {
        thread(name = "photo-scan") {
            val list = mutableListOf<Uri>()
            if (useGoogle) GPhotos.enabledFiles(context).forEach { list += Uri.fromFile(it) }
            if (useDevice && hasPermission()) runCatching {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID), null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                )?.use { cur ->
                    while (cur.moveToNext() && list.size < 2000) {
                        list += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cur.getLong(0))
                    }
                }
            }
            list.shuffle()
            main.post {
                uris = list
                status = if (list.isEmpty()) "No photos yet.\nAdd a Google Photos album in Settings, copy pictures to the TV,\nor plug in a USB drive." else ""
                idx = 0
                loadNext()
            }
        }
    }

    private fun loadNext() {
        if (uris.isEmpty() || loading || !running) return
        loading = true
        val uri = uris[idx % uris.size]; idx++
        val tw = width.coerceAtLeast(1280); val th = height.coerceAtLeast(720)
        thread(name = "photo-load") {
            val bmp = runCatching { decode(uri, tw, th) }.getOrNull()
            main.post {
                loading = false
                if (!running) { bmp?.recycle(); return@post }
                if (bmp != null) {
                    prev?.recycle()
                    prev = cur
                    cur = bmp
                    shownAt = System.currentTimeMillis()
                } else if (uris.size > 1) loadNext()
            }
        }
    }

    private fun decode(uri: Uri, tw: Int, th: Int): Bitmap? {
        val cr = context.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= tw && o.outHeight / (sample * 2) >= th) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rot = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rot == 0f) return raw
        val mm = Matrix().apply { postRotate(rot) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, mm, true).also { if (it != raw) raw.recycle() }
    }

    private fun drawPhoto(c: Canvas, b: Bitmap, w: Float, h: Float, t: Float, alpha: Int, seed: Int) {
        // cover-fit, then slow zoom + pan (Ken Burns)
        val scale = maxOf(w / b.width, h / b.height) * (1.0f + 0.10f * t)
        val dw = b.width * scale; val dh = b.height * scale
        val dir = if (seed % 2 == 0) 1 else -1
        val dx = (w - dw) / 2f + dir * (dw - w) * 0.25f * (t - 0.5f)
        val dy = (h - dh) / 2f
        m.reset(); m.postScale(scale, scale); m.postTranslate(dx, dy)
        bmpPaint.alpha = alpha
        c.drawBitmap(b, m, bmpPaint)
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.BLACK)
        val now = System.currentTimeMillis()
        val b = cur
        if (b != null) {
            val age = now - shownAt
            val t = (age.toFloat() / (SHOW_MS + FADE_MS)).coerceIn(0f, 1f)
            val p = prev
            if (p != null && age < FADE_MS) drawPhoto(c, p, w, h, 1f, 255, idx + 1)
            val a = if (p != null && age < FADE_MS) (255 * age / FADE_MS).toInt() else 255
            drawPhoto(c, b, w, h, t, a, idx)
            if (age > SHOW_MS) loadNext()
        } else {
            txt.textAlign = Paint.Align.CENTER
            txt.typeface = MEDIUM
            txt.textSize = h * 0.045f
            var y = h * 0.45f
            for (line in status.split("\n")) { c.drawText(line, w / 2, y, txt); y += txt.textSize * 1.4f }
        }
        if (style == 2) return
        val cal = T.now()
        txt.setShadowLayer(h * 0.02f, 0f, 0f, Color.argb(200, 0, 0, 0))
        txt.textAlign = Paint.Align.LEFT
        if (style == 0) {
            txt.typeface = LIGHT; txt.textSize = h * 0.14f
            c.drawText(T.hm(cal, prefs.use24h), w * 0.05f, h * 0.86f, txt)
            txt.typeface = MEDIUM; txt.textSize = h * 0.04f
            c.drawText(T.dateLong(cal).uppercase(), w * 0.055f, h * 0.93f, txt)
        } else {
            txt.textAlign = Paint.Align.CENTER
            txt.typeface = BOLD
            Draw.fit(txt, "00:00", w * 0.6f, h * 0.5f)
            c.drawText(T.hm(cal, prefs.use24h), w / 2, Draw.centerY(txt, h * 0.48f), txt)
            txt.typeface = MEDIUM; txt.textSize = h * 0.045f
            c.drawText(T.dateLong(cal).uppercase(), w / 2, h * 0.80f, txt)
        }
        txt.clearShadowLayer()
    }
}
