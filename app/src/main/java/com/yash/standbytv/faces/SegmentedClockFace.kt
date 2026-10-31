package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.yash.standbytv.Palettes
import com.yash.standbytv.T
import java.util.Calendar

/** Seven-segment LED / VFD clock with ghosted unlit segments. */
class SegmentedClockFace(ctx: Context) : Face(ctx) {
    override val id = "led"
    override val title = "LED Clock"
    override val styleNames = listOf("Red LED", "Green VFD", "Ice Blue", "Amber", "White")
    private val colors = intArrayOf(0xFFFF2A2A.toInt(), 0xFF39FFB0.toInt(), 0xFF5CC8FF.toInt(), 0xFFFFA51F.toInt(), 0xFFF2F2F2.toInt())

    // segments a b c d e f g
    private val map = intArrayOf(0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011, 0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011)

    private val on = Paint(Paint.ANTI_ALIAS_FLAG)
    private val off = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MONO; textAlign = Paint.Align.CENTER }
    private val path = Path()

    private fun seg(c: Canvas, cx: Float, cy: Float, len: Float, t: Float, vertical: Boolean, paint: Paint) {
        val hl = len / 2; val ht = t / 2
        path.reset()
        if (!vertical) {
            path.moveTo(cx - hl, cy); path.lineTo(cx - hl + ht, cy - ht); path.lineTo(cx + hl - ht, cy - ht)
            path.lineTo(cx + hl, cy); path.lineTo(cx + hl - ht, cy + ht); path.lineTo(cx - hl + ht, cy + ht)
        } else {
            path.moveTo(cx, cy - hl); path.lineTo(cx + ht, cy - hl + ht); path.lineTo(cx + ht, cy + hl - ht)
            path.lineTo(cx, cy + hl); path.lineTo(cx - ht, cy + hl - ht); path.lineTo(cx - ht, cy - hl + ht)
        }
        path.close()
        c.drawPath(path, paint)
    }

    /** Draws one digit with its top-left at (x, y). */
    private fun digit(c: Canvas, x: Float, y: Float, W: Float, H: Float, d: Int) {
        val t = W * 0.17f
        val g = t * 0.18f
        val bits = if (d < 0) 0 else map[d]
        fun p(bit: Int) = if (bits and (1 shl (6 - bit)) != 0) on else off
        val hl = W - g * 2; val vl = H / 2 - g * 2
        seg(c, x + W / 2, y, hl, t, false, p(0))              // a
        seg(c, x + W, y + H / 4, vl, t, true, p(1))           // b
        seg(c, x + W, y + H * 3 / 4, vl, t, true, p(2))       // c
        seg(c, x + W / 2, y + H, hl, t, false, p(3))          // d
        seg(c, x, y + H * 3 / 4, vl, t, true, p(4))           // e
        seg(c, x, y + H / 4, vl, t, true, p(5))               // f
        seg(c, x + W / 2, y + H / 2, hl, t, false, p(6))      // g
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.BLACK)
        val col = colors[style]
        on.color = col
        on.setShadowLayer(h * 0.02f, 0f, 0f, Palettes.withAlpha(col, 200))
        off.color = Palettes.withAlpha(col, 22)

        val cal = T.now()
        val hStr = T.hour(cal, prefs.use24h).padStart(2, ' ')
        val digits = intArrayOf(
            if (hStr[0] == ' ') -1 else hStr[0] - '0', hStr[1] - '0',
            cal.get(Calendar.MINUTE) / 10, cal.get(Calendar.MINUTE) % 10
        )
        val H = h * 0.46f
        val W = H * 0.52f
        val gap = W * 0.42f
        val colonW = W * 0.5f
        val secW = W * 0.5f; val secH = H * 0.5f
        val showSec = prefs.showSeconds
        val total = W * 4 + gap * 2 + colonW + gap + (if (showSec) gap + secW * 2 + gap * 0.5f else 0f)
        var x = (w - total) / 2 + W * 0.06f
        val y = (h - H) / 2 - h * 0.04f

        c.save()
        c.skew(-0.09f, 0f)
        c.translate(y * 0.09f + H * 0.045f, 0f)
        for (i in 0 until 4) {
            digit(c, x, y, W, H, digits[i])
            x += W + gap
            if (i == 1) {
                val blink = (System.currentTimeMillis() / 1000) % 2 == 0L
                val dotR = W * 0.09f
                val cp = if (blink) on else off
                c.drawCircle(x + colonW / 2 - gap / 2, y + H * 0.3f, dotR, cp)
                c.drawCircle(x + colonW / 2 - gap / 2, y + H * 0.7f, dotR, cp)
                x += colonW
            }
        }
        if (showSec) {
            val s = cal.get(Calendar.SECOND)
            digit(c, x, y + H - secH, secW, secH, s / 10)
            digit(c, x + secW + gap * 0.5f, y + H - secH, secW, secH, s % 10)
        }
        c.restore()

        // AM/PM lamps and the date
        txt.textSize = h * 0.04f
        if (!prefs.use24h) {
            val pm = cal.get(Calendar.AM_PM) == Calendar.PM
            txt.color = if (pm) Palettes.withAlpha(col, 60) else col
            c.drawText("AM", w * 0.2f, y + H + h * 0.12f, txt)
            txt.color = if (pm) col else Palettes.withAlpha(col, 60)
            c.drawText("PM", w * 0.28f, y + H + h * 0.12f, txt)
        }
        txt.color = col
        c.drawText(T.fmt(cal, "EEE dd MMM yyyy").uppercase(), w * 0.62f, y + H + h * 0.12f, txt)
    }
}
