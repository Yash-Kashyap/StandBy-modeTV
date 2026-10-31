package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.yash.standbytv.Palettes
import com.yash.standbytv.T
import kotlin.random.Random

/** Digital-rain backdrop rendered at half resolution, with the time glowing on top. */
class MatrixFace(ctx: Context) : Face(ctx) {
    override val id = "matrix"
    override val title = "Matrix"
    override val styleNames = listOf("Green", "Cyan", "Red", "Purple", "Gold")
    private val colors = intArrayOf(0xFF00FF66.toInt(), 0xFF00E5FF.toInt(), 0xFFFF2D2D.toInt(), 0xFFB45CFF.toInt(), 0xFFFFC94A.toInt())
    override val tickMs = 66L

    private var buf: Bitmap? = null
    private var bc: Canvas? = null
    private var drops = FloatArray(0)
    private var speeds = FloatArray(0)
    private var cell = 0f
    private val glyphs = "アイウエオカキクケコサシスセソタチツテトナニヌネノ0123456789ABCDEFZ:+-*<>".toCharArray()
    private val fade = Paint().apply { color = Color.argb(34, 0, 0, 0) }
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MONO; textAlign = Paint.Align.CENTER }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MONO; textAlign = Paint.Align.CENTER }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 0, 0, 0) }
    private val src = Rect(); private val dst = RectF()

    override fun onStop() { buf?.recycle(); buf = null; bc = null }
    override fun onStyleChanged() { bc?.drawColor(Color.BLACK) }

    private fun ensure(w: Int, h: Int) {
        val bw = (w / 2).coerceAtLeast(1); val bh = (h / 2).coerceAtLeast(1)
        if (buf?.width == bw && buf?.height == bh) return
        buf?.recycle()
        buf = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bc = Canvas(buf!!).apply { drawColor(Color.BLACK) }
        cell = bh / 34f
        val cols = (bw / cell).toInt() + 1
        drops = FloatArray(cols) { Random.nextFloat() * -bh }
        speeds = FloatArray(cols) { 0.6f + Random.nextFloat() * 0.9f }
        gp.textSize = cell * 0.95f
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        if (w <= 0 || h <= 0) return
        ensure(w.toInt(), h.toInt())
        val b = buf ?: return; val k = bc ?: return
        val col = colors[style]
        k.drawRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), fade)
        for (i in drops.indices) {
            val x = i * cell + cell / 2
            val y = drops[i]
            gp.color = Color.WHITE
            k.drawText(glyphs[Random.nextInt(glyphs.size)].toString(), x, y, gp)
            gp.color = col
            k.drawText(glyphs[Random.nextInt(glyphs.size)].toString(), x, y - cell, gp)
            drops[i] += cell * speeds[i]
            if (drops[i] > b.height + cell * 4 && Random.nextFloat() > 0.96f) drops[i] = -Random.nextFloat() * cell * 10
        }
        src.set(0, 0, b.width, b.height); dst.set(0f, 0f, w, h)
        c.drawBitmap(b, src, dst, null)

        val cal = T.now()
        val time = T.hm(cal, prefs.use24h)
        Draw.fit(big, "00:00", w * 0.6f, h * 0.36f)
        val bw = big.measureText("00:00") * 1.12f
        val bh = big.textSize * 0.95f
        c.drawRoundRect(w / 2 - bw / 2, h / 2 - bh / 2, w / 2 + bw / 2, h / 2 + bh / 2 + h * 0.09f, h * 0.03f, h * 0.03f, shade)
        big.color = Color.WHITE
        big.setShadowLayer(h * 0.03f, 0f, 0f, col)
        c.drawText(time, w / 2, Draw.centerY(big, h / 2), big)
        big.clearShadowLayer()

        gp.textSize = h * 0.035f
        gp.color = Palettes.withAlpha(col, 230)
        val s = gp.textSize
        c.drawText(T.dateLong(cal).uppercase(), w / 2, h / 2 + bh / 2 + s * 1.4f, gp)
        gp.textSize = cell * 0.95f
    }
}
