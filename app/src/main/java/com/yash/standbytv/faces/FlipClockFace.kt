package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.yash.standbytv.T

/** Retro split-flap clock with a real two-stage flip animation. */
class FlipClockFace(ctx: Context) : Face(ctx) {
    override val id = "flip"
    override val title = "Flip Clock"
    override val styleNames = listOf("Graphite", "Paper", "Amber", "Midnight Blue")

    private class Card { var shown = ""; var old = ""; var start = 0L }
    private val cards = Array(3) { Card() }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = CONDENSED }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = BOLD }
    private val rect = RectF()
    private val accIn = AccelerateInterpolator()
    private val decOut = DecelerateInterpolator()
    private val FLIP_MS = 640L

    private val schemes = arrayOf(
        intArrayOf(0xFF1E1E21.toInt(), 0xFFF2F2F2.toInt(), 0xFF000000.toInt()),
        intArrayOf(0xFFF4F1EA.toInt(), 0xFF1A1A1A.toInt(), 0xFF101010.toInt()),
        intArrayOf(0xFF2A1A05.toInt(), 0xFFFFB000.toInt(), 0xFF000000.toInt()),
        intArrayOf(0xFF0E1A33.toInt(), 0xFFDDE6FF.toInt(), 0xFF02060E.toInt()),
    )

    override fun draw(c: Canvas, w: Float, h: Float) {
        val sc = schemes[style]
        c.drawColor(sc[2])
        val cal = T.now()
        val values = mutableListOf(
            T.hour(cal, prefs.use24h),
            T.min(cal)
        )
        if (prefs.showSeconds) values.add(T.sec(cal))
        val n = values.size

        val gap = w * 0.03f
        val cw = minOf((w - gap * (n + 1)) / n, h * 0.78f)
        val ch = cw * (if (n == 3) 1.05f else 0.95f)
        val totalW = cw * n + gap * (n - 1)
        var x = (w - totalW) / 2f
        val y = (h - ch) / 2f
        val now = System.currentTimeMillis()
        var animating = false

        cardPaint.color = sc[0]; txt.color = sc[1]
        txt.textSize = ch * 0.78f
        line.color = sc[2]; line.strokeWidth = ch * 0.012f

        for (i in 0 until n) {
            val card = cards[i]
            val v = values[i]
            if (card.shown != v) {
                card.old = if (card.shown.isEmpty()) v else card.shown
                card.shown = v
                card.start = now
            }
            rect.set(x, y, x + cw, y + ch)
            val p = ((now - card.start).toFloat() / FLIP_MS).coerceIn(0f, 1f)
            if (p < 1f) animating = true
            drawCard(c, rect, card.old, card.shown, p)
            x += cw + gap
        }

        if (!prefs.use24h) {
            label.color = Palettes_alpha(sc[1], 200)
            label.textSize = ch * 0.07f
            c.drawText(T.ampm(cal), (w - totalW) / 2f + cw * 0.06f, y + ch - ch * 0.06f, label)
        }
        label.color = Palettes_alpha(sc[1], 150)
        label.textSize = h * 0.04f
        label.textAlign = Paint.Align.CENTER
        c.drawText(T.dateLong(cal).uppercase(), w / 2f, y + ch + (h - y - ch) * 0.55f, label)
        label.textAlign = Paint.Align.LEFT

        if (animating) postInvalidateOnAnimation()
    }

    private fun Palettes_alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    /** Static new-top + old-bottom, then the flap: old-top folding down, then new-bottom unfolding. */
    private fun drawCard(c: Canvas, r: RectF, old: String, new: String, p: Float) {
        val mid = r.centerY()
        half(c, r, new, top = true, scale = 1f)
        half(c, r, if (p >= 1f) new else old, top = false, scale = 1f)
        if (p < 1f) {
            if (p < 0.5f) half(c, r, old, top = true, scale = 1f - accIn.getInterpolation(p * 2f))
            else half(c, r, new, top = false, scale = decOut.getInterpolation((p - 0.5f) * 2f))
        }
        c.drawLine(r.left, mid, r.right, mid, line)
    }

    private fun half(c: Canvas, r: RectF, text: String, top: Boolean, scale: Float) {
        if (scale <= 0.01f) return
        val mid = r.centerY()
        val rad = r.width() * 0.07f
        c.save()
        if (top) c.clipRect(r.left, r.top, r.right, mid) else c.clipRect(r.left, mid, r.right, r.bottom)
        if (scale < 1f) c.scale(1f, scale, r.centerX(), mid)
        c.drawRoundRect(r, rad, rad, cardPaint)
        c.drawText(text, r.centerX(), Draw.centerY(txt, mid), txt)
        c.restore()
    }
}
