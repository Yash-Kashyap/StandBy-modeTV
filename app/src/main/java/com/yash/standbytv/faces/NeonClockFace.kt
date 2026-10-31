package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.yash.standbytv.Palettes
import com.yash.standbytv.T
import kotlin.random.Random

/** Neon-sign clock on a dark brick wall, with tube glow and the odd flicker. */
class NeonClockFace(ctx: Context) : Face(ctx) {
    override val id = "neon"
    override val title = "Neon"
    override val styleNames = listOf("Hot Pink", "Ice Cyan", "Lime", "Sunset Orange", "Pink & Blue")
    private val colors = arrayOf(
        intArrayOf(0xFFFF2BD6.toInt(), 0xFFFF2BD6.toInt()),
        intArrayOf(0xFF22E4FF.toInt(), 0xFF22E4FF.toInt()),
        intArrayOf(0xFF9DFF2B.toInt(), 0xFF9DFF2B.toInt()),
        intArrayOf(0xFFFF7A1A.toInt(), 0xFFFFC21A.toInt()),
        intArrayOf(0xFFFF2BD6.toInt(), 0xFF22E4FF.toInt()),
    )
    override val tickMs = 50L

    private val wall = Paint()
    private val brick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF1A1020.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val tube = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private var flickerPart = -1
    private var flickerUntil = 0L

    private fun neon(c: Canvas, text: String, x: Float, y: Float, color: Int, size: Float, on: Float) {
        for (pp in arrayOf(glow, tube, core)) { pp.textSize = size; pp.typeface = THIN }
        // Glow via text shadows: unlike blur filters, these are hardware-accelerated on Android 8/9.
        glow.strokeWidth = size * 0.05f
        glow.color = Palettes.withAlpha(color, (110 * on).toInt())
        glow.setShadowLayer(size * 0.09f, 0f, 0f, Palettes.withAlpha(color, (255 * on).toInt()))
        c.drawText(text, x, y, glow)
        tube.strokeWidth = size * 0.035f
        tube.color = Palettes.withAlpha(color, (255 * on).toInt())
        tube.setShadowLayer(size * 0.02f, 0f, 0f, Palettes.withAlpha(color, (255 * on).toInt()))
        c.drawText(text, x, y, tube)
        core.strokeWidth = size * 0.012f
        core.color = Color.argb((230 * on).toInt(), 255, 255, 255)
        c.drawText(text, x, y, core)
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        wall.color = 0xFF0A060E.toInt()
        c.drawRect(0f, 0f, w, h, wall)
        // brick pattern
        brick.strokeWidth = h * 0.002f
        val bh = h / 18f; val bw = bh * 2.6f
        var row = 0; var y = 0f
        while (y < h) {
            val off = if (row % 2 == 0) 0f else bw / 2
            var x = -off
            while (x < w) { c.drawRect(x, y, x + bw, y + bh, brick); x += bw }
            y += bh; row++
        }

        val now = System.currentTimeMillis()
        if (now > flickerUntil && Random.nextFloat() < 0.004f) { flickerPart = Random.nextInt(2); flickerUntil = now + 450 }
        fun level(part: Int): Float =
            if (part == flickerPart && now < flickerUntil) (if ((now / 60) % 2 == 0L) 0.25f else 0.9f) else 1f

        val cal = T.now()
        val col = colors[style]
        val size = h * 0.42f
        val hh = T.hour(cal, prefs.use24h); val mm = T.min(cal)
        tube.textSize = size; tube.typeface = THIN
        val colonW = tube.measureText(":")
        val hw = tube.measureText(hh); val mw = tube.measureText(mm)
        val total = hw + colonW + mw
        val x0 = (w - total) / 2
        val base = h * 0.56f
        neon(c, hh, x0 + hw / 2, base, col[0], size, level(0))
        neon(c, ":", x0 + hw + colonW / 2, base, col[0], size, if ((now / 1000) % 2 == 0L) 1f else 0.35f)
        neon(c, mm, x0 + hw + colonW + mw / 2, base, col[1], size, level(1))

        val sub = T.fmt(cal, "EEEE").lowercase() + if (prefs.use24h) "" else "  " + T.ampm(cal).lowercase()
        neon(c, sub, w / 2, h * 0.82f, col[1], h * 0.09f, 1f)
    }
}
