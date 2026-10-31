package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.yash.standbytv.T
import java.util.Calendar
import kotlin.math.sin
import kotlin.random.Random

/** Chunky 8-bit clock over a pixel landscape that follows the time of day. */
class PixelArtClockFace(ctx: Context) : Face(ctx) {
    override val id = "pixel"
    override val title = "Pixel Art"
    override val styleNames = listOf("Landscape", "Game Boy", "Arcade")
    override val tickMs = 100L

    // 5x7 digit font
    private val font = arrayOf(
        arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        arrayOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    )
    private val p = Paint()
    private val txt = Paint().apply { typeface = MONO; textAlign = Paint.Align.CENTER; isAntiAlias = false }
    private val stars = Array(60) { intArrayOf(Random.nextInt(1000), Random.nextInt(1000)) }
    private val gb = intArrayOf(0xFF0F380F.toInt(), 0xFF306230.toInt(), 0xFF8BAC0F.toInt(), 0xFF9BBC0F.toInt())
    private val rainbow = intArrayOf(0xFFFF004D.toInt(), 0xFFFFA300.toInt(), 0xFFFFEC27.toInt(), 0xFF00E436.toInt(), 0xFF29ADFF.toInt(), 0xFFFF77A8.toInt())

    private fun lerp(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    private fun block(c: Canvas, x: Float, y: Float, s: Float, color: Int) { p.color = color; c.drawRect(x, y, x + s, y + s, p) }

    override fun draw(c: Canvas, w: Float, h: Float) {
        val cal = T.now()
        val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
        val P = h / 90f                     // scene pixel
        val now = System.currentTimeMillis()

        // --- scene
        when (style) {
            0 -> landscape(c, w, h, P, hour, now)
            1 -> { c.drawColor(gb[3]); hills(c, w, h, P, gb[2], gb[1]) }
            else -> {
                c.drawColor(Color.BLACK)
                p.color = 0x22FFFFFF
                var yy = 0f; while (yy < h) { c.drawRect(0f, yy, w, yy + P * 0.35f, p); yy += P * 2 }
            }
        }

        // --- digits
        val B = h / 24f
        val text = T.hm(cal, prefs.use24h)
        var units = 0
        for (ch in text) units += if (ch == ':') 2 else 6
        units -= 1
        var x = (w - units * B) / 2f
        val y = h * 0.26f
        val night = hour < 6 || hour >= 19
        val ink = when (style) { 1 -> gb[0]; 0 -> if (night) 0xFFFFF1E8.toInt() else 0xFF1D2B53.toInt(); else -> Color.WHITE }
        val shadow = when (style) { 1 -> gb[1]; 0 -> if (night) 0xFF1D2B53.toInt() else 0x66FFFFFF; else -> 0xFF5F574F.toInt() }
        var di = 0
        for (ch in text) {
            if (ch == ':') {
                if ((now / 1000) % 2 == 0L) {
                    for (ry in intArrayOf(2, 4)) { block(c, x + B * 0.15f, y + ry * B + B * 0.15f, B, shadow); block(c, x, y + ry * B, B, ink) }
                }
                x += 2 * B; continue
            }
            val g = font[ch - '0']
            val color = if (style == 2) rainbow[di % rainbow.size] else ink
            for (ry in 0 until 7) for (rx in 0 until 5) if (g[ry][rx] == '1') {
                block(c, x + rx * B + B * 0.15f, y + ry * B + B * 0.15f, B, shadow)
                block(c, x + rx * B, y + ry * B, B, color)
            }
            x += 6 * B; di++
        }

        txt.textSize = B * 1.1f
        txt.color = ink
        val sub = (if (prefs.use24h) "" else T.ampm(cal) + " ") + T.fmt(cal, "EEE dd MMM").uppercase()
        c.drawText(sub, w / 2, y + 9.6f * B, txt)
    }

    private fun hills(c: Canvas, w: Float, h: Float, P: Float, far: Int, near: Int) {
        val cols = (w / P).toInt() + 1
        for (i in 0 until cols) {
            val farH = (14 + 6 * sin(i * 0.05) + 3 * sin(i * 0.13 + 1)).toInt()
            p.color = far; c.drawRect(i * P, h - farH * P, i * P + P, h, p)
            val nearH = (7 + 4 * sin(i * 0.08 + 2) + 2 * sin(i * 0.21)).toInt()
            p.color = near; c.drawRect(i * P, h - nearH * P, i * P + P, h, p)
        }
    }

    private fun landscape(c: Canvas, w: Float, h: Float, P: Float, hour: Float, now: Long) {
        val night = hour < 6 || hour >= 19
        val dusk = (hour in 6f..8f) || (hour in 17f..19f)
        val top = when { night -> 0xFF0B0E2A.toInt(); dusk -> 0xFF3B2A6B.toInt(); else -> 0xFF29ADFF.toInt() }
        val bottom = when { night -> 0xFF26215C.toInt(); dusk -> 0xFFFF8A4C.toInt(); else -> 0xFFB8E3FF.toInt() }
        // stepped (banded) pixel sky
        val bands = 10
        for (i in 0 until bands) {
            p.color = lerp(top, bottom, i / (bands - 1f))
            c.drawRect(0f, h * i / bands, w, h * (i + 1) / bands, p)
        }
        if (night) for (s in stars) block(c, (s[0] / 1000f * w / P).toInt() * P, (s[1] / 1000f * h * 0.6f / P).toInt() * P, P * 0.8f, 0xFFFFF1E8.toInt())

        // sun / moon travelling across the sky
        val t = if (night) ((hour - 19 + 24) % 24) / 11f else (hour - 6) / 13f
        val cx = w * (0.08f + 0.84f * t.coerceIn(0f, 1f))
        val cy = h * (0.62f - 0.5f * sin(Math.PI * t.coerceIn(0f, 1f)).toFloat())
        val r = 5
        val body = if (night) 0xFFFFF1E8.toInt() else 0xFFFFEC27.toInt()
        for (yy in -r..r) for (xx in -r..r) if (xx * xx + yy * yy <= r * r)
            block(c, ((cx / P).toInt() + xx) * P, ((cy / P).toInt() + yy) * P, P, body)

        // clouds drift
        val cloud = if (night) 0xFF3A3F6B.toInt() else Color.WHITE
        for (k in 0 until 3) {
            val speed = 0.004f + k * 0.002f
            val cxk = (((now / 1000f) * speed + k * 0.37f) % 1.2f - 0.1f) * w
            val cyk = h * (0.12f + k * 0.1f)
            val bx = (cxk / P).toInt(); val by = (cyk / P).toInt()
            for ((dx, dy, ww) in listOf(Triple(0, 1, 12), Triple(2, 0, 6), Triple(3, -1, 3)))
                for (i in 0 until ww) block(c, (bx + dx + i) * P, (by + dy) * P, P, cloud)
        }
        val far = if (night) 0xFF1D2B53.toInt() else 0xFF008751.toInt()
        val near = if (night) 0xFF0F1530.toInt() else 0xFF00B543.toInt()
        hills(c, w, h, P, far, near)
    }
}
